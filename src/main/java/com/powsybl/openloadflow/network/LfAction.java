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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfAction.class);

    private final String id;

    private LfBranch disabledBranch; // switch to open

    private LfBranch enabledBranch; // switch to close

    private LfNetwork network;

    public LfAction(Action action, LfNetwork network) {
        this.id = Objects.requireNonNull(action.getId());
        this.network = Objects.requireNonNull(network);
        switch (action.getType()) {
            case "SWITCH":
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
                throw new UnsupportedOperationException("Unsupported action type: "  + action.getType());
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

    public void apply() {
        apply(false, Collections.emptyList());
    }

    public void apply(boolean withConnectivity, List<String> alreadyRemovedEdges) {
        if (disabledBranch != null) {
            disabledBranch.setDisabled(true);
        }
        if (enabledBranch != null) {
            enabledBranch.setDisabled(false);
            enabledBranch.getBus1().setDisabled(false);
            enabledBranch.getBus2().setDisabled(false);
        }

        if (withConnectivity) {
            // update connectivity with disabled branches
            List<LfBranch> disabledBranches = network.getBranches().stream()
                    .filter(LfElement::isDisabled)
                    .collect(Collectors.toList());
            GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();
            connectivity.startTemporaryChanges();
            disabledBranches.stream()
                    .filter(b -> b.getBus1() != null && b.getBus2() != null)
                    .filter(b -> !alreadyRemovedEdges.contains(b.getId())) // FIXME
                    .forEach(connectivity::removeEdge);
            // update connectivity with enabled branches.
            if (enabledBranch != null) {
                connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
            }

            // add to contingency description buses and branches that won't be part of the main connected
            // component in post contingency state
            Set<LfBus> buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
            for (LfBus bus : buses) {
                bus.setDisabled(true);
                bus.getBranches().forEach(branch -> branch.setDisabled(true));
            }
            connectivity.undoTemporaryChanges();

            LOGGER.info("Network state after action {}", id);
            network.getBuses().stream().forEach(bus -> LOGGER.info("LfBus {} is disabled: {}", bus.getId(), bus.isDisabled()));
            network.getBranches().stream().forEach(branch -> LOGGER.info("LfBranch {} is disabled: {}", branch.getId(), branch.isDisabled()));
        }
    }
}
