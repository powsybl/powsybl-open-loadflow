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
import java.util.stream.Collectors;

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

    public static void apply(List<LfAction> actions, LfNetwork network) {
        Objects.requireNonNull(actions);
        Objects.requireNonNull(network);

        var connectivity = network.getConnectivity();
        connectivity.startTemporaryChanges();

        for (LfAction action : actions) {
            action.apply(connectivity);
        }

        Set<LfBus> buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
        for (LfBus bus : buses) {
            bus.setDisabled(true);
            bus.getBranches().forEach(branch -> branch.setDisabled(true));
        }
        connectivity.undoTemporaryChanges();

        network.getBuses().forEach(bus -> LOGGER.info("LfBus {} is disabled: {}", bus.getId(), bus.isDisabled()));
        network.getBranches().forEach(branch -> LOGGER.info("LfBranch {} is disabled: {}", branch.getId(), branch.isDisabled()));
    }

    public void apply(GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (disabledBranch != null) {
            disabledBranch.setDisabled(true);
            if (disabledBranch.getBus1() != null && disabledBranch.getBus2() != null) {
                connectivity.removeEdge(disabledBranch);
            }
        }
        if (enabledBranch != null) {
            enabledBranch.setDisabled(false);
            enabledBranch.getBus1().setDisabled(false);
            enabledBranch.getBus2().setDisabled(false);
            connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        }
    }
}
