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

import java.util.ArrayList;
import java.util.List;

import io.librevents.dto.block.BlockDetails;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.dto.message.LibreventsMessage;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class BaseRabbitIntegrationTest extends BaseIntegrationTest {

    private List<LibreventsMessage<ContractEventFilter>> broadcastFiltersEventMessages =
            new ArrayList<>();

    public List<LibreventsMessage<ContractEventFilter>> getBroadcastFilterEventMessages() {
        return broadcastFiltersEventMessages;
    }

    protected void clearMessages() {
        super.clearMessages();
        broadcastFiltersEventMessages.clear();
    }

    @RabbitListener(
            bindings =
                    @QueueBinding(
                            key = "thisIsRoutingKey.*",
                            value = @Queue("ThisIsAEventsQueue"),
                            exchange =
                                    @Exchange(
                                            value = "ThisIsAExchange",
                                            type = ExchangeTypes.TOPIC)))
    public void onEvent(LibreventsMessage message) {
        if (message.getDetails() instanceof ContractEventDetails) {
            getBroadcastContractEvents().add((ContractEventDetails) message.getDetails());
        } else if (message.getDetails() instanceof BlockDetails) {
            getBroadcastBlockMessages().add((BlockDetails) message.getDetails());
        }
    }
}
