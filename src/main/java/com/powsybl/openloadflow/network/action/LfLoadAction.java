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
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfLoadImpl;
import com.powsybl.openloadflow.util.PerUnit;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfLoadAction extends AbstractLfAction<LoadAction> {

    private final String loadId;
    private final LfLoad lfLoad;
    private final PowerShift powerShift;

    public LfLoadAction(String id, LoadAction action, Network network, LfNetwork lfNetwork) {
        super(id, action);
        this.loadId = action.getLoadId();
        Load load = network.getLoad(action.getLoadId());
        lfLoad = lfNetwork.getLoadById(action.getLoadId());
        powerShift = createPowerShift(load, action);
    }

    private static PowerShift createPowerShift(Load load, LoadAction loadAction) {
        double activePowerShift = loadAction.getActivePowerValue().stream().map(a -> loadAction.isRelativeValue() ? a : a - load.getP0()).findAny().orElse(0);
        double reactivePowerShift = loadAction.getReactivePowerValue().stream().map(r -> loadAction.isRelativeValue() ? r : r - load.getQ0()).findAny().orElse(0);

        // In case of a power shift, we suppose that the shift on a load P0 is exactly the same on the variable active power
        // of P0 that could be described in a LoadDetail extension.
        // Note that fictitious loads have a zero variable active power shift.
        double variableActivePower = LfLoadImpl.isLoadNotParticipating(load) ? 0.0 : activePowerShift;
        return new PowerShift(activePowerShift / PerUnit.SB,
                variableActivePower / PerUnit.SB,
                reactivePowerShift / PerUnit.SB);
    }

    @Override
    public boolean isValid() {
        return lfLoad != null;
    }

    @Override
    public boolean apply(LfNetwork lfNetwork, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (isValid() && !lfLoad.isOriginalLoadDisabled(loadId)) {
            lfLoad.setTargetP(lfLoad.getTargetP() + powerShift.getActive());
            lfLoad.setTargetQ(lfLoad.getTargetQ() + powerShift.getReactive());
            lfLoad.setAbsVariableTargetP(lfLoad.getAbsVariableTargetP() + Math.signum(powerShift.getActive()) * Math.abs(powerShift.getVariableActive()));
            return true;
        }
        return false;
    }
}
