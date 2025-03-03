/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.librevents.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.librevents.dto.block.BlockDetails;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.dto.message.ContractEventFilterAdded;
import io.librevents.dto.message.ContractEventFilterRemoved;
import io.librevents.dto.message.LibreventsMessage;
import io.librevents.dto.transaction.TransactionDetails;
import io.librevents.integration.KafkaSettings;
import io.librevents.model.TransactionMonitoringSpec;
import io.librevents.utils.JSON;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextManager;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(partitions = 1, controlledShutdown = true)
public class BaseKafkaIntegrationTest extends BaseIntegrationTest {

    private static final String KAFKA_LISTENER_CONTAINER_ID =
            "org.springframework.kafka.KafkaListenerEndpointContainer#0";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<LibreventsMessage<ContractEventFilter>> broadcastFiltersEventMessages =
            new ArrayList<>();
    private final List<LibreventsMessage<TransactionMonitoringSpec>>
            broadcastTransactionEventMessages = new ArrayList<>();
    @Autowired public KafkaListenerEndpointRegistry registry;
    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired private KafkaSettings kafkaSettings;
    private KafkaMessageListenerContainer<String, String> testContainer;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        startKafkaContainer();
    }

    private void startKafkaContainer() {
        // set up the Kafka consumer properties
        final Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps(generateTestGroupId(), "false", embeddedKafka);

        // Child classes can modify the properties
        modifyKafkaConsumerProps(consumerProperties);

        // create a Kafka consumer factory
        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        consumerProperties, new StringDeserializer(), new StringDeserializer());

        // set the topic that needs to be consumed
        ContainerProperties containerProperties =
                new ContainerProperties(
                        kafkaSettings.getContractEventsTopic(),
                        kafkaSettings.getEventeumEventsTopic(),
                        kafkaSettings.getBlockEventsTopic(),
                        kafkaSettings.getTransactionEventsTopic());

        // create a Kafka MessageListenerContainer
        testContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

        // setup a Kafka message listener
        testContainer.setupMessageListener(
                new MessageListener<String, String>() {
                    @Override
                    public void onMessage(ConsumerRecord<String, String> record) {
                        System.err.println(
                                ">> Received message: " + JSON.stringify(record.value()));
                        try {
                            if (record.topic().equals(kafkaSettings.getContractEventsTopic())) {
                                final LibreventsMessage<ContractEventDetails> message =
                                        objectMapper.readValue(
                                                record.value(), LibreventsMessage.class);

                                getBroadcastContractEvents().add(message.getDetails());
                            }

                            if (record.topic().equals(kafkaSettings.getEventeumEventsTopic())) {
                                final LibreventsMessage message =
                                        objectMapper.readValue(
                                                record.value(), LibreventsMessage.class);

                                if (message.getType().equals(ContractEventFilterAdded.TYPE)
                                        || message.getType()
                                                .equals(ContractEventFilterRemoved.TYPE)) {
                                    final LibreventsMessage<ContractEventFilter> filterMessge =
                                            message;
                                    getBroadcastFilterEventMessages().add(filterMessge);
                                } else {
                                    final LibreventsMessage<TransactionMonitoringSpec> txMessge =
                                            message;
                                    getBroadcastTransactionEventMessages().add(txMessge);
                                }
                            }

                            if (record.topic().equals(kafkaSettings.getBlockEventsTopic())) {
                                final LibreventsMessage<BlockDetails> message =
                                        objectMapper.readValue(
                                                record.value(), LibreventsMessage.class);

                                getBroadcastBlockMessages().add(message.getDetails());
                            }

                            if (record.topic().equals(kafkaSettings.getTransactionEventsTopic())) {
                                final LibreventsMessage<TransactionDetails> message =
                                        objectMapper.readValue(
                                                record.value(), LibreventsMessage.class);

                                getBroadcastTransactionMessages().add(message.getDetails());
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

        // start the container and underlying message listener
        testContainer.start();

        ContainerTestUtils.waitForAssignment(
                testContainer,
                embeddedKafka.getTopics().size() * embeddedKafka.getPartitionsPerTopic());

        final MessageListenerContainer defaultContainer =
                registry.getListenerContainer(KAFKA_LISTENER_CONTAINER_ID);

        // Container won't exist in non multi-instance mode
        if (defaultContainer != null) {
            ContainerTestUtils.waitForAssignment(
                    defaultContainer, embeddedKafka.getPartitionsPerTopic());
        }

        registry.getListenerContainers()
                .forEach(
                        container -> {
                            try {
                                if (container != defaultContainer) {
                                    ContainerTestUtils.waitForAssignment(container, 1);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

        clearMessages();
    }

    protected void restartEventeumKafka(
            Runnable stoppedLogic, TestContextManager testContextManager) {
        testContainer.stop();
        this.restartEventeum(stoppedLogic, testContextManager);
        startKafkaContainer();
    }

    @AfterEach
    public void tearDown() {
        // stop the container
        testContainer.stop();
    }

    public List<LibreventsMessage<ContractEventFilter>> getBroadcastFilterEventMessages() {
        return broadcastFiltersEventMessages;
    }

    public List<LibreventsMessage<TransactionMonitoringSpec>>
            getBroadcastTransactionEventMessages() {
        return broadcastTransactionEventMessages;
    }

    protected void clearMessages() {
        super.clearMessages();
        broadcastFiltersEventMessages.clear();
    }

    private String generateTestGroupId() {
        return "testGroup-" + UUID.randomUUID();
    }

    protected Map<String, Object> modifyKafkaConsumerProps(Map<String, Object> consumerProps) {
        return consumerProps;
    }
}
