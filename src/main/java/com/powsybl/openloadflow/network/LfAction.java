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
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.impl.AbstractLfGenerator;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.action.*;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
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

        private final PowerShift powerShift;

        private LoadShift(LfBus bus, String loadId, PowerShift powerShift) {
            this.bus = bus;
            this.loadId = loadId;
            this.powerShift = powerShift;
        }
    }

    public static final class GeneratorChange {

        private final LfGenerator generator;

        private final double deltaTargetP;

        private GeneratorChange(LfGenerator generator, double deltaTargetP) {
            this.generator = generator;
            this.deltaTargetP = deltaTargetP;
        }

        public LfGenerator getGenerator() {
            return generator;
        }

        public double getDeltaTargetP() {
            return deltaTargetP;
        }
    }

    private final String id;

    private final LfBranch disabledBranch; // switch to open

    private final LfBranch enabledBranch; // switch to close

    private final TapPositionChange tapPositionChange;

    private final LoadShift loadShift;

    private final GeneratorChange generatorChange;

    private LfAction(String id, LfBranch disabledBranch, LfBranch enabledBranch, TapPositionChange tapPositionChange,
                     LoadShift loadShift, GeneratorChange generatorChange) {
        this.id = Objects.requireNonNull(id);
        this.disabledBranch = disabledBranch;
        this.enabledBranch = enabledBranch;
        this.tapPositionChange = tapPositionChange;
        this.loadShift = loadShift;
        this.generatorChange = generatorChange;
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

            case HvdcAction.NAME:
                return create((HvdcAction) action, lfNetwork);

            default:
                throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
        }
    }

    private static Optional<LfAction> create(HvdcAction action, LfNetwork lfNetwork) {
        // as a first approach, we only support an action that switches an hvdc operated in AC emulation into an active power
        // set point operation mode.
        LfHvdc lfHvdc = lfNetwork.getHvdcById(action.getHvdcId());
        if (lfHvdc != null) {
            action.isAcEmulationEnabled().ifPresent(isEnabled -> {
                if (isEnabled) { // the operation mode remains AC emulation.
                    throw new UnsupportedOperationException("Hvdc action: line is already in AC emulation, not supported yet.");
                } else { // the operation mode changes from AC emulation to fixed active power set point.
                    lfHvdc.setDisabled(true);
                    lfHvdc.getConverterStation1().setTargetP(-lfHvdc.getP1().eval());
                    lfHvdc.getConverterStation2().setTargetP(-lfHvdc.getP2().eval());
                }
            });
        }
        return Optional.empty(); // could be in another component or not operated in AC emulation
    }

    private static Optional<LfAction> create(LoadAction action, LfNetwork lfNetwork, Network network, boolean breakers) {
        Load load = network.getLoad(action.getLoadId());
        Terminal terminal = load.getTerminal();
        Bus bus = Networks.getBus(terminal, breakers);
        if (bus != null) {
            LfBus lfBus = lfNetwork.getBusById(bus.getId());
            double activePowerShift = 0;
            double reactivePowerShift = 0;
            OptionalDouble activePowerValue = action.getActivePowerValue();
            OptionalDouble reactivePowerValue = action.getReactivePowerValue();
            if (activePowerValue.isPresent()) {
                activePowerShift = action.isRelativeValue() ? activePowerValue.getAsDouble() : activePowerValue.getAsDouble() - load.getP0();
            }
            if (reactivePowerValue.isPresent()) {
                reactivePowerShift = action.isRelativeValue() ? reactivePowerValue.getAsDouble() : reactivePowerValue.getAsDouble() - load.getQ0();
            }
            // In case of a power shift, we suppose that the shift on a load P0 is exactly the same on the variable active power
            // of P0 that could be described in a LoadDetail extension.
            PowerShift powerShift = new PowerShift(activePowerShift / PerUnit.SB, activePowerShift / PerUnit.SB, reactivePowerShift / PerUnit.SB);
            return Optional.of(new LfAction(action.getId(), null, null, null, new LoadShift(lfBus, load.getId(), powerShift), null));
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

    private static Optional<LfAction> create(GeneratorAction action, LfNetwork lfNetwork) {
        LfGenerator generator = lfNetwork.getGeneratorById(action.getGeneratorId());
        if (generator != null) {
            OptionalDouble activePowerValue = action.getActivePowerValue();
            Optional<Boolean> relativeValue = action.isActivePowerRelativeValue();
            if (relativeValue.isPresent() && activePowerValue.isPresent()) {
                double deltaTargetP;
                if (relativeValue.get().equals(Boolean.TRUE)) {
                    deltaTargetP = activePowerValue.getAsDouble() / PerUnit.SB;
                } else {
                    deltaTargetP = activePowerValue.getAsDouble() / PerUnit.SB - generator.getInitialTargetP();
                }
                var generatorChange = new GeneratorChange(generator, deltaTargetP);
                return Optional.of(new LfAction(action.getId(), null, null, null, null, generatorChange));
            } else {
                throw new UnsupportedOperationException("Generator action: configuration not supported yet.");
            }
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

    public static void apply(List<LfAction> actions, LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        // first process connectivity part of actions
        updateConnectivity(actions, network, contingency);

        // then process remaining changes of actions
        for (LfAction action : actions) {
            action.apply(networkParameters);
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

    public void apply(LfNetworkParameters networkParameters) {
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
                PowerShift shift = loadShift.powerShift;
                bus.setLoadTargetP(bus.getLoadTargetP() + shift.getActive());
                bus.setLoadTargetQ(bus.getLoadTargetQ() + shift.getReactive());
                bus.getAggregatedLoads().setAbsVariableLoadTargetP(bus.getAggregatedLoads().getAbsVariableLoadTargetP()
                        + Math.signum(shift.getActive()) * Math.abs(shift.getVariableActive()) * PerUnit.SB);
            }
        }

        if (generatorChange != null) {
            LfGenerator generator = generatorChange.getGenerator();
            if (!generator.isDisabled()) {
                generator.setTargetP(generator.getTargetP() + generatorChange.getDeltaTargetP());
                if (!AbstractLfGenerator.checkActivePowerControl(generator.getId(), generator.getTargetP(), generator.getMinP(), generator.getMaxP(),
                        networkParameters.getPlausibleActivePowerLimit(), null)) {
                    generator.setParticipating(false);
                }
            }
        }
    }
}
