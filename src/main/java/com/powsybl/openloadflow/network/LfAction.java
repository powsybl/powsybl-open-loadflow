/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.LineConnectionAction;
import com.powsybl.security.action.PhaseTapChangerTapPositionAction;
import com.powsybl.security.action.SwitchAction;

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

    private final String id;

    private final LfBranch disabledBranch; // switch to open

    private final LfBranch enabledBranch; // switch to close

    private final TapPositionChange tapPositionChange;

    private LfAction(String id, LfBranch disabledBranch, LfBranch enabledBranch, TapPositionChange tapPositionChange) {
        this.id = Objects.requireNonNull(id);
        this.disabledBranch = disabledBranch;
        this.enabledBranch = enabledBranch;
        this.tapPositionChange = tapPositionChange;
    }

    public static Optional<LfAction> create(Action action, LfNetwork network) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(network);
        switch (action.getType()) {
            case SwitchAction.NAME: {
                SwitchAction switchAction = (SwitchAction) action;
                LfBranch branch = network.getBranchById(switchAction.getSwitchId());
                if (branch != null) {
                    LfBranch disabledBranch = null;
                    LfBranch enabledBranch = null;
                    if (switchAction.isOpen()) {
                        disabledBranch = branch;
                    } else {
                        enabledBranch = branch;
                    }
                    return Optional.of(new LfAction(action.getId(), disabledBranch, enabledBranch, null));
                }
                return Optional.empty();
            }

            case LineConnectionAction.NAME: {
                LineConnectionAction lineConnectionAction = (LineConnectionAction) action;
                LfBranch branch = network.getBranchById(lineConnectionAction.getLineId());
                if (branch != null) {
                    if (lineConnectionAction.isOpenSide1() && lineConnectionAction.isOpenSide2()) {
                        return Optional.of(new LfAction(action.getId(), branch, null, null));
                    } else {
                        throw new UnsupportedOperationException("Line connection action: only open line at both sides is supported yet.");
                    }
                }
                return Optional.empty();
            }

            case PhaseTapChangerTapPositionAction.NAME: {
                PhaseTapChangerTapPositionAction phaseTapChangerTapPositionAction = (PhaseTapChangerTapPositionAction) action;
                LfBranch branch = network.getBranchById(phaseTapChangerTapPositionAction.getTransformerId()); // only two windings transformer for the moment.
                if (branch != null) {
                    if (branch.getPiModel() instanceof SimplePiModel) {
                        throw new UnsupportedOperationException("Phase tap changer tap connection action: only one tap in the branch {" + phaseTapChangerTapPositionAction.getTransformerId() + "}");
                    } else {
                        var tapPositionChange = new TapPositionChange(branch, phaseTapChangerTapPositionAction.getValue(), phaseTapChangerTapPositionAction.isRelativeValue());
                        return Optional.of(new LfAction(action.getId(), null, null, tapPositionChange));
                    }
                }
                return Optional.empty();
            }

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
}
