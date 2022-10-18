/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.LineConnectionAction;
import com.powsybl.security.action.PhaseTapChangerTapPositionAction;
import com.powsybl.security.action.SwitchAction;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfAction {

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

    private final String id;

    private LfBranch disabledBranch; // switch to open

    private LfBranch enabledBranch; // switch to close

    private TapPositionChange tapPositionChange;

    public LfAction(Action action, LfNetwork network) {
        this.id = Objects.requireNonNull(action.getId());
        Objects.requireNonNull(network);
        LfBranch branch;
        switch (action.getType()) {
            case SwitchAction.NAME:
                SwitchAction switchAction = (SwitchAction) action;
                branch = network.getBranchById(switchAction.getSwitchId());
                checkBranch(branch, switchAction.getSwitchId());
                if (switchAction.isOpen()) {
                    disabledBranch = branch;
                } else {
                    enabledBranch = branch;
                }
                break;
            case LineConnectionAction.NAME:
                LineConnectionAction lineConnectionAction = (LineConnectionAction) action;
                branch = network.getBranchById(lineConnectionAction.getLineId());
                checkBranch(branch, lineConnectionAction.getLineId());
                if (lineConnectionAction.isOpenSide1() && lineConnectionAction.isOpenSide2()) {
                    disabledBranch = branch;
                } else {
                    throw new UnsupportedOperationException("Line connection action: only open line at both sides is supported yet.");
                }
                break;
            case PhaseTapChangerTapPositionAction.NAME:
                PhaseTapChangerTapPositionAction phaseTapChangerTapPositionAction = (PhaseTapChangerTapPositionAction) action;
                branch = network.getBranchById(phaseTapChangerTapPositionAction.getTransformerId()); // only two windings transformer for the moment.
                checkBranch(branch, phaseTapChangerTapPositionAction.getTransformerId()); // how to check that it is really a phase tap changer?
                if (branch.getPiModel() instanceof SimplePiModel) {
                    throw new UnsupportedOperationException("Phase tap changer tap connection action: only one tap in the branch {" + phaseTapChangerTapPositionAction.getTransformerId() + "}");
                } else {
                    tapPositionChange = new TapPositionChange(branch, phaseTapChangerTapPositionAction.getValue(), phaseTapChangerTapPositionAction.isRelativeValue());
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported action type: " + action.getType());
        }
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

    public static void apply(List<LfAction> actions, LfNetwork network, LfContingency contingency) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        // first process connectivity part of actions
        updateConnectivity(actions, network, contingency);

        // then process remaining changes of actions
        for (LfAction action : actions) {
            action.apply();
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

    public void apply() {
        if (tapPositionChange != null) {
            LfBranch branch = tapPositionChange.getBranch();
            int tapPosition = branch.getPiModel().getTapPosition();
            int value = tapPositionChange.getValue();
            int newTapPosition = tapPositionChange.isRelative() ? tapPosition + value : value;
            branch.getPiModel().setTapPosition(newTapPosition);
        }
    }

    private static void checkBranch(LfBranch branch, String branchId) {
        if (branch == null) {
            throw new PowsyblException("Branch " + branchId + " not found in the network");
        }
    }
}
