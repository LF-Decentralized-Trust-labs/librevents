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

import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.ContractEventStatus;
import io.librevents.dto.event.filter.ContractEventFilter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-factory.properties")
@Import(EventStoreFactoryConfig.class)
class EventStoreFactoryIT extends BaseIntegrationTest {

    @Autowired private EventStoreFactoryConfig.Entities<ContractEventDetails> savedEvents;

    @Test
    void testEventStoreFactoryWiredCorrectly() throws Exception {
        final EventEmitter emitter = deployEventEmitterContract();

        final ContractEventFilter registeredFilter =
                registerDummyEventFilter(emitter.getContractAddress());
        emitter.emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue").send();

        Thread.sleep(15000);
        assertEquals(1, savedEvents.getEntities().size());

        final ContractEventDetails eventDetails = savedEvents.getEntities().getFirst();
        verifyDummyEventDetails(registeredFilter, eventDetails, ContractEventStatus.CONFIRMED);
    }
}
