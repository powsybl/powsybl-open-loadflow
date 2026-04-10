/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.fastrestart;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ControlUnit;
import com.powsybl.iidm.network.extensions.ControlZone;
import com.powsybl.iidm.network.extensions.PilotPoint;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.lf.AbstractLoadFlowContext;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.impl.AbstractLfGenerator;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public enum NetworkCache {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCache.class);

    public static class Entry<C extends AbstractLoadFlowContext> extends DefaultNetworkListener {

        private final WeakReference<Network> networkRef;

        private final String workingVariantId;
        private String tmpVariantId;

        private final LoadFlowParameters parameters;

        private HashMap<LfNetwork, LfNetworkParameters> lfNetworksAndParameters;
        private List<C> contexts;

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

        public List<C> getContexts() {
            return contexts;
        }

        public void setContexts(List<C> contexts) {
            lfNetworksAndParameters = new HashMap<>();
            this.contexts = contexts;
            for (C context : contexts) {
                lfNetworksAndParameters.put(context.getNetwork(), context.getParameters().getNetworkParameters());
            }
        }

        public LoadFlowParameters getParameters() {
            return parameters;
        }

        public void setPause(boolean pause) {
            this.pause = pause;
        }

        private void reset() {
            if (contexts != null) {
                for (C context : contexts) {
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

        private static Optional<Bus> getBus(Injection<?> injection, boolean isBreakers) {
            return Optional.ofNullable(isBreakers
                    ? injection.getTerminal().getBusBreakerView().getBus()
                    : injection.getTerminal().getBusView().getBus());
        }

        private static Optional<LfBus> getLfBus(Injection<?> injection, LfNetwork lfNetwork, boolean isBreakers) {
            return getBus(injection, isBreakers)
                    .map(bus -> lfNetwork.getBusById(bus.getId()));
        }

        enum CacheUpdateStatus {
            UNSUPPORTED_UPDATE,
            ELEMENT_UPDATED,
            IGNORE_UPDATE,
            ELEMENT_NOT_FOUND
        }

        record CacheUpdateResult(CacheUpdateStatus status, LfNetwork lfNetwork) {
            static CacheUpdateResult unsupportedUpdate() {
                return new CacheUpdateResult(CacheUpdateStatus.UNSUPPORTED_UPDATE, null);
            }

            static CacheUpdateResult elementUpdated(LfNetwork lfNetwork) {
                return new CacheUpdateResult(CacheUpdateStatus.ELEMENT_UPDATED, lfNetwork);
            }

            static CacheUpdateResult ignoreUpdate() {
                return new CacheUpdateResult(CacheUpdateStatus.IGNORE_UPDATE, null);
            }

            static CacheUpdateResult elementNotFound() {
                return new CacheUpdateResult(CacheUpdateStatus.ELEMENT_NOT_FOUND, null);
            }
        }

        private CacheUpdateResult onInjectionUpdate(Injection<?> injection, TriFunction<LfNetwork, LfNetworkParameters, LfBus, CacheUpdateResult> handler) {
            for (Map.Entry<LfNetwork, LfNetworkParameters> lfNetworkAndParam : lfNetworksAndParameters.entrySet()) {
                LfNetwork lfNetwork = lfNetworkAndParam.getKey();
                LfNetworkParameters params = lfNetworkAndParam.getValue();
                LfBus lfBus = getLfBus(injection, lfNetwork, params.isBreakers()).orElse(null);
                if (lfBus != null) {
                    return handler.apply(lfNetwork, params, lfBus);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private static CacheUpdateResult updateLfGeneratorTargetP(String id, double oldValue, double newValue, LfNetwork network, LfNetworkParameters networkParameters, LfBus lfBus) {
            double valueShift = newValue - oldValue;
            LfGenerator lfGenerator = lfBus.getNetwork().getGeneratorById(id);
            double newTargetP = lfGenerator.getInitialTargetP() + valueShift / PerUnit.SB;
            lfGenerator.setTargetP(newTargetP);
            lfGenerator.setInitialTargetP(newTargetP);
            lfGenerator.reApplyActivePowerControlChecks(networkParameters, null);
            return CacheUpdateResult.elementUpdated(network);
        }

        private CacheUpdateResult onGeneratorUpdate(Generator generator, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(generator, (lfNetwork, networkParameters, lfBus) -> {
                if (attribute.equals("targetV")) {
                    double valueShift = (double) newValue - (double) oldValue;
                    GeneratorVoltageControl voltageControl = lfBus.getGeneratorVoltageControl().orElseThrow();
                    double nominalV = voltageControl.getControlledBus().getNominalV();
                    double newTargetV = voltageControl.getTargetValue() + valueShift / nominalV;
                    if (AbstractLfGenerator.checkTargetV(generator.getId(), newTargetV, nominalV, networkParameters, null)) {
                        voltageControl.setTargetValue(newTargetV);
                    } else {
                        lfNetwork.getGeneratorById(generator.getId()).setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
                        if (lfBus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE)) {
                            lfBus.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
                        }
                    }
                    lfNetwork.validate(LoadFlowModel.AC, null);
                    return CacheUpdateResult.elementUpdated(lfNetwork);
                } else if (attribute.equals("targetP")) {
                    return updateLfGeneratorTargetP(generator.getId(), (double) oldValue, (double) newValue, lfNetwork, networkParameters, lfBus);
                }
                return CacheUpdateResult.unsupportedUpdate();
            });
        }

        private CacheUpdateResult onBatteryUpdate(Battery battery, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(battery, (lfNetwork, networkParameters, lfBus) -> {
                if (attribute.equals("targetP")) {
                    return updateLfGeneratorTargetP(battery.getId(), (double) oldValue, (double) newValue, lfNetwork, networkParameters, lfBus);
                }
                return CacheUpdateResult.unsupportedUpdate();
            });
        }

        private CacheUpdateResult onShuntUpdate(ShuntCompensator shunt, String attribute) {
            return onInjectionUpdate(shunt, (lfNetwork, networkParameters, lfBus) -> {
                if (attribute.equals("sectionCount")) {
                    if (lfBus.getControllerShunt().isEmpty()) {
                        LfShunt lfShunt = lfBus.getShunt().orElseThrow();
                        lfShunt.reInit();
                        return CacheUpdateResult.elementUpdated(lfNetwork);
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
            for (LfNetwork lfNetwork : lfNetworksAndParameters.keySet()) {
                LfBranch lfBranch = lfNetwork.getBranchById(switchId);
                if (lfBranch != null) {
                    updateSwitch(open, lfNetwork, lfBranch);
                    return CacheUpdateResult.elementUpdated(lfNetwork);
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
                AbstractLfBranchAction.updateBusesAndBranchStatus(connectivity);
            } finally {
                connectivity.undoTemporaryChanges();
            }
        }

        private CacheUpdateResult onTransformerTargetVoltageUpdate(String twtId, double newValue) {
            for (LfNetwork lfNetwork : lfNetworksAndParameters.keySet()) {
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    var vc = lfBranch.getVoltageControl().orElseThrow();
                    vc.setTargetValue(newValue / vc.getControlledBus().getNominalV());
                    return CacheUpdateResult.elementUpdated(lfNetwork);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private CacheUpdateResult onTransformerTapPositionUpdate(String twtId, int newTapPosition) {
            for (LfNetwork lfNetwork : lfNetworksAndParameters.keySet()) {
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    lfBranch.getPiModel().setTapPosition(newTapPosition);
                    return CacheUpdateResult.elementUpdated(lfNetwork);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        void processUpdateResult(Identifiable<?> identifiable, String attribute, CacheUpdateResult result) {
            switch (result.status) {
                case UNSUPPORTED_UPDATE -> reset();
                case ELEMENT_UPDATED -> result.lfNetwork.setNetworkUpdated(true);
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
                        if (attribute.equals("targetV") || attribute.equals("targetP")) {
                            result = onGeneratorUpdate(generator, attribute, oldValue, newValue);
                        }
                    } else if (identifiable.getType() == IdentifiableType.BATTERY) {
                        Battery battery = (Battery) identifiable;
                        if (attribute.equals("targetP")) {
                            result = onBatteryUpdate(battery, attribute, oldValue, newValue);
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
        public void onExtensionUpdate(Extension<?> extension, String attribute, String variantId, Object oldValue, Object newValue) {
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
                for (LfNetwork lfNetwork : lfNetworksAndParameters.keySet()) {
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        lfSvc.setTargetValue(event.value() / lfSvc.getPilotBus().getNominalV());
                        return CacheUpdateResult.elementUpdated(lfNetwork);
                    }
                }
                return CacheUpdateResult.elementNotFound();
            } else if ("controlUnitParticipate".equals(attribute)) {
                ControlUnit.ParticipateEvent event = (ControlUnit.ParticipateEvent) newValue;
                ControlZone controlZone = svc.getControlZone(event.controlZoneName()).orElseThrow();
                for (LfNetwork lfNetwork : lfNetworksAndParameters.keySet()) {
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        if (event.value()) {
                            lfSvc.addParticipatingControlUnit(event.controlUnitId());
                        } else {
                            lfSvc.removeParticipatingControlUnit(event.controlUnitId());
                        }
                        return CacheUpdateResult.elementUpdated(lfNetwork);
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
        public void onPropertyAdded(Identifiable identifiable, String attribute, Object newValue) {
            onPropertyChange();
        }

        @Override
        public void onPropertyReplaced(Identifiable identifiable, String attribute, Object oldValue, Object newValue) {
            onPropertyChange();
        }

        @Override
        public void onPropertyRemoved(Identifiable identifiable, String attribute, Object oldValue) {
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

    private final List<Entry<AcLoadFlowContext>> acEntries = new ArrayList<>();

    private final List<Entry<DcLoadFlowContext>> dcEntries = new ArrayList<>();

    private final Lock lock = new ReentrantLock();

    private void evictDeadEntries() {
        Iterator<Entry<AcLoadFlowContext>> itAc = acEntries.iterator();
        while (itAc.hasNext()) {
            Entry<AcLoadFlowContext> entry = itAc.next();
            if (entry.getNetworkRef().get() == null) {
                // release all resources
                entry.close();
                itAc.remove();
                LOGGER.info("Dead network removed from AC load flow cache ({} remains)", acEntries.size());
            }
        }
        Iterator<Entry<DcLoadFlowContext>> itDc = dcEntries.iterator();
        while (itDc.hasNext()) {
            Entry<DcLoadFlowContext> entry = itDc.next();
            if (entry.getNetworkRef().get() == null) {
                // release all resources
                entry.close();
                itDc.remove();
                LOGGER.info("Dead network removed from DC load flow cache ({} remains)", dcEntries.size());
            }
        }
    }

    public int getAcEntryCount() {
        lock.lock();
        try {
            evictDeadEntries();
            return acEntries.size();
        } finally {
            lock.unlock();
        }
    }

    public int getDcEntryCount() {
        lock.lock();
        try {
            evictDeadEntries();
            return dcEntries.size();
        } finally {
            lock.unlock();
        }
    }

    public Optional<Entry<AcLoadFlowContext>> findEntryAc(Network network) {
        String variantId = network.getVariantManager().getWorkingVariantId();
        return acEntries.stream()
                .filter(e -> e.getNetworkRef().get() == network && e.getWorkingVariantId().equals(variantId))
                .findFirst();
    }

    public Optional<Entry<DcLoadFlowContext>> findEntryDc(Network network) {
        String variantId = network.getVariantManager().getWorkingVariantId();
        return dcEntries.stream()
                .filter(e -> e.getNetworkRef().get() == network && e.getWorkingVariantId().equals(variantId))
                .findFirst();
    }

    private Entry get(Network network, LoadFlowParameters parameters, boolean isDc) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(parameters);

        Entry entry;
        lock.lock();
        try {
            evictDeadEntries();

            entry = isDc ? findEntryDc(network).orElse(null) : findEntryAc(network).orElse(null);

            // invalid cache if parameters have changed
            // TODO to refine later by comparing in detail parameters that have changed
            if (entry != null && !OpenLoadFlowParameters.equals(parameters, entry.getParameters())) {
                // release all resources
                entry.close();
                if (isDc) {
                    dcEntries.remove(entry);
                } else {
                    acEntries.remove(entry);
                }
                entry = null;
                LOGGER.info("Network cache evicted because of parameters change");
            }

            if (entry == null) {
                entry = new Entry<>(network, OpenLoadFlowParameters.clone(parameters));
                if (isDc) {
                    dcEntries.add(entry);
                } else {
                    acEntries.add(entry);
                }
                network.addListener(entry);
                LOGGER.info("Network cache created for network '{}' and variant '{}'",
                        network.getId(), network.getVariantManager().getWorkingVariantId());

                return entry;
            }
        } finally {
            lock.unlock();
        }

        return entry;
    }

    public Entry<AcLoadFlowContext> getAc(Network network, LoadFlowParameters parameters) {
        Entry<AcLoadFlowContext> entry = get(network, parameters, false);

        // restart from previous state
        if (entry.getContexts() != null) {
            LOGGER.info("Network cache for AC load flow reused for network '{}' and variant '{}'",
                    network.getId(), network.getVariantManager().getWorkingVariantId());

            for (AcLoadFlowContext acContext : entry.getContexts()) {
                AcLoadFlowResult result = acContext.getResult();
                if (result != null && result.getSolverStatus() == AcSolverStatus.CONVERGED) {
                    acContext.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer(true));
                }
            }
        } else {
            LOGGER.info("Network cache for AC load flow cannot be reused for network '{}' because invalided", network.getId());
        }

        return entry;
    }

    public Entry<DcLoadFlowContext> getDc(Network network, LoadFlowParameters parameters) {
        Entry<DcLoadFlowContext> entry = get(network, parameters, true);

        if (entry.getContexts() != null) {
            LOGGER.info("Network cache for DC load flow reused for network '{}' and variant '{}'",
                    network.getId(), network.getVariantManager().getWorkingVariantId());
        } else {
            LOGGER.info("Network cache for DC load flow cannot be reused for network '{}' because invalided", network.getId());
        }

        return entry;
    }

    public void clear() {
        lock.lock();
        try {
            for (var entry : acEntries) {
                entry.close();
            }
            acEntries.clear();
            for (var entry : dcEntries) {
                entry.close();
            }
            dcEntries.clear();
        } finally {
            lock.unlock();
        }
    }
}
