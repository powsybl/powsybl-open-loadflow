/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.VoltageControl;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum NetworkCache {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCache.class);

    public static class Entry extends DefaultNetworkListener {

        private final WeakReference<Network> networkRef;

        private final String variantId;

        private final LoadFlowParameters parameters;

        private List<AcLoadFlowContext> contexts;

        private LfNetworkList.VariantCleaner variantCleaner;

        public Entry(Network network, LoadFlowParameters parameters) {
            Objects.requireNonNull(network);
            this.networkRef = new WeakReference<>(network);
            this.variantId = network.getVariantManager().getWorkingVariantId();
            this.parameters = Objects.requireNonNull(parameters);
        }

        public WeakReference<Network> getNetworkRef() {
            return networkRef;
        }

        public String getVariantId() {
            return variantId;
        }

        public List<AcLoadFlowContext> getContexts() {
            return contexts;
        }

        public void setContexts(List<AcLoadFlowContext> contexts) {
            this.contexts = contexts;
        }

        public void setVariantCleaner(LfNetworkList.VariantCleaner variantCleaner) {
            this.variantCleaner = variantCleaner;
        }

        public LoadFlowParameters getParameters() {
            return parameters;
        }

        private void reset() {
            if (contexts != null) {
                for (AcLoadFlowContext context : contexts) {
                    context.close();
                }
                contexts = null;
            }
        }

        private void onStructureChange() {
            // too difficult to update LfNetwork incrementally
            reset();
        }

        @Override
        public void onCreation(Identifiable identifiable) {
            onStructureChange();
        }

        @Override
        public void afterRemoval(String s) {
            onStructureChange();
        }

        private static Bus getBus(Injection<?> injection, AcLoadFlowContext context) {
            return context.getParameters().getNetworkParameters().isBreakers()
                    ? injection.getTerminal().getBusBreakerView().getBus()
                    : injection.getTerminal().getBusView().getBus();
        }

        private boolean onGeneratorUpdate(Generator generator, String attribute, Object oldValue, Object newValue) {
            boolean found = false;
            for (AcLoadFlowContext context : contexts) {
                Bus bus = getBus(generator, context);
                if (bus != null) {
                    LfNetwork lfNetwork = context.getNetwork();
                    LfBus lfBus = lfNetwork.getBusById(bus.getId());
                    if (lfBus != null) {
                        if (attribute.equals("targetV")) {
                            double valueShift = (double) newValue - (double) oldValue;
                            VoltageControl voltageControl = lfBus.getVoltageControl().orElseThrow();
                            double newTargetV = voltageControl.getTargetValue() + valueShift / lfBus.getNominalV();
                            voltageControl.setTargetValue(newTargetV);
                            context.setNetworkUpdated(true);
                            found = true;
                            break;
                        } else {
                            throw new IllegalStateException("Unsupported generator attribute: " + attribute);
                        }
                    }
                }
            }
            if (!found) {
                LOGGER.warn("Cannot update {} of generator '{}'", attribute, generator.getId());
            }
            return found;
        }

        private boolean onSwitchOpen(String switchId, boolean open) {
            boolean found = false;
            for (AcLoadFlowContext context : contexts) {
                LfNetwork lfNetwork = context.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(switchId);
                if (lfBranch != null) {
                    lfBranch.setDisabled(open);
                    context.setNetworkUpdated(true);
                    found = true;
                }
            }
            if (!found) {
                LOGGER.warn("Cannot open switch '{}'", switchId);
            }
            return found;
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, String variantId, Object oldValue, Object newValue) {
            if (contexts == null) {
                return;
            }
            boolean done = false;
            switch (attribute) {
                case "v":
                case "angle":
                case "p":
                case "q":
                case "p1":
                case "q1":
                case "p2":
                case "q2":
                    // ignore because it is related to state update and won't affect LF calculation
                    done = true;
                    break;

                default:
                    if (identifiable.getType() == IdentifiableType.GENERATOR) {
                        Generator generator = (Generator) identifiable;
                        if (attribute.equals("targetV")
                                && onGeneratorUpdate(generator, attribute, oldValue, newValue)) {
                            done = true;
                        }
                    } else if (identifiable.getType() == IdentifiableType.SWITCH
                            && attribute.equals("open")) {
                        if (onSwitchOpen(identifiable.getId(), (boolean) newValue)) {
                            done = true;
                        }
                    }
                    break;
            }

            if (!done) {
                reset();
            }
        }

        private void onPropertyChange() {
            // nothing to do there could not have any impact on LF calculation
        }

        @Override
        public void onElementAdded(Identifiable identifiable, String attribute, Object newValue) {
            onPropertyChange();
        }

        @Override
        public void onElementReplaced(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
            onPropertyChange();
        }

        @Override
        public void onElementRemoved(Identifiable identifiable, String attribute, Object oldValue) {
            onPropertyChange();
        }

        private void onVariantChange() {
            // we reset
            // TODO to study later if we can do better
            reset();
        }

        @Override
        public void onVariantCreated(String sourceVariantId, String targetVariantId) {
            onVariantChange();
        }

        @Override
        public void onVariantOverwritten(String sourceVariantId, String targetVariantId) {
            onVariantChange();
        }

        @Override
        public void onVariantRemoved(String variantId) {
            onVariantChange();
        }

        public void close() {
            reset();
            if (variantCleaner != null) {
                variantCleaner.clean();
                variantCleaner = null;
            }
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private final Lock lock = new ReentrantLock();

    private void evictDeadEntries() {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.getNetworkRef().get() == null) {
                // release all resources
                entry.close();
                it.remove();
                LOGGER.info("Dead network removed from cache ({} remains)", entries.size());
            }
        }
    }

    public int getEntryCount() {
        lock.lock();
        try {
            evictDeadEntries();
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    public Optional<Entry> findEntry(Network network) {
        String variantId = network.getVariantManager().getWorkingVariantId();
        return entries.stream()
                .filter(e -> e.getNetworkRef().get() == network && e.getVariantId().equals(variantId))
                .findFirst();
    }

    public Entry get(Network network, LoadFlowParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        Entry entry;
        lock.lock();
        try {
            evictDeadEntries();

            entry = findEntry(network).orElse(null);

            // invalid cache if parameters have changed
            // TODO to refine later by comparing in detail parameters that have changed
            if (entry != null && !OpenLoadFlowParameters.equals(parameters, entry.getParameters())) {
                // release all resources
                entry.close();
                entries.remove(entry);
                entry = null;
                LOGGER.info("Network cache evicted because of parameters change");
            }

            if (entry == null) {
                entry = new Entry(network, OpenLoadFlowParameters.clone(parameters));
                entries.add(entry);
                network.addListener(entry);

                LOGGER.info("Network cache created for network '{}' and variant '{}'",
                        network.getId(), network.getVariantManager().getWorkingVariantId());

                return entry;
            }
        } finally {
            lock.unlock();
        }

        // restart from previous state
        if (entry.getContexts() != null) {
            LOGGER.info("Network cache reused for network '{}' and variant '{}'",
                    network.getId(), network.getVariantManager().getWorkingVariantId());

            for (AcLoadFlowContext context : entry.getContexts()) {
                AcLoadFlowResult result = context.getResult();
                if (result != null && result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED) {
                    context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
                }
            }
        } else {
            LOGGER.info("Network cache cannot be reused for network '{}' because invalided", network.getId());
        }

        return entry;
    }

    public void clear() {
        lock.lock();
        try {
            for (var entry : entries) {
                entry.close();
            }
            entries.clear();
        } finally {
            lock.unlock();
        }
    }
}
