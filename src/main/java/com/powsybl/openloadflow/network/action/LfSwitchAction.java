/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.SwitchAction;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfSwitchAction extends AbstractLfBranchAction<SwitchAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfSwitchAction.class);

    public LfSwitchAction(SwitchAction action, LfNetwork lfNetwork) {
        super(action, lfNetwork);
    }

    @Override
    void findEnabledDisabledBranches(LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getSwitchId());
        if (branch != null) {
            if (action.isOpen()) {
                setDisabledBranch(branch);
            } else {
                setEnabledBranch(branch);
            }
        }
    }

    @Override
    public boolean checkError(Network network) {
        Switch sw = network.getSwitch(action.getSwitchId());
        boolean error;
        error = false;
        if (action.isOpen() != sw.isOpen()) {
            VoltageLevel vl = sw.getVoltageLevel();
            Bus bus1 = vl.getBusBreakerView().getBus1(sw.getId());
            Bus bus2 = vl.getBusBreakerView().getBus1(sw.getId());
            if (bus1 == bus2) {
                LOGGER.error("Switch '{}' connected at both sides to same bus", action.getId());
            } else {
                LOGGER.trace("Switch '{}' is {} in the network and action is to {}", action.getId(), sw.isOpen() ? "open" : "closed",
                        action.isOpen() ? "open" : "closed");
                error = true;
            }
        }
        return error;
    }
}
