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
import java.util.ArrayList;
import java.util.Arrays;

import io.librevents.chain.util.Web3jUtil;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.ContractEventStatus;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.dto.event.filter.ContractEventSpecification;
import io.librevents.dto.event.filter.ParameterDefinition;
import io.librevents.dto.event.filter.ParameterType;
import io.librevents.dto.event.parameter.NumberParameter;
import io.librevents.dto.event.parameter.StringParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-db.properties")
class ArraysIT extends BaseKafkaIntegrationTest {

    // "BytesValue" in hex
    private static final String BYTES_VALUE_HEX =
            "0x427974657356616c756500000000000000000000000000000000000000000000";

    // "BytesValue2" in hex
    private static final String BYTES_VALUE2_HEX =
            "0x427974657356616c756532000000000000000000000000000000000000000000";

    @Test
    void testEventWithArrays() throws Exception {
        final EventEmitter emitter = deployEventEmitterContract();

        final ContractEventFilter registeredFilter =
                registerDummyEventArrayFilter(emitter.getContractAddress());
        emitter.emitEventArray(
                        BigInteger.ONE,
                        BigInteger.TEN,
                        stringToBytes("BytesValue"),
                        stringToBytes("BytesValue2"))
                .send();

        waitForContractEventMessages(1);

        assertEquals(1, getBroadcastContractEvents().size());

        final ContractEventDetails eventDetails = getBroadcastContractEvents().getFirst();

        assertEquals(
                registeredFilter.getEventSpecification().getEventName(), eventDetails.getName());
        assertEquals(ContractEventStatus.UNCONFIRMED, eventDetails.getStatus());

        final ArrayList<NumberParameter> uintArray =
                (ArrayList<NumberParameter>)
                        eventDetails.getNonIndexedParameters().getFirst().getValue();

        assertEquals(BigInteger.ONE, uintArray.getFirst().getValue());
        assertEquals(BigInteger.TEN, uintArray.get(1).getValue());

        final ArrayList<StringParameter> bytesArray =
                (ArrayList<StringParameter>)
                        eventDetails.getNonIndexedParameters().get(1).getValue();

        assertEquals(BYTES_VALUE_HEX, bytesArray.getFirst().getValue());
        assertEquals(BYTES_VALUE2_HEX, bytesArray.get(1).getValue());

        assertEquals(
                Web3jUtil.getSignature(registeredFilter.getEventSpecification()),
                eventDetails.getEventSpecificationSignature());
    }

    private ContractEventFilter registerDummyEventArrayFilter(String contractAddress) {
        return registerEventFilter(createDummyEventArrayFilter(contractAddress));
    }

    private ContractEventFilter createDummyEventArrayFilter(String contractAddress) {

        final ContractEventSpecification eventSpec = new ContractEventSpecification();

        eventSpec.setNonIndexedParameterDefinitions(
                Arrays.asList(
                        new ParameterDefinition(0, ParameterType.build("UINT256[]")),
                        new ParameterDefinition(1, ParameterType.build("BYTES32[]"))));

        eventSpec.setEventName("DummyEventArray");

        return createFilter(getDummyEventFilterId(), contractAddress, eventSpec);
    }
}
