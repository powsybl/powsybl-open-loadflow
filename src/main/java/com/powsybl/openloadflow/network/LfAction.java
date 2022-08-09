/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.security.action.Action;
import com.powsybl.security.action.SwitchAction;
import com.powsybl.security.action.GenerationRedispatchAction;

import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class LfAction {

    private final String id;

    private LfBranch disabledBranch; // switch to open

    private LfBranch enabledBranch; // switch to close

    private LfGenerator redispatchGenerator; // generator on which new target is applied

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
            case "GENERATION_REDISPATCH":
                GenerationRedispatchAction generationRedispatchAction = (GenerationRedispatchAction) action;
                LfGenerator lfGenerator = network.getGeneratorById(generationRedispatchAction.getGeneratorId());
                if (lfGenerator == null) {
                    throw new PowsyblException("Generator " + generationRedispatchAction.getGeneratorId() + " not found in the network");
                }
                redispatchGenerator = lfGenerator;
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
        var connectivity = network.getConnectivity();
        if (disabledBranch != null) {
            disabledBranch.setDisabled(true);
            if (disabledBranch.getBus1() != null && disabledBranch.getBus2() != null) {
                // connectivity.cut(disabledBranch);
            }
        }
        if (enabledBranch != null) {
            enabledBranch.setDisabled(false);
            if (enabledBranch.getBus1() != null && enabledBranch.getBus2() != null) {
                // connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
            }
        }
        // FIXME: connectivity analysis will show that some buses could be enabled/disabled. Maybe also other branches?
    }
}
