/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.validation.BalanceType;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.action.*;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public final class LfAction {

    private static final class TapPositionChange {

        private final LfBranch branch;

        private final int value;

        private final boolean relative;

        private TapPositionChange(LfBranch branch, int value, boolean relative) {
            this.branch = Objects.requireNonNull(branch);
            this.value = value;
            this.relative = relative;
        }

        public LfBranch getBranch() {
            return branch;
        }

        public int getValue() {
            return value;
        }

        public boolean isRelative() {
            return relative;
        }
    }

    private static final class LoadShift {

        private final LfBus bus;

        private final String loadId;

        private final double p0;

        private final PowerShift powerShift;

        private LoadShift(LfBus bus, String loadId, double p0, PowerShift powerShift) {
            this.bus = bus;
            this.loadId = loadId;
            this.p0 = p0;
            this.powerShift = powerShift;
        }
    }

    public static final class GeneratorUpdates {

        // Reference to the generator
        private final LfGenerator generator;

        private final Optional<Double> activePowerValue;

        private final Optional<Boolean> voltageRegulation;

        private final Optional<Double> targetV;

        private final Optional<Double> targetQ;

        private GeneratorUpdates(LfGenerator generator, Optional<Double> activePowerValue,
                                 Optional<Boolean> voltageRegulation, Optional<Double> targetV, Optional<Double> targetQ) {
            this.generator = generator;
            this.activePowerValue = activePowerValue;
            this.voltageRegulation = voltageRegulation;
            this.targetV = targetV;
            this.targetQ = targetQ;
        }

        public LfGenerator getGenerator() {
            return generator;
        }

        public Optional<Double> getActivePowerValue() {
            return activePowerValue;
        }

        public Optional<Boolean> isVoltageRegulation() {
            return voltageRegulation;
        }

        public Optional<Double> getTargetV() {
            return targetV;
        }

        public Optional<Double> getTargetQ() {
            return targetQ;
        }

    }

    private final String id;

    private final LfBranch disabledBranch; // switch to open

    private final LfBranch enabledBranch; // switch to close

    private final TapPositionChange tapPositionChange;

    private final LoadShift loadShift;

    private final GeneratorUpdates generatorUpdates;

    private LfAction(String id, LfBranch disabledBranch, LfBranch enabledBranch, TapPositionChange tapPositionChange,
                     LoadShift loadShift, GeneratorUpdates generatorUpdates) {
        this.id = Objects.requireNonNull(id);
        this.disabledBranch = disabledBranch;
        this.enabledBranch = enabledBranch;
        this.tapPositionChange = tapPositionChange;
        this.loadShift = loadShift;
        this.generatorUpdates = generatorUpdates;
    }

    public static Optional<LfAction> create(Action action, LfNetwork lfNetwork, Network network, boolean breakers) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(network);
        switch (action.getType()) {
            case SwitchAction.NAME:
                return create((SwitchAction) action, lfNetwork);

            case LineConnectionAction.NAME:
                return create((LineConnectionAction) action, lfNetwork);

            case PhaseTapChangerTapPositionAction.NAME:
                return create((PhaseTapChangerTapPositionAction) action, lfNetwork);

            case LoadAction.NAME:
                return create((LoadAction) action, lfNetwork, network, breakers);

            case GeneratorAction.NAME:
                return create((GeneratorAction) action, lfNetwork);

            default:
                throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
        }
    }

    private static Optional<LfAction> create(LoadAction action, LfNetwork lfNetwork, Network network, boolean breakers) {
        Load load = network.getLoad(action.getLoadId());
        Terminal terminal = load.getTerminal();
        Bus bus = Networks.getBus(terminal, breakers);
        if (bus != null) {
            LfBus lfBus = lfNetwork.getBusById(bus.getId());
            double activePowerShift = 0;
            double reactivePowerShift = 0;
            Optional<Double> activePowerValue = action.getActivePowerValue();
            Optional<Double> reactivePowerValue = action.getReactivePowerValue();
            if (activePowerValue.isPresent()) {
                activePowerShift = action.isRelativeValue() ? activePowerValue.get() : activePowerValue.get() - load.getP0();
            }
            if (reactivePowerValue.isPresent()) {
                reactivePowerShift = action.isRelativeValue() ? reactivePowerValue.get() : reactivePowerValue.get() - load.getQ0();
            }
            // In case of a power shift, we suppose that the shift on a load P0 is exactly the same on the variable active power
            // of P0 that could be described in a LoadDetail extension.
            PowerShift powerShift = new PowerShift(activePowerShift / PerUnit.SB, activePowerShift / PerUnit.SB, reactivePowerShift / PerUnit.SB);
            return Optional.of(new LfAction(action.getId(), null, null, null,
                    new LoadShift(lfBus, load.getId(), load.getP0(), powerShift), null));
        }
        return Optional.empty(); // could be in another component or in contingency.
    }

    private static Optional<LfAction> create(PhaseTapChangerTapPositionAction action, LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getTransformerId()); // only two windings transformer for the moment.
        if (branch != null) {
            if (branch.getPiModel() instanceof SimplePiModel) {
                throw new UnsupportedOperationException("Phase tap changer tap connection action: only one tap in the branch {" + action.getTransformerId() + "}");
            } else {
                var tapPositionChange = new TapPositionChange(branch, action.getTapPosition(), action.isRelativeValue());
                return Optional.of(new LfAction(action.getId(), null, null, tapPositionChange, null, null));
            }
        }
        return Optional.empty(); // could be in another component
    }

    private static Optional<LfAction> create(LineConnectionAction action, LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getLineId());
        if (branch != null) {
            if (action.isOpenSide1() && action.isOpenSide2()) {
                return Optional.of(new LfAction(action.getId(), branch, null, null, null, null));
            } else {
                throw new UnsupportedOperationException("Line connection action: only open line at both sides is supported yet.");
            }
        }
        return Optional.empty(); // could be in another component
    }

    private static Optional<LfAction> create(SwitchAction action, LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getSwitchId());
        if (branch != null) {
            LfBranch disabledBranch = null;
            LfBranch enabledBranch = null;
            if (action.isOpen()) {
                disabledBranch = branch;
            } else {
                enabledBranch = branch;
            }
            return Optional.of(new LfAction(action.getId(), disabledBranch, enabledBranch, null, null, null));
        }
        return Optional.empty(); // could be in another component
    }

    private static Optional<LfAction> create(GeneratorAction action, LfNetwork network) {
        LfGenerator generator = network.getGeneratorById(action.getGeneratorId());
        if (generator != null) {
            Optional<Double> newTargetP = Optional.empty();
            Optional<Double> activePowerValue = action.getActivePowerValue();
            if (activePowerValue.isPresent()) {
                // A GeneratorAction CANNOT be created if only one of the activePowerValue or isActivePowerRelative is empty
                Optional<Boolean> relativeValue = action.isActivePowerRelativeValue();
                if (!(relativeValue.isPresent() && relativeValue.get())) {
                    throw new PowsyblException("LfAction.GeneratorUpdates: setTargetP with an absolute power value is not supported yet.");
                }
                newTargetP = Optional.of(activePowerValue.get() / PerUnit.SB + generator.getTargetP());
            }

            Optional<Double> newTargetQ = action.getTargetQ();
            if (newTargetQ.isPresent()) {
                newTargetQ = Optional.of(newTargetQ.get() / PerUnit.SB);
            }

            Optional<Double> newTargetV = action.getTargetV();
            if (newTargetV.isPresent()) {
                newTargetV = Optional.of(newTargetV.get() / generator.getControlledBus().getNominalV());
            }
            var generatorUpdates = new GeneratorUpdates(generator, newTargetP, action.isVoltageRegulatorOn(), newTargetV, newTargetQ);
            return Optional.of(new LfAction(action.getId(), null, null, null, null, generatorUpdates));
        }
        return Optional.empty();
    }

    public String getId() {
        return id;
    }

    public LfBranch getDisabledBranch() {
        return disabledBranch;
    }

    public LfBranch getEnabledBranch() {
        return enabledBranch;
    }

    public static void apply(List<LfAction> actions, LfNetwork network, LfContingency contingency, LoadFlowParameters.BalanceType balanceType) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        // first process connectivity part of actions
        updateConnectivity(actions, network, contingency);

        // then process remaining changes of actions
        for (LfAction action : actions) {
            action.apply(balanceType);
        }
    }

    private static void updateConnectivity(List<LfAction> actions, LfNetwork network, LfContingency contingency) {
        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();

        // re-update connectivity according to post contingency state (revert after LfContingency apply)
        connectivity.startTemporaryChanges();
        contingency.getDisabledBranches().forEach(connectivity::removeEdge);

        // update connectivity according to post action state
        connectivity.startTemporaryChanges();
        for (LfAction action : actions) {
            action.updateConnectivity(connectivity);
        }

        // add to action description buses and branches that won't be part of the main connected
        // component in post action state.
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        removedBranches.forEach(branch -> branch.setDisabled(true));

        // add to action description buses and branches that will be part of the main connected
        // component in post action state.
        Set<LfBus> addedBuses = connectivity.getVerticesAddedToMainComponent();
        addedBuses.forEach(bus -> bus.setDisabled(false));
        Set<LfBranch> addedBranches = new HashSet<>(connectivity.getEdgesAddedToMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : addedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(addedBranches::add);
        }
        addedBranches.forEach(branch -> branch.setDisabled(false));

        // reset connectivity to discard post contingency connectivity and post action connectivity
        connectivity.undoTemporaryChanges();
        connectivity.undoTemporaryChanges();
    }

    public void updateConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (disabledBranch != null && disabledBranch.getBus1() != null && disabledBranch.getBus2() != null) {
            connectivity.removeEdge(disabledBranch);
        }
        if (enabledBranch != null) {
            connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        }
    }

    public void apply(LoadFlowParameters.BalanceType balanceType) {
        if (tapPositionChange != null) {
            LfBranch branch = tapPositionChange.getBranch();
            int tapPosition = branch.getPiModel().getTapPosition();
            int value = tapPositionChange.getValue();
            int newTapPosition = tapPositionChange.isRelative() ? tapPosition + value : value;
            branch.getPiModel().setTapPosition(newTapPosition);
        }

        if (loadShift != null) {
            LfBus bus = loadShift.bus;
            String loadId = loadShift.loadId;
            if (!bus.getAggregatedLoads().isDisabled(loadId)) {
                Double loadP0 = loadShift.p0;
                PowerShift shift = loadShift.powerShift;
                double newP0 = loadP0 / PerUnit.SB + shift.getActive();
                double oldUpdatedP0 = LfContingency.getUpdatedLoadP0(bus, balanceType, loadP0 / PerUnit.SB, loadP0 / PerUnit.SB);
                double newUpdatedP0 = LfContingency.getUpdatedLoadP0(bus, balanceType, newP0, newP0);
                bus.setLoadTargetP(bus.getLoadTargetP() + newUpdatedP0 - oldUpdatedP0);
                bus.setLoadTargetQ(bus.getLoadTargetQ() + shift.getReactive());
                bus.getAggregatedLoads().setAbsVariableLoadTargetP(bus.getAggregatedLoads().getAbsVariableLoadTargetP()
                        + Math.signum(shift.getActive()) * Math.abs(shift.getVariableActive()) * PerUnit.SB);
            }
        }

        if (generatorUpdates != null) {
            generatorUpdates.getActivePowerValue().ifPresent(activePowerValue -> {
                generatorUpdates.getGenerator().setTargetP(activePowerValue);
            });

            generatorUpdates.isVoltageRegulation().ifPresent(voltageRegulationOn ->
                            generatorUpdates.getGenerator().getControlledBus().setVoltageControlEnabled(voltageRegulationOn));

            generatorUpdates.getTargetV().ifPresent(value -> {
                throw new PowsyblException("GeneratorUpdates: setTargetV not implemented yet.");
            });
                    /* value ->
                generatorUpdates.getGenerator().getControlledBus().getVoltageControl().ifPresentOrElse(
                    voltageControl -> voltageControl.setTargetValue(value),
                    () -> {
                        throw new PowsyblException("GeneratorAction: No controlled bus for generator " + generatorUpdates.getGenerator().getId());
                    })
            ); */
            generatorUpdates.getTargetQ().ifPresent(value -> {
                throw new PowsyblException("GeneratorUpdates: setTargetQ not implemented yet.");
            });

                    /* value -> generatorUpdates.getGenerator().getBus().setGenerationTargetQ(value)); */
        }
    }
}
