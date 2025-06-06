/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.LoadAction;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfLoadAction extends AbstractLfAction<LoadAction> {

    private final Network network;

    private final boolean breakers;

    public LfLoadAction(String id, LoadAction action, Network network, boolean breakers) {
        super(id, action);
        this.network = network;
        this.breakers = breakers;
    }

    @Override
    public boolean apply(LfNetwork lfNetwork, LfContingency contingency, LfNetworkParameters networkParameters) {
        Load load = network.getLoad(action.getLoadId());
        Terminal terminal = load.getTerminal();
        Bus bus = Networks.getBus(terminal, breakers);
        if (bus != null) {
            LfLoad lfLoad = lfNetwork.getLoadById(load.getId());
            if (lfLoad != null) {
                PowerShift powerShift = PowerShift.createPowerShift(load, action);
                if (!lfLoad.isOriginalLoadDisabled(load.getId())) {
                    lfLoad.setTargetP(lfLoad.getTargetP() + powerShift.getActive());
                    lfLoad.setTargetQ(lfLoad.getTargetQ() + powerShift.getReactive());
                    lfLoad.setAbsVariableTargetP(lfLoad.getAbsVariableTargetP() + Math.signum(powerShift.getActive()) * Math.abs(powerShift.getVariableActive()));
                    return true;
                }
            }
        }
        return false;
    }
}
