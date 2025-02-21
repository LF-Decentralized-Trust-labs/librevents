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

package net.consensys.eventeum.integration.broadcast.blockchain;

import lombok.extern.slf4j.Slf4j;
import net.consensys.eventeum.dto.block.BlockDetails;
import net.consensys.eventeum.dto.event.ContractEventDetails;
import net.consensys.eventeum.dto.message.*;
import net.consensys.eventeum.dto.transaction.TransactionDetails;
import net.consensys.eventeum.integration.RabbitSettings;
import net.consensys.eventeum.utils.JSON;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * A RabbitBlockChainEventBroadcaster that broadcasts the events to a RabbitMQ exchange.
 *
 * <p>The routing key for each message will be defined by the routingKeyPrefix configured, plus
 * filterId for new contract events.
 *
 * <p>The exchange and routingKeyPrefix can be configured via the rabbitmq.exchange and
 * rabbitmq.routingKeyPrefix properties.
 *
 * @author ioBuilders technical team tech@io.builders
 */
@Slf4j
public class RabbitBlockChainEventBroadcaster implements BlockchainEventBroadcaster {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitSettings rabbitSettings;

    public RabbitBlockChainEventBroadcaster(
            RabbitTemplate rabbitTemplate, RabbitSettings rabbitSettings) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitSettings = rabbitSettings;
    }

    @Override
    public void broadcastNewBlock(BlockDetails block) {
        final EventeumMessage<BlockDetails> message = createBlockEventMessage(block);
        rabbitTemplate.convertAndSend(
                this.rabbitSettings.getExchange(),
                String.format("%s", this.rabbitSettings.getBlockEventsRoutingKey()),
                message);

        log.info(
                "New block sent: [{}] to exchange [{}] with routing key [{}]",
                JSON.stringify(message),
                this.rabbitSettings.getExchange(),
                this.rabbitSettings.getBlockEventsRoutingKey());
    }

    @Override
    public void broadcastContractEvent(ContractEventDetails eventDetails) {
        final EventeumMessage<ContractEventDetails> message =
                createContractEventMessage(eventDetails);
        rabbitTemplate.convertAndSend(
                this.rabbitSettings.getExchange(),
                String.format(
                        "%s.%s",
                        this.rabbitSettings.getContractEventsRoutingKey(),
                        eventDetails.getFilterId()),
                message);

        log.info(
                "New contract event sent: [{}] to exchange [{}] with routing key [{}.{}]",
                JSON.stringify(message),
                this.rabbitSettings.getExchange(),
                this.rabbitSettings.getContractEventsRoutingKey(),
                eventDetails.getFilterId());
    }

    @Override
    public void broadcastTransaction(TransactionDetails transactionDetails) {
        final EventeumMessage<TransactionDetails> message =
                createTransactionEventMessage(transactionDetails);
        rabbitTemplate.convertAndSend(
                this.rabbitSettings.getExchange(),
                String.format(
                        "%s.%s",
                        this.rabbitSettings.getTransactionEventsRoutingKey(),
                        transactionDetails.getHash()),
                message);

        log.info(
                "New transaction event sent: [{}] to exchange [{}] with routing key [{}.{}]",
                JSON.stringify(message),
                this.rabbitSettings.getExchange(),
                this.rabbitSettings.getTransactionEventsRoutingKey(),
                transactionDetails.getHash());
    }

    @Override
    public void broadcastMessage(MessageDetails messageDetails) {
        final EventeumMessage<MessageDetails> message = createMessageEventMessage(messageDetails);
        rabbitTemplate.convertAndSend(
                this.rabbitSettings.getExchange(),
                String.format(
                        "%s.%s",
                        this.rabbitSettings.getMessageEventsRoutingKey(),
                        messageDetails.getTopicId()),
                message);

        log.info(
                "New message event sent: [{}] to exchange [{}] with routing key [{}.{}]",
                JSON.stringify(message),
                this.rabbitSettings.getExchange(),
                this.rabbitSettings.getMessageEventsRoutingKey(),
                messageDetails.getTopicId());
    }

    protected EventeumMessage<BlockDetails> createBlockEventMessage(BlockDetails blockDetails) {
        return new BlockEvent(blockDetails);
    }

    protected EventeumMessage<ContractEventDetails> createContractEventMessage(
            ContractEventDetails contractEventDetails) {
        return new ContractEvent(contractEventDetails);
    }

    protected EventeumMessage<TransactionDetails> createTransactionEventMessage(
            TransactionDetails transactionDetails) {
        return new TransactionEvent(transactionDetails);
    }

    protected EventeumMessage<MessageDetails> createMessageEventMessage(
            MessageDetails messageDetails) {
        return new MessageEvent(messageDetails);
    }
}
