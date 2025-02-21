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

package net.consensys.eventeum.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.consensys.eventeum.chain.block.tx.TransactionMonitoringBlockListener;
import net.consensys.eventeum.chain.block.tx.criteria.TransactionMatchingCriteria;
import net.consensys.eventeum.chain.block.tx.criteria.factory.TransactionMatchingCriteriaFactory;
import net.consensys.eventeum.integration.broadcast.internal.EventeumEventBroadcaster;
import net.consensys.eventeum.model.TransactionMonitoringSpec;
import net.consensys.eventeum.repository.TransactionMonitoringSpecRepository;
import net.consensys.eventeum.service.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultTransactionMonitoringService implements TransactionMonitoringService {

    private final EventeumEventBroadcaster eventeumEventBroadcaster;
    private final TransactionMonitoringSpecRepository transactionMonitoringRepo;
    private final TransactionMonitoringBlockListener monitoringBlockListener;
    private final TransactionMatchingCriteriaFactory matchingCriteriaFactory;
    private final Map<String, TransactionMonitor> transactionMonitors = new HashMap<>();

    @Autowired
    public DefaultTransactionMonitoringService(
            EventeumEventBroadcaster eventeumEventBroadcaster,
            TransactionMonitoringSpecRepository transactionMonitoringRepo,
            TransactionMonitoringBlockListener monitoringBlockListener,
            TransactionMatchingCriteriaFactory matchingCriteriaFactory) {
        this.eventeumEventBroadcaster = eventeumEventBroadcaster;
        this.transactionMonitoringRepo = transactionMonitoringRepo;
        this.monitoringBlockListener = monitoringBlockListener;
        this.matchingCriteriaFactory = matchingCriteriaFactory;
    }

    @Override
    public void registerTransactionsToMonitor(TransactionMonitoringSpec spec) {
        registerTransactionsToMonitor(spec, true);
    }

    @Override
    public void registerTransactionsToMonitor(TransactionMonitoringSpec spec, boolean broadcast) {
        if (isTransactionSpecRegistered(spec)) {
            log.info("Already registered transaction monitoring spec with id: {}", spec.getId());
            return;
        }

        registerTransactionMonitoring(spec);
        saveTransactionMonitoringSpec(spec);

        if (broadcast) {
            eventeumEventBroadcaster.broadcastTransactionMonitorAdded(spec);
        }
    }

    @Override
    public void stopMonitoringTransactions(String monitorId) throws NotFoundException {
        stopMonitoringTransactions(monitorId, true);
    }

    @Override
    public void stopMonitoringTransactions(String monitorId, boolean broadcast)
            throws NotFoundException {

        final TransactionMonitor transactionMonitor = getTransactionMonitor(monitorId);

        if (transactionMonitor == null) {
            throw new NotFoundException("No monitored transaction with id: " + monitorId);
        }

        removeTransactionMonitorMatchingCriteria(transactionMonitor);
        deleteTransactionMonitor(monitorId);

        if (broadcast) {
            eventeumEventBroadcaster.broadcastTransactionMonitorRemoved(
                    transactionMonitor.getSpec());
        }
    }

    @Override
    public List<TransactionMonitoringSpec> listTransactionMonitorings() {
        return transactionMonitors.values().stream()
                .map(TransactionMonitor::getSpec)
                .collect(Collectors.toList());
    }

    private void removeTransactionMonitorMatchingCriteria(TransactionMonitor transactionMonitor) {
        monitoringBlockListener.removeMatchingCriteria(transactionMonitor.getMatchingCriteria());
    }

    private void deleteTransactionMonitor(String monitorId) {
        transactionMonitors.remove(monitorId);

        transactionMonitoringRepo.deleteById(monitorId);
    }

    private TransactionMonitor getTransactionMonitor(String monitorId) {
        return transactionMonitors.get(monitorId);
    }

    private void registerTransactionMonitoring(TransactionMonitoringSpec spec) {

        final TransactionMatchingCriteria matchingCriteria = matchingCriteriaFactory.build(spec);
        monitoringBlockListener.addMatchingCriteria(matchingCriteria);
        transactionMonitors.put(spec.getId(), new TransactionMonitor(spec, matchingCriteria));
    }

    private void saveTransactionMonitoringSpec(TransactionMonitoringSpec spec) {
        transactionMonitoringRepo.save(spec);
    }

    private boolean isTransactionSpecRegistered(TransactionMonitoringSpec spec) {
        return transactionMonitors.containsKey(spec.getId());
    }

    @Data
    private class TransactionMonitor {
        TransactionMonitoringSpec spec;

        TransactionMatchingCriteria matchingCriteria;

        public TransactionMonitor(
                TransactionMonitoringSpec spec, TransactionMatchingCriteria matchingCriteria) {
            this.spec = spec;
            this.matchingCriteria = matchingCriteria;
        }
    }
}
