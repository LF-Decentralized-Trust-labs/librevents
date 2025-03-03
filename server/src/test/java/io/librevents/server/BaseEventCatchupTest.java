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

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.librevents.dto.event.ContractEventDetails;
import io.librevents.model.EventFilterSyncStatus;
import io.librevents.model.SyncStatus;
import io.librevents.repository.EventFilterSyncStatusRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseEventCatchupTest extends BaseKafkaIntegrationTest {

    private static final int NUM_OF_EVENTS_BEFORE_START = 30;

    private static EventEmitter eventEmitter;

    static {
        BaseIntegrationTest.shouldPersistNodeVolume = false;
    }

    @Autowired private EventFilterSyncStatusRepository syncStatusRepository;

    @BeforeAll
    public static void doEmitEvents() throws Exception {
        final Web3j web3j = Web3j.build(new HttpService("http://localhost:8545"));

        eventEmitter = EventEmitter.deploy(web3j, CREDS, GAS_PRICE, GAS_LIMIT).send();

        for (int i = 0; i < NUM_OF_EVENTS_BEFORE_START; i++) {
            eventEmitter
                    .emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue")
                    .send();
        }

        System.setProperty("EVENT_EMITTER_CONTRACT_ADDRESS", eventEmitter.getContractAddress());
    }

    @BeforeEach
    @Override
    public void clearMessages() {
        // There's a race condition that sometimes causes the event messages to be cleared after
        // being
        // received
        // Overriding to remove the clearing of event messages as its not required here (until there
        // are
        // multiple tests!)
        getBroadcastBlockMessages().clear();
        getBroadcastTransactionMessages().clear();
    }

    @Test
    void testEventsCatchupOnStart() throws Exception {
        waitForMessages(30, getBroadcastContractEvents(), false, TopicTypesEnum.MESSAGE, 6000);

        final List<ContractEventDetails> events =
                getBroadcastContractEvents().stream()
                        .sorted(Comparator.comparing(ContractEventDetails::getBlockNumber))
                        .toList();
        final int startBlock = events.getFirst().getBlockNumber().intValue();

        for (int i = 0; i < events.size(); i++) {
            assertEquals(startBlock + i, events.get(i).getBlockNumber().intValue());
        }

        final EventFilterSyncStatus syncStatus =
                syncStatusRepository
                        .findById("DummyEvent")
                        .orElseThrow(() -> new RuntimeException("No sync status in db"));

        assertEquals(SyncStatus.SYNCED, syncStatus.getSyncStatus());

        // Only need to sync with start block
        // assertEquals(events.getFirst().getBlockNumber(), syncStatus.getLastBlockNumber());

        getBroadcastContractEvents().clear();

        eventEmitter.emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue").send();

        waitForBlockMessages(1);

        // These below lines do not work because the parity node is not auto-mined

        // final ContractEventDetails event = getBroadcastContractEvents().getFirst();
        // assertEquals(startBlock + NUM_OF_EVENTS_BEFORE_START, event.getBlockNumber().intValue());
    }

    @Override
    protected Map<String, Object> modifyKafkaConsumerProps(Map<String, Object> consumerProps) {
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return consumerProps;
    }
}
