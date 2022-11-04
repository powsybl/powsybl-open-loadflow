/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    public static class Entry implements NetworkListener {

        private final WeakReference<Network> networkRef;

        private final LoadFlowParameters parameters;

        private List<AcLoadFlowContext> contexts;

        public Entry(WeakReference<Network> networkRef, LoadFlowParameters parameters) {
            this.networkRef = Objects.requireNonNull(networkRef);
            this.parameters = parameters;
        }

        public WeakReference<Network> getNetworkRef() {
            return networkRef;
        }

        public List<AcLoadFlowContext> getContexts() {
            return contexts;
        }

        public void setContexts(List<AcLoadFlowContext> contexts) {
            this.contexts = contexts;
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
        public void beforeRemoval(Identifiable identifiable) {
            // we don't care
        }

        @Override
        public void afterRemoval(String s) {
            onStructureChange();
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
            // seems to be not called anymore
        }

        private void onLoadUpdate(Load load, String attribute, Object oldValue, Object newValue) {
            for (AcLoadFlowContext context : contexts) {
                Bus bus = context.getParameters().getNetworkParameters().isBreakers()
                        ? load.getTerminal().getBusBreakerView().getBus()
                        : load.getTerminal().getBusView().getBus();
                if (bus != null) {
                    LfNetwork lfNetwork = context.getNetwork();
                    LfBus lfBus = lfNetwork.getBusById(bus.getId());
                    if (lfBus != null) {
                        if (attribute.equals("p0")) {
                            double loadShiftP = (double) newValue - (double) oldValue;
                            double newLoadP = lfBus.getLoadTargetP() + loadShiftP / PerUnit.SB;
                            lfBus.reInitLoadTargetP(newLoadP);
                            context.setNetworkUpdated(true);
                        } else {
                            throw new IllegalStateException("Unsupported load attribute: " + attribute);
                        }
                    }
                }
            }
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
                    if (identifiable.getType() == IdentifiableType.LOAD) {
                        Load load = (Load) identifiable;
                        if (attribute.equals("p0")) {
                            onLoadUpdate(load, attribute, oldValue, newValue);
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
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private final Lock lock = new ReentrantLock();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new LoadFlowParametersJsonModule());

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

    private boolean equals(LoadFlowParameters parameters1, LoadFlowParameters parameters2) {
        try {
            return objectMapper.writeValueAsString(parameters1)
                    .equals(objectMapper.writeValueAsString(parameters2));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private Optional<Entry> findEntry(Network network) {
        return entries.stream()
                .filter(e -> e.getNetworkRef().get() == network)
                .findFirst();
    }

    public Entry get(Network network, LoadFlowParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        lock.lock();
        try {
            evictDeadEntries();

            var entry = findEntry(network).orElse(null);

            // invalid cache if parameters have changed
            // TODO to refine later by comparing in detail parameters that have changed
            if (entry != null && !equals(parameters, entry.getParameters())) {
                // release all resources
                entry.close();
                entries.remove(entry);
                entry = null;
                LOGGER.info("Network cache evicted because of parameters change");
            }

            if (entry == null) {
                entry = new Entry(new WeakReference<>(network), parameters);
                entries.add(entry);
                network.addListener(entry);

                LOGGER.info("Network cache created for network '{}'", network.getId());

                return entry;
            } else {
                LOGGER.info("Network cache reused for network '{}'", network.getId());

                // restart from previous state
                for (AcLoadFlowContext context : entry.getContexts()) {
                    AcLoadFlowResult result = context.getResult();
                    if (result != null && result.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED) {
                        context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
                    }
                }

                return entry;
            }
        } finally {
            lock.unlock();
        }
    }
}
