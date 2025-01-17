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
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfHvdcAction extends AbstractLfAction<HvdcAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfHvdcAction.class);

    public LfHvdcAction(String id, HvdcAction action) {
        super(id, action);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        LfHvdc lfHvdc = network.getHvdcById(action.getHvdcId());
        Optional<Boolean> acEmulationEnabled = action.isAcEmulationEnabled();
        if (lfHvdc != null && acEmulationEnabled.isPresent()) {
            if (acEmulationEnabled.get().equals(Boolean.TRUE)) { // the operation mode remains AC emulation.
                throw new UnsupportedOperationException("Hvdc action: line is already in AC emulation, not supported yet.");
            } else { // the operation mode changes from AC emulation to fixed active power set point.
                lfHvdc.setAcEmulation(false);
                lfHvdc.setDisabled(true); // for equations only, but should be hidden
                lfHvdc.getConverterStation1().setTargetP(-lfHvdc.getP1().eval()); // override
                lfHvdc.getConverterStation2().setTargetP(-lfHvdc.getP2().eval()); // override
                return true;
            }
        } else if (lfHvdc != null) {
            LOGGER.warn("Hvdc action {}: hvdc is already in active power setpoint mode, action not supported.", action.getId());
        } else {
            LOGGER.warn("Hvdc action {}: hvdc line not found", action.getId());
        }
        return false;
    }
}
