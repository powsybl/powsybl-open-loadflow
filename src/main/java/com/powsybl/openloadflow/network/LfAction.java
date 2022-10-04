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
import com.powsybl.security.action.SwitchAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfAction.class);

    private final String id;

    private LfBranch disabledBranch; // switch to open

    private LfBranch enabledBranch; // switch to close

    public LfAction(Action action, LfNetwork network) {
        this.id = Objects.requireNonNull(action.getId());
        Objects.requireNonNull(network);
        switch (action.getType()) {
            case SwitchAction.NAME:
                SwitchAction switchAction = (SwitchAction) action;
                LfBranch branch = network.getBranchById(switchAction.getSwitchId());
                if (branch == null) {
                    throw new PowsyblException("Branch " + switchAction.getSwitchId() + " not found in the network");
                }
                if (switchAction.isOpen()) {
                    disabledBranch = branch;
                } else {
                    enabledBranch = branch;
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

        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
        connectivity.setMainComponentVertex(network.getSlackBus());
        connectivity.startTemporaryChanges();
        contingency.getDisabledBranches().stream()
                .filter(Objects::nonNull) // could be in another component
                .filter(b -> b.getBus1() != null && b.getBus2() != null)
                .forEach(connectivity::removeEdge);
        Set<LfBus> postContingencyRemovedBuses = connectivity.getVerticesRemovedFromMainComponent();
        postContingencyRemovedBuses.forEach(bus -> bus.setDisabled(false)); // undo.
        Set<LfBranch> postContingencyRemovedBranches = connectivity.getEdgesRemovedFromMainComponent();
        postContingencyRemovedBranches.forEach(branch -> branch.setDisabled(false)); // undo.

        for (LfAction action : actions) {
            action.updateConnectivity(connectivity);
        }

        // add to action description buses and branches that won't be part of the main connected
        // component in post action state.
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = connectivity.getEdgesRemovedFromMainComponent();
        removedBranches.forEach(branch -> branch.setDisabled(true));
        // add to action description buses and branches that will be part of the main connected
        // component in post action state.
        Set<LfBus> addedBuses = connectivity.getVerticesAddedToMainComponent();
        addedBuses.forEach(bus -> bus.setDisabled(false));
        Set<LfBranch> addedBranches = connectivity.getEdgesAddedToMainComponent();
        addedBranches.forEach(branch -> branch.setDisabled(false));

        // reset connectivity to discard triggered branches
        connectivity.undoTemporaryChanges();

        network.getBuses().forEach(bus -> LOGGER.info("LfBus {} is disabled: {}", bus.getId(), bus.isDisabled()));
        network.getBranches().forEach(branch -> LOGGER.info("LfBranch {} is disabled: {}", branch.getId(), branch.isDisabled()));
    }

    public void updateConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (disabledBranch != null && disabledBranch.getBus1() != null && disabledBranch.getBus2() != null) {
            connectivity.removeEdge(disabledBranch);
        }
        if (enabledBranch != null) {
            connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        }
    }
}
