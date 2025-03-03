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
import java.util.List;
import java.util.Optional;

import io.librevents.dto.block.BlockDetails;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.integration.eventstore.EventStore;
import io.librevents.model.LatestBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.crypto.Keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-sql.properties")
@ContextConfiguration(initializers = {BroadcasterSqlDBEventStoreIT.Initializer.class})
@Testcontainers
public class BroadcasterSqlDBEventStoreIT extends MainBroadcasterTests {

    @Container
    public static MSSQLServerContainer mssqlserver =
            new MSSQLServerContainer().withPassword("reallyStrongPwd123").acceptLicense();

    static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            TestPropertyValues.of("spring.datasource.url=" + mssqlserver.getJdbcUrl())
                    .applyTo(configurableApplicationContext.getEnvironment());
        }
    }

    @Autowired private EventStore eventStore;

    @Test
    void testBroadcastsUnconfirmedEventAfterInitialEmit() throws Exception {
        doTestBroadcastsUnconfirmedEventAfterInitialEmit();
    }

    @Test
    void testBroadcastNotOrderedEvent() throws Exception {
        doTestBroadcastsNotOrderedEvent();
    }

    @Test
    void testBroadcastsConfirmedEventAfterBlockThresholdReached() throws Exception {
        doTestBroadcastsConfirmedEventAfterBlockThresholdReached();
    }

    @Test
    void testContractEventForUnregisteredEventFilterNotBroadcast() throws Exception {
        doTestContractEventForUnregisteredEventFilterNotBroadcast();
    }

    @Test
    void testBroadcastBlock() throws Exception {
        doTestBroadcastBlock();
    }

    @Test
    void testBroadcastsUnconfirmedTransactionAfterInitialMining() throws Exception {
        doTestBroadcastsUnconfirmedTransactionAfterInitialMining();
    }

    @Test
    void testBroadcastsConfirmedTransactionAfterBlockThresholdReached() throws Exception {
        doTestBroadcastsConfirmedTransactionAfterBlockThresholdReached();
    }

    @Test
    void testBroadcastFailedTransactionFilteredByHash() throws Exception {
        doTestBroadcastFailedTransactionFilteredByHash();
    }

    @Test
    void testBroadcastFailedTransactionFilteredByTo() throws Exception {
        doTestBroadcastFailedTransactionFilteredByTo();
    }

    @Test
    void testBroadcastFailedTransactionFilteredByFrom() throws Exception {
        doTestBroadcastFailedTransactionFilteredByFrom();
    }

    @Test
    void testBroadcastEventAddedToEventStore() throws Exception {

        final EventEmitter emitter = deployEventEmitterContract();

        final ContractEventFilter registeredFilter =
                registerDummyEventFilter(emitter.getContractAddress());
        emitter.emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue").send();

        waitForContractEventMessages(1);

        assertEquals(1, getBroadcastContractEvents().size());

        final ContractEventDetails eventDetails = getBroadcastContractEvents().getFirst();

        Thread.sleep(1000);

        List<ContractEventDetails> savedEvents =
                eventStore
                        .getContractEventsForSignature(
                                eventDetails.getEventSpecificationSignature(),
                                Keys.toChecksumAddress(emitter.getContractAddress()),
                                PageRequest.of(0, 100000))
                        .getContent();
        eventDetails.setId(savedEvents.getFirst().getId());
        assertEquals(1, savedEvents.size());
        assertEquals(eventDetails, savedEvents.getFirst());
    }

    @Test
    void testBroadcastBlockAddedToEventStore() throws Exception {
        doTestBroadcastBlock();

        Thread.sleep(1000);

        final Optional<LatestBlock> latestBlock = eventStore.getLatestBlockForNode("default");

        assertEquals(true, latestBlock.isPresent());

        final List<BlockDetails> broadcastBlocks = getBroadcastBlockMessages();
        assertEquals(
                broadcastBlocks.get(broadcastBlocks.size() - 1).getNumber(),
                latestBlock.get().getNumber());
    }
}
