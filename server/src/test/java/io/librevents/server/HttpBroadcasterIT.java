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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.librevents.dto.block.BlockDetails;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.ContractEventStatus;
import io.librevents.dto.event.filter.ContractEventFilter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-http.properties")
@TestMethodOrder(MethodOrderer.MethodName.class)
public class HttpBroadcasterIT extends BaseIntegrationTest {

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    void testBroadcastBlock() throws Exception {
        StubHttpConsumer consumer = new StubHttpConsumer();
        consumer.start(getBroadcastContractEvents(), getBroadcastBlockMessages());

        triggerBlocks(1);
        waitForBlockMessages(1);

        consumer.stop();

        BlockDetails blockDetails = getBroadcastBlockMessages().getFirst();
        assertEquals(1, blockDetails.getNumber().compareTo(BigInteger.ZERO));
        assertNotNull(blockDetails.getHash());
        assertNotNull(blockDetails.getTimestamp());
    }

    @Test
    void testBroadcastContractEvent() throws Exception {
        StubHttpConsumer consumer = new StubHttpConsumer();
        consumer.start(getBroadcastContractEvents(), getBroadcastBlockMessages());

        final EventEmitter emitter = deployEventEmitterContract();

        final ContractEventFilter registeredFilter =
                registerDummyEventFilter(emitter.getContractAddress());
        emitter.emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue").send();

        waitForContractEventMessages(1);

        consumer.stop();

        assertEquals(1, getBroadcastContractEvents().size());

        final ContractEventDetails eventDetails = getBroadcastContractEvents().getFirst();
        verifyDummyEventDetails(registeredFilter, eventDetails, ContractEventStatus.UNCONFIRMED);
    }
}
