/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ControlUnit;
import com.powsybl.iidm.network.extensions.ControlZone;
import com.powsybl.iidm.network.extensions.PilotPoint;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.AbstractLfGenerator;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public enum NetworkCache {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCache.class);

    public static class Entry extends DefaultNetworkListener {

        private final WeakReference<Network> networkRef;

        private final String workingVariantId;
        private String tmpVariantId;

        private final LoadFlowParameters parameters;

        private List<AcLoadFlowContext> contexts;

        private boolean pause = false;

        public Entry(Network network, LoadFlowParameters parameters) {
            Objects.requireNonNull(network);
            this.networkRef = new WeakReference<>(network);
            this.workingVariantId = network.getVariantManager().getWorkingVariantId();
            this.parameters = Objects.requireNonNull(parameters);
        }

        public WeakReference<Network> getNetworkRef() {
            return networkRef;
        }

        public String getWorkingVariantId() {
            return workingVariantId;
        }

        public void setTmpVariantId(String tmpVariantId) {
            this.tmpVariantId = tmpVariantId;
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

        public void setPause(boolean pause) {
            this.pause = pause;
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

        private static Optional<Bus> getBus(Injection<?> injection, AcLoadFlowContext context) {
            return Optional.ofNullable(context.getParameters().getNetworkParameters().isBreakers()
                    ? injection.getTerminal().getBusBreakerView().getBus()
                    : injection.getTerminal().getBusView().getBus());
        }

        private static Optional<LfBus> getLfBus(Injection<?> injection, AcLoadFlowContext context) {
            return getBus(injection, context)
                    .map(bus -> context.getNetwork().getBusById(bus.getId()));
        }

        enum CacheUpdateStatus {
            UNSUPPORTED_UPDATE,
            ELEMENT_UPDATED,
            IGNORE_UPDATE,
            ELEMENT_NOT_FOUND
        }

        record CacheUpdateResult(CacheUpdateStatus status, AcLoadFlowContext context) {
            static CacheUpdateResult unsupportedUpdate() {
                return new CacheUpdateResult(CacheUpdateStatus.UNSUPPORTED_UPDATE, null);
            }

            static CacheUpdateResult elementUpdated(AcLoadFlowContext context) {
                return new CacheUpdateResult(CacheUpdateStatus.ELEMENT_UPDATED, context);
            }

            static CacheUpdateResult ignoreUpdate() {
                return new CacheUpdateResult(CacheUpdateStatus.IGNORE_UPDATE, null);
            }

            static CacheUpdateResult elementNotFound() {
                return new CacheUpdateResult(CacheUpdateStatus.ELEMENT_NOT_FOUND, null);
            }
        }

        private CacheUpdateResult onInjectionUpdate(Injection<?> injection, BiFunction<AcLoadFlowContext, LfBus, CacheUpdateResult> handler) {
            for (AcLoadFlowContext context : contexts) {
                LfBus lfBus = getLfBus(injection, context).orElse(null);
                if (lfBus != null) {
                    return handler.apply(context, lfBus);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private CacheUpdateResult onGeneratorUpdate(Generator generator, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(generator, (context, lfBus) -> {
                if (attribute.equals("targetV")) {
                    double valueShift = (double) newValue - (double) oldValue;
                    GeneratorVoltageControl voltageControl = lfBus.getGeneratorVoltageControl().orElseThrow();
                    double nominalV = voltageControl.getControlledBus().getNominalV();
                    double newTargetV = voltageControl.getTargetValue() + valueShift / nominalV;
                    LfNetworkParameters networkParameters = context.getParameters().getNetworkParameters();
                    if (AbstractLfGenerator.checkTargetV(generator.getId(), newTargetV, nominalV, networkParameters, null)) {
                        voltageControl.setTargetValue(newTargetV);
                    } else {
                        context.getNetwork().getGeneratorById(generator.getId()).setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
                        if (lfBus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE)) {
                            lfBus.setGeneratorVoltageControlEnabled(false);
                        }
                    }
                    context.getNetwork().validate(LoadFlowModel.AC, null);
                    return CacheUpdateResult.elementUpdated(context);
                }
                return CacheUpdateResult.unsupportedUpdate();
            });
        }

        private CacheUpdateResult onShuntUpdate(ShuntCompensator shunt, String attribute) {
            return onInjectionUpdate(shunt, (context, lfBus) -> {
                if (attribute.equals("sectionCount")) {
                    if (lfBus.getControllerShunt().isEmpty()) {
                        LfShunt lfShunt = lfBus.getShunt().orElseThrow();
                        lfShunt.reInit();
                        return CacheUpdateResult.elementUpdated(context);
                    } else {
                        LOGGER.info("Shunt compensator {} is controlling voltage or connected to a bus containing a shunt compensator" +
                                "with an active voltage control: not supported", shunt.getId());
                        return CacheUpdateResult.unsupportedUpdate();
                    }
                }
                return CacheUpdateResult.unsupportedUpdate();
            });
        }

        private CacheUpdateResult onSwitchUpdate(String switchId, boolean open) {
            for (AcLoadFlowContext context : contexts) {
                LfNetwork lfNetwork = context.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(switchId);
                if (lfBranch != null) {
                    updateSwitch(open, lfNetwork, lfBranch);
                    return CacheUpdateResult.elementUpdated(context);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private static void updateSwitch(boolean open, LfNetwork lfNetwork, LfBranch lfBranch) {
            var connectivity = lfNetwork.getConnectivity();
            connectivity.startTemporaryChanges();
            try {
                if (open) {
                    connectivity.removeEdge(lfBranch);
                } else {
                    connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
                }
                LfAction.updateBusesAndBranchStatus(connectivity);
            } finally {
                connectivity.undoTemporaryChanges();
            }
        }

        private CacheUpdateResult onTransformerTargetVoltageUpdate(String twtId, double newValue) {
            for (AcLoadFlowContext context : contexts) {
                LfNetwork lfNetwork = context.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    var vc = lfBranch.getVoltageControl().orElseThrow();
                    vc.setTargetValue(newValue / vc.getControlledBus().getNominalV());
                    return CacheUpdateResult.elementUpdated(context);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private CacheUpdateResult onTransformerTapPositionUpdate(String twtId, int newTapPosition) {
            for (AcLoadFlowContext context : contexts) {
                LfNetwork lfNetwork = context.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    lfBranch.getPiModel().setTapPosition(newTapPosition);
                    return CacheUpdateResult.elementUpdated(context);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        void processUpdateResult(Identifiable<?> identifiable, String attribute, CacheUpdateResult result) {
            switch (result.status) {
                case UNSUPPORTED_UPDATE -> reset();
                case ELEMENT_UPDATED -> result.context.setNetworkUpdated(true);
                case IGNORE_UPDATE -> { /* nothing to do */ }
                case ELEMENT_NOT_FOUND -> LOGGER.warn("Cannot update attribute '{}' of element '{}' (type={})", attribute, identifiable.getId(), identifiable.getType());
            }
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, String variantId, Object oldValue, Object newValue) {
            if (contexts == null || pause) {
                return;
            }
            CacheUpdateResult result = CacheUpdateResult.unsupportedUpdate(); // by default to be safe
            switch (attribute) {
                case "v",
                     "angle",
                     "p",
                     "q",
                     "p1",
                     "q1",
                     "p2",
                     "q2",
                     "p3",
                     "q3" -> result = CacheUpdateResult.ignoreUpdate(); // ignore because it is related to state update and won't affect LF calculation
                default -> {
                    if (identifiable.getType() == IdentifiableType.GENERATOR) {
                        Generator generator = (Generator) identifiable;
                        if (attribute.equals("targetV")) {
                            result = onGeneratorUpdate(generator, attribute, oldValue, newValue);
                        }
                    } else if (identifiable.getType() == IdentifiableType.SHUNT_COMPENSATOR) {
                        ShuntCompensator shunt = (ShuntCompensator) identifiable;
                        if (attribute.equals("sectionCount")) {
                            result = onShuntUpdate(shunt, attribute);
                        }
                    } else if (identifiable.getType() == IdentifiableType.SWITCH
                            && attribute.equals("open")) {
                        result = onSwitchUpdate(identifiable.getId(), (boolean) newValue);
                    } else if (identifiable.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER) {
                        if (attribute.equals("ratioTapChanger.regulationValue")) {
                            result = onTransformerTargetVoltageUpdate(identifiable.getId(), (double) newValue);
                        } else if (attribute.equals("ratioTapChanger.tapPosition")) {
                            result = onTransformerTapPositionUpdate(identifiable.getId(), (int) newValue);
                        }
                    } else if (identifiable.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER) {
                        for (ThreeSides side : ThreeSides.values()) {
                            if (attribute.equals("ratioTapChanger" + side.getNum() + ".regulationValue")) {
                                result = onTransformerTargetVoltageUpdate(LfLegBranch.getId(identifiable.getId(), side.getNum()), (double) newValue);
                                break;
                            } else if (attribute.equals("ratioTapChanger" + side.getNum() + ".tapPosition")) {
                                result = onTransformerTapPositionUpdate(LfLegBranch.getId(identifiable.getId(), side.getNum()), (int) newValue);
                                break;
                            }
                        }
                    }
                }
            }

            processUpdateResult(identifiable, attribute, result);
        }

        @Override
        public void onExtensionUpdate(Extension<?> extension, String attribute, Object oldValue, Object newValue) {
            if (contexts == null || pause) {
                return;
            }

            CacheUpdateResult result = CacheUpdateResult.unsupportedUpdate();
            if ("secondaryVoltageControl".equals(extension.getName())) {
                SecondaryVoltageControl svc = (SecondaryVoltageControl) extension;
                result = onSecondaryVoltageControlExtensionUpdate(svc, attribute, newValue);
            }

            processUpdateResult((Identifiable<?>) extension.getExtendable(), attribute, result);
        }

        private CacheUpdateResult onSecondaryVoltageControlExtensionUpdate(SecondaryVoltageControl svc, String attribute, Object newValue) {
            if ("pilotPointTargetV".equals(attribute)) {
                PilotPoint.TargetVoltageEvent event = (PilotPoint.TargetVoltageEvent) newValue;
                ControlZone controlZone = svc.getControlZone(event.controlZoneName()).orElseThrow();
                for (AcLoadFlowContext context : contexts) {
                    LfNetwork lfNetwork = context.getNetwork();
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        lfSvc.setTargetValue(event.value() / lfSvc.getPilotBus().getNominalV());
                        return CacheUpdateResult.elementUpdated(context);
                    }
                }
                return CacheUpdateResult.elementNotFound();
            } else if ("controlUnitParticipate".equals(attribute)) {
                ControlUnit.ParticipateEvent event = (ControlUnit.ParticipateEvent) newValue;
                ControlZone controlZone = svc.getControlZone(event.controlZoneName()).orElseThrow();
                for (AcLoadFlowContext context : contexts) {
                    LfNetwork lfNetwork = context.getNetwork();
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        if (event.value()) {
                            lfSvc.addParticipatingControlUnit(event.controlUnitId());
                        } else {
                            lfSvc.removeParticipatingControlUnit(event.controlUnitId());
                        }
                        return CacheUpdateResult.elementUpdated(context);
                    }
                }
                return CacheUpdateResult.elementNotFound();
            }
            return CacheUpdateResult.unsupportedUpdate();
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
            Network network = networkRef.get();
            if (network != null && tmpVariantId != null) {
                network.getVariantManager().removeVariant(tmpVariantId);
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
                .filter(e -> e.getNetworkRef().get() == network && e.getWorkingVariantId().equals(variantId))
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
                if (result != null && result.getSolverStatus() == AcSolverStatus.CONVERGED) {
                    context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer(true));
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
