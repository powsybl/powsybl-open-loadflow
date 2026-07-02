/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.util.PerUnit;
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
public class NetworkCache<I extends NetworkCache.Input<I>, V extends NetworkCache.Value> {

    public static final NetworkCache<LfInput, AcLfValue> AC_LF_INSTANCE = new NetworkCache<>(AcLfEntry::new);

    public static final NetworkCache<LfInput, DcLfValue> DC_LF_INSTANCE = new NetworkCache<>(DcLfEntry::new);

    public static final NetworkCache<DcSensiInput, DcSensiValue> DC_SENSI_INSTANCE = new NetworkCache<>(DcSensiEntry::new);

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkCache.class);

    /**
     * Input associated to a cache entry, used to detect when input data of the previous run has changed.
     */
    public interface Input<T extends Input<T>> {

        T copy();

        String hasChanged(T other);

        LoadFlowParameters getLoadFlowParameters();
    }

    /**
     * Value associated to a cache entry, used to store the LF network and its parameters.
     */
    public interface Value {

        LfNetwork getNetwork();

        LfNetworkParameters getNetworkParameters();

        boolean isNetworkUpdated();

        void setNetworkUpdated(boolean networkUpdated);

        boolean isTopologyUpdated(); // used to know if network state variables should be reset (e.g. after switch actions)

        void setTopologyUpdated(boolean networkUpdated);

        void close();
    }

    /**
     * Cache entry to store everything associated to a given IIDM network.
     */
    public interface Entry<I extends Input<I>, V extends Value> {

        WeakReference<Network> getNetworkRef();

        String getWorkingVariantId();

        void setVariantCleaner(LfNetworkList.VariantCleaner variantCleaner);

        List<V> getValues();

        void setValues(List<V> values);

        I getInput();

        void setPause(boolean pause);

        Set<String> getInvalidationReasons();

        void clearInvalidationReasons();

        void restart();

        void close();
    }

    public static class LfInput implements Input<LfInput> {

        private final LoadFlowParameters parameters;

        public LfInput(LoadFlowParameters parameters) {
            this.parameters = Objects.requireNonNull(parameters);
        }

        @Override
        public LfInput copy() {
            return new LfInput(OpenLoadFlowParameters.clone(parameters));
        }

        @Override
        public String hasChanged(LfInput other) {
            // TODO to refine later by comparing in detail parameters that have changed
            return OpenLoadFlowParameters.equals(parameters, other.parameters) ? null : "parameters";
        }

        @Override
        public LoadFlowParameters getLoadFlowParameters() {
            return parameters;
        }
    }

    public abstract static class AbstractValue implements Value {

        private boolean networkUpdated = true;

        private boolean topologyUpdated = false;

        @Override
        public boolean isNetworkUpdated() {
            return networkUpdated;
        }

        @Override
        public void setNetworkUpdated(boolean networkUpdated) {
            this.networkUpdated = networkUpdated;
        }

        @Override
        public boolean isTopologyUpdated() {
            return topologyUpdated;
        }

        @Override
        public void setTopologyUpdated(boolean topologyUpdated) {
            this.topologyUpdated = topologyUpdated;
        }
    }

    public static class AcLfValue extends AbstractValue {

        private final AcLoadFlowContext context;

        public AcLfValue(AcLoadFlowContext context) {
            this.context = context;
        }

        public AcLoadFlowContext getContext() {
            return context;
        }

        @Override
        public LfNetwork getNetwork() {
            return context.getNetwork();
        }

        @Override
        public LfNetworkParameters getNetworkParameters() {
            return context.getParameters().getNetworkParameters();
        }

        @Override
        public void close() {
            context.close();
        }
    }

    public static class DcSensiInput implements Input<DcSensiInput> {

        private final LoadFlowParameters parameters;
        // this is what impact TopoConfig
        private final Set<String> topoActionIds;

        public DcSensiInput(LoadFlowParameters parameters, Set<String> topoActionIds) {
            this.parameters = Objects.requireNonNull(parameters);
            this.topoActionIds = Objects.requireNonNull(topoActionIds);
        }

        @Override
        public DcSensiInput copy() {
            return new DcSensiInput(OpenLoadFlowParameters.clone(parameters), topoActionIds);
        }

        @Override
        public String hasChanged(DcSensiInput other) {
            // TODO to refine later by comparing in detail parameters that have changed
            if (!OpenLoadFlowParameters.equals(parameters, other.parameters)) {
                return "parameters";
            }
            if (!topoActionIds.equals(other.topoActionIds)) {
                return "actions";
            }
            return null;
        }

        @Override
        public LoadFlowParameters getLoadFlowParameters() {
            return parameters;
        }
    }

    public static class DcSensiValue extends AbstractValue {

        private final DcLoadFlowContext context;

        public DcSensiValue(DcLoadFlowContext context) {
            this.context = context;
        }

        public DcLoadFlowContext getContext() {
            return context;
        }

        @Override
        public LfNetwork getNetwork() {
            return context.getNetwork();
        }

        @Override
        public LfNetworkParameters getNetworkParameters() {
            return context.getParameters().getNetworkParameters();
        }

        @Override
        public void close() {
            context.close();
        }
    }

    public static class AcLfEntry extends AbstractEntry<LfInput, AcLfValue> {

        public AcLfEntry(Network network, LfInput input) {
            super(network, input);
        }

        @Override
        public void restart() {
            if (values != null) {
                for (AcLfValue value : values) {
                    AcLoadFlowResult result = value.getContext().getResult();
                    if (result != null && result.getSolverStatus() == AcSolverStatus.CONVERGED) {
                        value.getContext().getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer(true));
                    }
                }
            }
        }
    }

    public static class DcLfValue extends AbstractValue {

        private final DcLoadFlowContext context;

        public DcLfValue(DcLoadFlowContext context) {
            this.context = context;
        }

        public DcLoadFlowContext getContext() {
            return context;
        }

        @Override
        public LfNetwork getNetwork() {
            return context.getNetwork();
        }

        @Override
        public LfNetworkParameters getNetworkParameters() {
            return context.getParameters().getNetworkParameters();
        }

        @Override
        public void close() {
            context.close();
        }
    }

    public static class DcLfEntry extends AbstractEntry<LfInput, DcLfValue> {

        public DcLfEntry(Network network, LfInput input) {
            super(network, input);
        }

        @Override
        public void restart() {
            // nothing to do
        }
    }

    public static class DcSensiEntry extends AbstractEntry<DcSensiInput, DcSensiValue> {

        public DcSensiEntry(Network network, DcSensiInput input) {
            super(network, input);
        }

        @Override
        public void restart() {
            // nothing to do
        }
    }

    public abstract static class AbstractEntry<I extends Input<I>, V extends Value> implements NetworkListener, Entry<I, V> {

        private final WeakReference<Network> networkRef;

        private final String workingVariantId;
        private LfNetworkList.VariantCleaner variantCleaner;

        private final I input;

        protected List<V> values;

        private boolean pause = false;

        private final Set<String> invalidationReasons = new LinkedHashSet<>();

        protected AbstractEntry(Network network, I input) {
            Objects.requireNonNull(network);
            this.networkRef = new WeakReference<>(network);
            this.workingVariantId = network.getVariantManager().getWorkingVariantId();
            this.input = Objects.requireNonNull(input);
            network.addListener(this);
        }

        @Override
        public WeakReference<Network> getNetworkRef() {
            return networkRef;
        }

        @Override
        public String getWorkingVariantId() {
            return workingVariantId;
        }

        @Override
        public void setVariantCleaner(LfNetworkList.VariantCleaner variantCleaner) {
            if (this.variantCleaner != null) {
                throw new PowsyblException("Entry already has a variant cleaner set");
            }
            this.variantCleaner = variantCleaner;
        }

        @Override
        public List<V> getValues() {
            return values;
        }

        @Override
        public void setValues(List<V> values) {
            this.values = values;
        }

        @Override
        public I getInput() {
            return input;
        }

        @Override
        public void setPause(boolean pause) {
            this.pause = pause;
        }

        @Override
        public Set<String> getInvalidationReasons() {
            return invalidationReasons;
        }

        @Override
        public void clearInvalidationReasons() {
            invalidationReasons.clear();
        }

        protected void reset(String invalidationReason) {
            if (values != null) {
                for (V value : values) {
                    value.close();
                }
                values = null;
                invalidationReasons.add(invalidationReason);
            }
            if (variantCleaner != null) {
                variantCleaner.clean();
                variantCleaner = null;
            }
        }

        private void onStructureChange() {
            // too difficult to update LfNetwork incrementally
            reset("structure");
        }

        @Override
        public void onCreation(Identifiable identifiable) {
            onStructureChange();
        }

        @Override
        public void afterRemoval(String s) {
            onStructureChange();
        }

        private static <V extends Value> Optional<Bus> getBus(Injection<?> injection, V value) {
            return Optional.ofNullable(value.getNetworkParameters().isBreakers()
                    ? injection.getTerminal().getBusBreakerView().getBus()
                    : injection.getTerminal().getBusView().getBus());
        }

        private static <V extends Value> Optional<LfBus> getLfBus(Injection<?> injection, V value) {
            return getBus(injection, value)
                    .map(bus -> value.getNetwork().getBusById(bus.getId()));
        }

        enum CacheUpdateStatus {
            UNSUPPORTED_UPDATE,
            ELEMENT_UPDATED,
            ELEMENT_AND_TOPOLOGY_UPDATED,
            IGNORE_UPDATE,
            ELEMENT_NOT_FOUND
        }

        record CacheUpdateResult<V extends Value>(CacheUpdateStatus status, Set<V> values, String invalidationReason) {
            static <V extends Value> CacheUpdateResult<V> unsupportedUpdate(String invalidationReason) {
                return new CacheUpdateResult<>(CacheUpdateStatus.UNSUPPORTED_UPDATE, null, invalidationReason);
            }

            static <V extends Value> CacheUpdateResult<V> elementUpdated(V value) {
                return new CacheUpdateResult<>(CacheUpdateStatus.ELEMENT_UPDATED, Set.of(value), null);
            }

            static <V extends Value> CacheUpdateResult<V> multipleElementsUpdated(Set<V> values) { // used when multiple LfNetworks can be updated at once (e.g. hvdc)
                return new CacheUpdateResult<>(CacheUpdateStatus.ELEMENT_UPDATED, values, null);
            }

            static <V extends Value> CacheUpdateResult<V> elementAndTopologyUpdated(V value) {
                return new CacheUpdateResult<>(CacheUpdateStatus.ELEMENT_AND_TOPOLOGY_UPDATED, Set.of(value), null);
            }

            static <V extends Value> CacheUpdateResult<V> ignoreUpdate() {
                return new CacheUpdateResult<>(CacheUpdateStatus.IGNORE_UPDATE, null, null);
            }

            static <V extends Value> CacheUpdateResult<V> elementNotFound() {
                return new CacheUpdateResult<>(CacheUpdateStatus.ELEMENT_NOT_FOUND, null, null);
            }
        }

        private CacheUpdateResult<V> onInjectionUpdate(Injection<?> injection, BiFunction<V, LfBus, CacheUpdateResult<V>> handler) {
            for (V value : values) {
                LfBus lfBus = getLfBus(injection, value).orElse(null);
                if (lfBus != null) {
                    return handler.apply(value, lfBus);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private static <V extends Value> CacheUpdateResult<V> updateLfGeneratorTargetP(String id, double oldValue, double newValue, V value, LfBus lfBus) {
            double valueShift = newValue - oldValue;
            LfGenerator lfGenerator = lfBus.getNetwork().getGeneratorById(id);
            double newTargetP = lfGenerator.getInitialTargetP() + valueShift / PerUnit.SB;
            lfGenerator.setTargetP(newTargetP);
            lfGenerator.setInitialTargetP(newTargetP);
            lfGenerator.reApplyActivePowerControlChecks(value.getNetworkParameters(), null);
            return CacheUpdateResult.elementUpdated(value);
        }

        private static <V extends Value> CacheUpdateResult<V> updateLfLoadTargetP(String id, double oldValue, double newValue, V value, LfBus lfBus) {
            // Load active power distribution is not handled
            double valueShift = newValue - oldValue;
            LfLoad lfLoad = lfBus.getNetwork().getLoadById(id);
            double newTargetP = lfLoad.getInitialTargetP() + valueShift / PerUnit.SB;
            lfLoad.setTargetP(newTargetP);
            lfLoad.setInitialTargetP(newTargetP);
            return CacheUpdateResult.elementUpdated(value);
        }

        private static <V extends Value> CacheUpdateResult<V> updateLfLoadTargetQ(String id, double oldValue, double newValue, V value, LfBus lfBus) {
            double valueShift = newValue - oldValue;
            LfLoad lfLoad = lfBus.getNetwork().getLoadById(id);
            double newTargetQ = lfLoad.getTargetQ() + valueShift / PerUnit.SB;
            lfLoad.setTargetQ(newTargetQ);
            return CacheUpdateResult.elementUpdated(value);
        }

        private static String createInvalidationReason(Identifiable<?> identifiable, String attribute) {
            return identifiable.getType() + "_" + attribute;
        }

        private static String createInvalidationReason(Extension<?> extension, String attribute) {
            return extension.getName() + "_" + attribute;
        }

        private CacheUpdateResult<V> onGeneratorUpdate(Generator generator, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(generator, (value, lfBus) -> onGeneratorUpdate(generator, attribute, oldValue, newValue, value, lfBus));
        }

        private CacheUpdateResult<V> onGeneratorUpdate(Generator generator, String attribute, Object oldValue, Object newValue,
                                                       V value, LfBus lfBus) {
            if ("targetV".equals(attribute)) {
                double valueShift = (double) newValue - (double) oldValue;
                GeneratorVoltageControl voltageControl = lfBus.getGeneratorVoltageControl().orElseThrow();
                double nominalV = voltageControl.getControlledBus().getNominalV();
                double newTargetV = voltageControl.getTargetValue() + valueShift / nominalV;
                LfNetworkParameters networkParameters = value.getNetworkParameters();
                if (AbstractLfGenerator.checkTargetV(generator.getId(), newTargetV, nominalV, networkParameters, null)) {
                    voltageControl.setTargetValue(newTargetV);
                } else {
                    value.getNetwork().getGeneratorById(generator.getId()).setGeneratorControlType(LfGenerator.GeneratorControlType.OFF);
                    if (lfBus.getGenerators().stream().noneMatch(gen -> gen.getGeneratorControlType() == LfGenerator.GeneratorControlType.VOLTAGE)) {
                        lfBus.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
                    }
                }
                value.getNetwork().validate(input.getLoadFlowParameters().isDc() ? LoadFlowModel.DC : LoadFlowModel.AC, null);
                return CacheUpdateResult.elementUpdated(value);
            } else if ("targetP".equals(attribute)) {
                return updateLfGeneratorTargetP(generator.getId(), (double) oldValue, (double) newValue, value, lfBus);
            }
            return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(generator, attribute));
        }

        private CacheUpdateResult<V> onBatteryUpdate(Battery battery, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(battery, (value, lfBus) -> {
                if ("targetP".equals(attribute)) {
                    return updateLfGeneratorTargetP(battery.getId(), (double) oldValue, (double) newValue, value, lfBus);
                }
                return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(battery, attribute));
            });
        }

        private CacheUpdateResult<V> onLoadUpdate(Load load, String attribute, Object oldValue, Object newValue) {
            return onInjectionUpdate(load, (value, lfBus) -> {
                if ("p0".equals(attribute)) {
                    LoadDetail loadDetail = load.getExtension(LoadDetail.class);
                    if (loadDetail != null) {
                        LOGGER.info("Load {} has a LoadDetail extension: not supported", load.getId());
                        return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(load, attribute));
                    }
                    if ((input.getLoadFlowParameters().getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                            || input.getLoadFlowParameters().getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                            && input.getLoadFlowParameters().isDistributedSlack()) {
                        LOGGER.info("Load active power distribution is enabled: not supported");
                        return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(load, attribute));
                    }
                    return updateLfLoadTargetP(load.getId(), (double) oldValue, (double) newValue, value, lfBus);
                }
                return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(load, attribute));
            });
        }

        private CacheUpdateResult<V> onBoundaryLineUpdate(BoundaryLine boundaryLine, String attribute, Object oldValue, Object newValue) {
            if ("p0".equals(attribute)) {
                if (!boundaryLine.isPaired()) {
                    for (V value : values) {
                        LfBus lfBus = value.getNetwork().getBusById(LfBoundaryLineBus.getId(boundaryLine));
                        if (lfBus != null) {
                            double valueShift = (double) newValue - (double) oldValue;
                            LfLoad lfLoad = lfBus.getLoads().getFirst();
                            double newTargetP = lfLoad.getTargetP() + valueShift / PerUnit.SB;
                            lfLoad.setTargetP(newTargetP);
                            return CacheUpdateResult.elementUpdated(value);
                        }
                    }
                    return CacheUpdateResult.elementNotFound();
                }
            }
            return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(boundaryLine, attribute));
        }

        private CacheUpdateResult<V> onShuntUpdate(ShuntCompensator shunt, String attribute) {
            return onInjectionUpdate(shunt, (value, lfBus) -> {
                if ("sectionCount".equals(attribute)) {
                    if (lfBus.getControllerShunt().isEmpty()) {
                        LfShunt lfShunt = lfBus.getShunt().orElseThrow();
                        lfShunt.reInit();
                        return CacheUpdateResult.elementUpdated(value);
                    } else {
                        LOGGER.info("Shunt compensator {} is controlling voltage or connected to a bus containing a shunt compensator" +
                                "with an active voltage control: not supported", shunt.getId());
                        return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(shunt, attribute));
                    }
                }
                return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(shunt, attribute));
            });
        }

        private CacheUpdateResult<V> onSwitchUpdate(String switchId, boolean open) {
            for (V value : values) {
                LfNetwork lfNetwork = value.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(switchId);
                if (lfBranch != null) {
                    updateSwitch(open, lfNetwork, lfBranch, value.isTopologyUpdated());
                    return CacheUpdateResult.elementAndTopologyUpdated(value);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private void updateSwitch(boolean open, LfNetwork lfNetwork, LfBranch lfBranch, boolean isTopologyUpdated) {
            var connectivity = lfNetwork.getConnectivity();
            if (!isTopologyUpdated) { // If this is the first temporary change
                connectivity.startTemporaryChanges();
            }
            if (open) {
                connectivity.removeEdge(lfBranch);
            } else {
                connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
            }
            AbstractLfBranchAction.updateBusesAndBranchStatus(connectivity);
        }

        private CacheUpdateResult<V> onTransformerTargetVoltageUpdate(String twtId, double newValue) {
            for (V value : values) {
                LfNetwork lfNetwork = value.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    var vc = lfBranch.getVoltageControl().orElseThrow();
                    vc.setTargetValue(newValue / vc.getControlledBus().getNominalV());
                    return CacheUpdateResult.elementUpdated(value);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private CacheUpdateResult<V> onTransformerTapPositionUpdate(String twtId, int newTapPosition) {
            for (V value : values) {
                LfNetwork lfNetwork = value.getNetwork();
                LfBranch lfBranch = lfNetwork.getBranchById(twtId);
                if (lfBranch != null) {
                    lfBranch.getPiModel().setTapPosition(newTapPosition);
                    return CacheUpdateResult.elementUpdated(value);
                }
            }
            return CacheUpdateResult.elementNotFound();
        }

        private CacheUpdateResult<V> onHvdcLineWithLccActiveSetpointUpdate(HvdcLine hvdcLine, Object oldValue, Object newValue) {
            LccConverterStation rectifier = (LccConverterStation) (hvdcLine.getConvertersMode().equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER) ?
                    hvdcLine.getConverterStation1() : hvdcLine.getConverterStation2());
            LccConverterStation inverter = (LccConverterStation) (hvdcLine.getConvertersMode().equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER) ?
                    hvdcLine.getConverterStation2() : hvdcLine.getConverterStation1());

            // First updating rectifier station
            double oldRectifierTargetP = (double) oldValue;
            double oldRectifierTargetQ = HvdcConverterStations.getLccConverterStationLoadTargetQ(oldRectifierTargetP, rectifier.getPowerFactor());
            double newRectifierTargetP = (double) newValue;
            double newRectifierTargetQ = HvdcConverterStations.getLccConverterStationLoadTargetQ(newRectifierTargetP, rectifier.getPowerFactor());
            CacheUpdateResult<V> result1 = onInjectionUpdate(rectifier, (value, lfBus) -> {
                updateLfLoadTargetP(rectifier.getId(), oldRectifierTargetP, newRectifierTargetP, value, lfBus);
                updateLfLoadTargetQ(rectifier.getId(), oldRectifierTargetQ, newRectifierTargetQ, value, lfBus);
                return CacheUpdateResult.elementUpdated(value);
            });
            if (!result1.status.equals(CacheUpdateStatus.ELEMENT_UPDATED)) {
                return result1;
            }

            // Then updating inverter station
            double oldInverterTargetP = -HvdcConverterStations.getAbsoluteValueInverterPAc(oldRectifierTargetP, rectifier.getLossFactor(), inverter.getLossFactor(), hvdcLine);
            double oldInverterTargetQ = HvdcConverterStations.getLccConverterStationLoadTargetQ(oldInverterTargetP, inverter.getPowerFactor());
            double newInverterTargetP = -HvdcConverterStations.getAbsoluteValueInverterPAc(newRectifierTargetP, rectifier.getLossFactor(), inverter.getLossFactor(), hvdcLine);
            double newInverterTargetQ = HvdcConverterStations.getLccConverterStationLoadTargetQ(newInverterTargetP, inverter.getPowerFactor());
            CacheUpdateResult<V> result2 = onInjectionUpdate(inverter, (value, lfBus) -> {
                updateLfLoadTargetP(inverter.getId(), oldInverterTargetP, newInverterTargetP, value, lfBus);
                updateLfLoadTargetQ(inverter.getId(), oldInverterTargetQ, newInverterTargetQ, value, lfBus);
                return CacheUpdateResult.elementUpdated(value);
            });
            if (!result2.status.equals(CacheUpdateStatus.ELEMENT_UPDATED)) {
                return result2;
            }

            // Merging two values (that can be in two different LfNetworks) in one CacheUpdateResult
            return CacheUpdateResult.multipleElementsUpdated(Set.of(result1.values.iterator().next(), result2.values.iterator().next()));
        }

        private CacheUpdateResult<V> onHvdcLineWithVscActiveSetpointUpdate(HvdcLine hvdcLine, String attribute, Object oldValue, Object newValue) {
            HvdcAngleDroopActivePowerControl droopControl = hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
            if (droopControl != null && droopControl.isEnabled() && input.getLoadFlowParameters().isHvdcAcEmulation()) {
                LOGGER.info("HVDC {} is in AC emulation mode: not supported", hvdcLine.getId());
                return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(hvdcLine, attribute));
            }
            VscConverterStation rectifier = (VscConverterStation) (hvdcLine.getConvertersMode().equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER) ?
                    hvdcLine.getConverterStation1() : hvdcLine.getConverterStation2());
            VscConverterStation inverter = (VscConverterStation) (hvdcLine.getConvertersMode().equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER) ?
                    hvdcLine.getConverterStation2() : hvdcLine.getConverterStation1());

            // First updating rectifier station
            double oldRectifierTargetP = -(double) oldValue;
            double newRectifierTargetP = -(double) newValue;
            CacheUpdateResult<V> result1 = onInjectionUpdate(rectifier, (value, lfBus) -> {
                updateLfGeneratorTargetP(rectifier.getId(), oldRectifierTargetP, newRectifierTargetP, value, lfBus);
                return CacheUpdateResult.elementUpdated(value);
            });
            if (!result1.status.equals(CacheUpdateStatus.ELEMENT_UPDATED)) {
                return result1;
            }

            // Then updating inverter station
            double oldInverterTargetP = -HvdcConverterStations.getAbsoluteValueInverterPAc(oldRectifierTargetP, rectifier.getLossFactor(), inverter.getLossFactor(), hvdcLine);
            double newInverterTargetP = -HvdcConverterStations.getAbsoluteValueInverterPAc(newRectifierTargetP, rectifier.getLossFactor(), inverter.getLossFactor(), hvdcLine);
            CacheUpdateResult<V> result2 = onInjectionUpdate(inverter, (value, lfBus) -> {
                updateLfGeneratorTargetP(inverter.getId(), oldInverterTargetP, newInverterTargetP, value, lfBus);
                return CacheUpdateResult.elementUpdated(value);
            });
            if (!result2.status.equals(CacheUpdateStatus.ELEMENT_UPDATED)) {
                return result2;
            }

            // Merging two values (that can be in two different LfNetworks) in one CacheUpdateResult
            return CacheUpdateResult.multipleElementsUpdated(Set.of(result1.values.iterator().next(), result2.values.iterator().next()));
        }

        private CacheUpdateResult<V> onHvdcLineUpdate(HvdcLine hvdcLine, String attribute, Object oldValue, Object newValue) {
            if ("activePowerSetpoint".equals(attribute)) {
                if (hvdcLine.getConverterStation1().getHvdcType().equals(HvdcConverterStation.HvdcType.LCC)) {
                    return onHvdcLineWithLccActiveSetpointUpdate(hvdcLine, oldValue, newValue);
                } else if (hvdcLine.getConverterStation1().getHvdcType().equals(HvdcConverterStation.HvdcType.VSC)) {
                    return onHvdcLineWithVscActiveSetpointUpdate(hvdcLine, attribute, oldValue, newValue);
                }
            }
            return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(hvdcLine, attribute));
        }

        void processUpdateResult(Identifiable<?> identifiable, String attribute, CacheUpdateResult<V> result) {
            switch (result.status) {
                case UNSUPPORTED_UPDATE -> reset(result.invalidationReason);
                case ELEMENT_UPDATED -> {
                    for (V value : result.values) {
                        value.setNetworkUpdated(true);
                    }
                }
                case ELEMENT_AND_TOPOLOGY_UPDATED -> {
                    for (V value : result.values) {
                        value.setNetworkUpdated(true);
                        value.setTopologyUpdated(true);
                    }
                }
                case IGNORE_UPDATE -> { /* nothing to do */ }
                case ELEMENT_NOT_FOUND -> LOGGER.warn("Cannot update attribute '{}' of element '{}' (type={})", attribute, identifiable.getId(), identifiable.getType());
            }
        }

        private boolean skipUpdate(String variantId) {
            return values == null || pause || !variantId.equals(workingVariantId);
        }

        @Override
        public void onUpdate(Identifiable identifiable, String attribute, String variantId, Object oldValue, Object newValue) {
            if (skipUpdate(variantId)) {
                return;
            }
            CacheUpdateResult<V> result = CacheUpdateResult.unsupportedUpdate(createInvalidationReason(identifiable, attribute)); // by default to be safe
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
                        // supports attribute: "targetV" or "targetP"
                        Generator generator = (Generator) identifiable;
                        result = onGeneratorUpdate(generator, attribute, oldValue, newValue);
                    } else if (identifiable.getType() == IdentifiableType.BATTERY) {
                        // supports attribute: "targetP"
                        Battery battery = (Battery) identifiable;
                        result = onBatteryUpdate(battery, attribute, oldValue, newValue);
                    } else if (identifiable.getType() == IdentifiableType.LOAD) {
                        // supports attribute: "p0"
                        Load load = (Load) identifiable;
                        result = onLoadUpdate(load, attribute, oldValue, newValue);
                    } else if (identifiable.getType() == IdentifiableType.BOUNDARY_LINE) {
                        // supports attribute: "p0"
                        BoundaryLine boundaryLine = (BoundaryLine) identifiable;
                        result = onBoundaryLineUpdate(boundaryLine, attribute, oldValue, newValue);
                    } else if (identifiable.getType() == IdentifiableType.SHUNT_COMPENSATOR) {
                        // supports attribute: "sectionCount"
                        ShuntCompensator shunt = (ShuntCompensator) identifiable;
                        result = onShuntUpdate(shunt, attribute);
                    } else if (identifiable.getType() == IdentifiableType.HVDC_LINE) {
                        // supports attribute: "activePowerSetpoint"
                        HvdcLine hvdcLine = (HvdcLine) identifiable;
                        result = onHvdcLineUpdate(hvdcLine, attribute, oldValue, newValue);
                    } else if (identifiable.getType() == IdentifiableType.SWITCH && "open".equals(attribute)) {
                        result = onSwitchUpdate(identifiable.getId(), (boolean) newValue);
                    } else if (identifiable.getType() == IdentifiableType.TWO_WINDINGS_TRANSFORMER) {
                        if ("ratioTapChanger.regulationValue".equals(attribute)) {
                            result = onTransformerTargetVoltageUpdate(identifiable.getId(), (double) newValue);
                        } else if ("ratioTapChanger.tapPosition".equals(attribute)) {
                            result = onTransformerTapPositionUpdate(identifiable.getId(), (int) newValue);
                        }
                    } else if (identifiable.getType() == IdentifiableType.THREE_WINDINGS_TRANSFORMER) {
                        for (ThreeSides side : ThreeSides.values()) {
                            if (("ratioTapChanger" + side.getNum() + ".regulationValue").equals(attribute)) {
                                result = onTransformerTargetVoltageUpdate(LfLegBranch.getId(identifiable.getId(), side.getNum()), (double) newValue);
                                break;
                            } else if (("ratioTapChanger" + side.getNum() + ".tapPosition").equals(attribute)) {
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
            if (skipUpdate(variantId)) {
                return;
            }

            CacheUpdateResult<V> result = CacheUpdateResult.unsupportedUpdate(createInvalidationReason(extension, attribute));
            if ("secondaryVoltageControl".equals(extension.getName())) {
                SecondaryVoltageControl svc = (SecondaryVoltageControl) extension;
                result = onSecondaryVoltageControlExtensionUpdate(svc, attribute, newValue);
            }

            processUpdateResult((Identifiable<?>) extension.getExtendable(), attribute, result);
        }

        private CacheUpdateResult<V> onSecondaryVoltageControlExtensionUpdate(SecondaryVoltageControl svc, String attribute, Object newValue) {
            if ("pilotPointTargetV".equals(attribute)) {
                PilotPoint.TargetVoltageEvent event = (PilotPoint.TargetVoltageEvent) newValue;
                ControlZone controlZone = svc.getControlZone(event.controlZoneName()).orElseThrow();
                for (V value : values) {
                    LfNetwork lfNetwork = value.getNetwork();
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        lfSvc.setTargetValue(event.value() / lfSvc.getPilotBus().getNominalV());
                        return CacheUpdateResult.elementUpdated(value);
                    }
                }
                return CacheUpdateResult.elementNotFound();
            } else if ("controlUnitParticipate".equals(attribute)) {
                ControlUnit.ParticipateEvent event = (ControlUnit.ParticipateEvent) newValue;
                ControlZone controlZone = svc.getControlZone(event.controlZoneName()).orElseThrow();
                for (V value : values) {
                    LfNetwork lfNetwork = value.getNetwork();
                    var lfSvc = lfNetwork.getSecondaryVoltageControl(controlZone.getName()).orElse(null);
                    if (lfSvc != null) {
                        if (event.value()) {
                            lfSvc.addParticipatingControlUnit(event.controlUnitId());
                        } else {
                            lfSvc.removeParticipatingControlUnit(event.controlUnitId());
                        }
                        return CacheUpdateResult.elementUpdated(value);
                    }
                }
                return CacheUpdateResult.elementNotFound();
            }
            return CacheUpdateResult.unsupportedUpdate(createInvalidationReason(svc, attribute));
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

        private void onVariantChange(String variantId) {
            if (variantId.equals(workingVariantId)) {
                reset("variant");
            }
        }

        @Override
        public void onVariantCreated(String sourceVariantId, String targetVariantId) {
            onVariantChange(targetVariantId);
        }

        @Override
        public void onVariantOverwritten(String sourceVariantId, String targetVariantId) {
            onVariantChange(targetVariantId);
        }

        @Override
        public void onVariantRemoved(String variantId) {
            onVariantChange(variantId);
        }

        @Override
        public void close() {
            reset("close");
            Network network = networkRef.get();
            if (network != null && variantCleaner != null) {
                variantCleaner.clean();
            }
        }
    }

    private final BiFunction<Network, I, Entry<I, V>> entryFactory;

    private final List<Entry<I, V>> entries = new ArrayList<>();

    private final Lock lock = new ReentrantLock();

    public NetworkCache(BiFunction<Network, I, Entry<I, V>> entryFactory) {
        this.entryFactory = Objects.requireNonNull(entryFactory);
    }

    private void evictDeadEntries() {
        Iterator<Entry<I, V>> it = entries.iterator();
        while (it.hasNext()) {
            Entry<I, V> entry = it.next();
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

    public Optional<Entry<I, V>> findEntry(Network network) {
        String variantId = network.getVariantManager().getWorkingVariantId();
        return entries.stream()
                .filter(e -> e.getNetworkRef().get() == network && e.getWorkingVariantId().equals(variantId))
                .findFirst();
    }

    public Entry<I, V> get(Network network, Input<I> input) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(input);

        Entry<I, V> entry;
        lock.lock();
        try {
            evictDeadEntries();

            entry = findEntry(network).orElse(null);

            // invalid cache if input has changed
            if (entry != null) {
                String reason = input.hasChanged(entry.getInput());
                if (reason != null) {
                    // release all resources
                    entry.close();
                    entries.remove(entry);
                    entry = null;
                    LOGGER.info("Network cache evicted for network '{}' and variant '{}' because of input change (reason={})",
                            network.getId(), network.getVariantManager().getWorkingVariantId(), reason);
                }
            }

            if (entry == null) {
                entry = entryFactory.apply(network, input.copy());
                entries.add(entry);

                LOGGER.info("Network cache created for network '{}' and variant '{}'",
                        network.getId(), network.getVariantManager().getWorkingVariantId());

                return entry;
            }
        } finally {
            lock.unlock();
        }

        // restart from previous state
        if (entry.getValues() != null) {
            LOGGER.info("Network cache reused for network '{}' and variant '{}'",
                    network.getId(), network.getVariantManager().getWorkingVariantId());

            entry.restart();
        } else {
            LOGGER.info("Network cache cannot be reused for network '{}' and variant '{}' because invalided (reasons={})",
                    network.getId(), network.getVariantManager().getWorkingVariantId(), entry.getInvalidationReasons());
            entry.clearInvalidationReasons();
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
