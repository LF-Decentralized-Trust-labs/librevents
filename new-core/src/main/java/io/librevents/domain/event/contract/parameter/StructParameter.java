package io.librevents.domain.event.contract.parameter;

import java.util.List;

import io.librevents.domain.common.ParameterType;
import io.librevents.domain.event.contract.ContractEventParameter;

public final class StructParameter extends ContractEventParameter<List<ContractEventParameter<?>>> {

    public StructParameter(
            ParameterType type,
            boolean indexed,
            int position,
            List<ContractEventParameter<?>> value) {
        super(type, indexed, position, value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("StructParameter value cannot be empty");
        }
    }
}
