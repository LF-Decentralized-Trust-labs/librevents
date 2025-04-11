package io.librevents.domain.filter.event;

import java.util.List;
import java.util.UUID;

import io.librevents.domain.filter.FilterName;

public final class GlobalEventFilter extends EventFilter {
    public GlobalEventFilter(
            UUID id,
            FilterName name,
            UUID nodeId,
            EventFilterSpecification specification,
            List<EventStatus> statuses,
            SyncState syncState) {
        super(id, name, nodeId, EventFilterScope.GLOBAL, specification, statuses, syncState);
    }
}
