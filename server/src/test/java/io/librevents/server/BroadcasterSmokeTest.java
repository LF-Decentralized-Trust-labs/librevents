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

import io.librevents.constant.Constants;
import io.librevents.dto.block.BlockDetails;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.ContractEventStatus;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.dto.transaction.TransactionDetails;
import io.librevents.dto.transaction.TransactionStatus;
import io.librevents.model.TransactionIdentifierType;
import io.librevents.model.TransactionMonitoringSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.util.AssertionErrors.assertTrue;

public abstract class BroadcasterSmokeTest extends BaseIntegrationTest {

    @Test
    void testBroadcastBlock() throws Exception {
        triggerBlocks(1);

        waitForBlockMessages(1);

        assertTrue("No blocks received", !getBroadcastBlockMessages().isEmpty());

        BlockDetails blockDetails = getBroadcastBlockMessages().getFirst();
        assertEquals(1, blockDetails.getNumber().compareTo(BigInteger.ZERO));
        assertNotNull(blockDetails.getHash());
    }

    @Test
    void testBroadcastContractEvent() throws Exception {

        final EventEmitter emitter = deployEventEmitterContract();

        final ContractEventFilter registeredFilter =
                registerDummyEventFilter(emitter.getContractAddress());
        emitter.emitEvent(stringToBytes("BytesValue"), BigInteger.TEN, "StringValue").send();

        waitForContractEventMessages(1);

        assertEquals(1, getBroadcastContractEvents().size());

        final ContractEventDetails eventDetails = getBroadcastContractEvents().getFirst();
        verifyDummyEventDetails(registeredFilter, eventDetails, ContractEventStatus.CONFIRMED);
    }

    @Test
    void testBroadcastTransactionEvent() throws Exception {

        final String txHash = sendTransaction();
        TransactionMonitoringSpec monitorSpec =
                new TransactionMonitoringSpec(
                        TransactionIdentifierType.HASH, txHash, Constants.DEFAULT_NODE_NAME);

        monitorTransaction(monitorSpec);

        waitForTransactionMessages(1);

        assertEquals(1, getBroadcastTransactionMessages().size());

        final TransactionDetails txDetails = getBroadcastTransactionMessages().getFirst();
        assertEquals(txHash, txDetails.getHash());
        assertEquals(TransactionStatus.CONFIRMED, txDetails.getStatus());
    }

    protected void onBlockMessageReceived(BlockDetails block) {
        getBroadcastBlockMessages().add(block);
    }

    protected void onContractEventMessageReceived(ContractEventDetails event) {
        getBroadcastContractEvents().add(event);
    }

    protected void onTransactionMessageReceived(TransactionDetails tx) {
        getBroadcastTransactionMessages().add(tx);
    }
}
