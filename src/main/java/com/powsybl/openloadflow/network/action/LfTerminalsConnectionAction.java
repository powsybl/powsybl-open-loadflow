/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfTerminalsConnectionAction extends AbstractLfBranchAction<TerminalsConnectionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfTerminalsConnectionAction.class);

    public LfTerminalsConnectionAction(TerminalsConnectionAction action, LfNetwork lfNetwork) {
        super(action, lfNetwork);
        if (action.getSide().isPresent()) {
            throw new UnsupportedOperationException("Terminals connection action: only open or close branch at both sides is supported yet.");
        }
    }

    @Override
    void findEnabledDisabledBranches(LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getElementId());
        if (branch != null && branch.getBus1() != null && branch.getBus2() != null) {
            if (action.isOpen()) {
                setDisabledBranch(branch);
            } else {
                setEnabledBranch(branch);
            }
        }
    }

    @Override
    public boolean checkError(Network network) {
        Branch<?> branch = network.getBranch(action.getElementId());
        boolean branchOpen1 = !branch.getTerminal1().isConnected();
        boolean branchOpen2 = !branch.getTerminal2().isConnected();
        boolean branchOpen = branchOpen1 || branchOpen2;
        boolean error = action.isOpen() != branchOpen;
        if (error) {
            if (branchOpen1 ^ branchOpen2) {
                LOGGER.error("Branch '{}' is open at one side in the network: unsupported in Woodbury", action.getId());
            } else {
                LOGGER.trace("Branch '{}' is {} in the network and action is to {}", action.getId(), branchOpen ? "open" : "closed",
                        action.isOpen() ? "open" : "closed");
            }
        }
        return error;
    }
}
