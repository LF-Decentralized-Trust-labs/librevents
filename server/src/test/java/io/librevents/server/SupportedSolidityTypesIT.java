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
import java.util.Random;

import io.librevents.chain.util.Web3jUtil;
import io.librevents.dto.event.ContractEventDetails;
import io.librevents.dto.event.ContractEventStatus;
import io.librevents.dto.event.filter.ContractEventFilter;
import io.librevents.dto.event.filter.ContractEventSpecification;
import io.librevents.dto.event.filter.ParameterDefinition;
import io.librevents.dto.event.filter.ParameterType;
import io.librevents.dto.event.parameter.StringParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = "classpath:application-test-db.properties")
public class SupportedSolidityTypesIT extends BaseKafkaIntegrationTest {

    @Test
    void testBytes16Broadcast() throws Exception {
        final EventEmitter eventEmitter = deployEventEmitterContract();

        final ContractEventSpecification eventSpec = new ContractEventSpecification();
        eventSpec.setIndexedParameterDefinitions(
                Arrays.asList(new ParameterDefinition(0, ParameterType.build("BYTES16"))));

        eventSpec.setNonIndexedParameterDefinitions(
                Arrays.asList(new ParameterDefinition(1, ParameterType.build("BYTES16"))));

        eventSpec.setEventName(eventEmitter.DUMMYEVENTBYTES16_EVENT.getName());

        registerEventFilter(createFilter(null, eventEmitter.getContractAddress(), eventSpec));

        // Generate random 16 byte value
        byte[] rndBytes = randomBytesValue(16);

        eventEmitter.emitEventBytes16(rndBytes).send();

        waitForContractEventMessages(1);

        final ContractEventDetails event = getBroadcastContractEvents().getFirst();
        final String valueHex = Numeric.toHexString(rndBytes, 0, 16, true);

        assertEquals(valueHex, event.getNonIndexedParameters().getFirst().getValueString());
        assertEquals(valueHex, event.getIndexedParameters().getFirst().getValueString());
    }

    @Test
    void testEventWithAdditionalTypes() throws Exception {
        final EventEmitter emitter = deployEventEmitterContract();

        byte[] byteValue = randomBytesValue(1);
        final ContractEventFilter registeredFilter =
                registerDummyEventAdditionalTypesFilter(emitter.getContractAddress());
        emitter.emitEventAdditionalTypes(BigInteger.ONE, BigInteger.TEN, byteValue).send();

        waitForContractEventMessages(1);

        assertEquals(1, getBroadcastContractEvents().size());

        final ContractEventDetails eventDetails = getBroadcastContractEvents().getFirst();

        assertEquals(
                registeredFilter.getEventSpecification().getEventName(), eventDetails.getName());
        assertEquals(ContractEventStatus.UNCONFIRMED, eventDetails.getStatus());

        assertEquals(BigInteger.ONE, eventDetails.getIndexedParameters().getFirst().getValue());
        assertEquals(BigInteger.TEN, eventDetails.getIndexedParameters().get(1).getValue());

        final ArrayList<StringParameter> addressArray =
                (ArrayList<StringParameter>)
                        eventDetails.getNonIndexedParameters().getFirst().getValue();

        assertEquals(
                Keys.toChecksumAddress(CREDS.getAddress()), addressArray.getFirst().getValue());
        assertEquals(
                Keys.toChecksumAddress(emitter.getContractAddress()),
                addressArray.get(1).getValue());

        assertEquals(
                Numeric.toHexString(byteValue, 0, 1, true),
                eventDetails.getNonIndexedParameters().get(1).getValueString());

        assertEquals(BigInteger.ONE, eventDetails.getNonIndexedParameters().get(2).getValue());

        assertEquals(
                Web3jUtil.getSignature(registeredFilter.getEventSpecification()),
                eventDetails.getEventSpecificationSignature());
    }

    private ContractEventFilter registerDummyEventAdditionalTypesFilter(String contractAddress) {
        return registerEventFilter(createDummyEventAdditionalTypesFilter(contractAddress));
    }

    private ContractEventFilter createDummyEventAdditionalTypesFilter(String contractAddress) {

        final ContractEventSpecification eventSpec = new ContractEventSpecification();

        eventSpec.setIndexedParameterDefinitions(
                Arrays.asList(
                        new ParameterDefinition(0, ParameterType.build("UINT16")),
                        new ParameterDefinition(1, ParameterType.build("INT64"))));

        eventSpec.setNonIndexedParameterDefinitions(
                Arrays.asList(
                        new ParameterDefinition(2, ParameterType.build("ADDRESS[]")),
                        new ParameterDefinition(3, ParameterType.build("BYTE")),
                        new ParameterDefinition(4, ParameterType.build("BOOL"))));

        eventSpec.setEventName("DummyEventAdditionalTypes");

        return createFilter(getDummyEventFilterId(), contractAddress, eventSpec);
    }

    private byte[] randomBytesValue(int size) {
        // Generate random 16 byte value
        final Random random = new Random();
        byte[] rndBytes = new byte[size];
        random.nextBytes(rndBytes);

        return rndBytes;
    }
}
