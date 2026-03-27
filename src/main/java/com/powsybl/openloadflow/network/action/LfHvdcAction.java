/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.HvdcAction;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfHvdc;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfHvdcAction extends AbstractLfAction<HvdcAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfHvdcAction.class);

    private final LfHvdc lfHvdc;

    public LfHvdcAction(HvdcAction action, LfNetwork network) {
        super(action);
        // As a first approach, we only support an action that switches an hvdc operated in AC emulation
        // into an active power set point operation mode.
        if (action.isAcEmulationEnabled().orElse(false)) {
            throw new UnsupportedOperationException("Hvdc action: enabling ac emulation mode through an action is not supported yet.");
        }
        lfHvdc = network.getHvdcById(action.getHvdcId());
    }

    @Override
    public boolean isValid() {
        return lfHvdc != null;
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (isValid()) {
            if (action.isAcEmulationEnabled().isEmpty()) {
                LOGGER.warn("Hvdc action {}: only explicitly disabling ac emulation is supported.", action.getId());
                return false;
            }
            // the operation mode changes from AC emulation to fixed active power set point.
            lfHvdc.setAcEmulation(false);
            lfHvdc.setDisabled(true); // for equations only, but should be hidden
            lfHvdc.getConverterStation1().setTargetP(-lfHvdc.getP1().eval()); // override
            lfHvdc.getConverterStation2().setTargetP(-lfHvdc.getP2().eval()); // override
            return true;
        } else {
            LOGGER.warn("Hvdc action {}: hvdc line not found", action.getId());
        }
        return false;
    }
}
