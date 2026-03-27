/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.ShuntCompensatorPositionAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfShuntImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfShuntCompensatorPositionAction extends AbstractLfAction<ShuntCompensatorPositionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfShuntCompensatorPositionAction.class);

    private final LfShunt shunt;

    public LfShuntCompensatorPositionAction(ShuntCompensatorPositionAction action, LfNetwork network) {
        super(action);
        LfShunt aShunt = network.getShuntById(action.getShuntCompensatorId());
        this.shunt = aShunt instanceof LfShuntImpl ? aShunt : null; // no svc here
    }

    @Override
    public boolean isValid() {
        return shunt != null;
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (isValid()) {
            if (shunt.getVoltageControl().isPresent()) {
                LOGGER.warn("Shunt compensator position action: voltage control is present on the shunt, section could be overridden.");
            }
            Optional<LfShunt.Controller> controllerOpt = shunt.getControllers().stream()
                .filter(controller -> controller.getId().equals(action.getShuntCompensatorId())).findAny();
            if (controllerOpt.isPresent()) {
                controllerOpt.get().updateSectionB(action.getSectionCount());
                return true;
            } else {
                LOGGER.warn("No section change: shunt {} not present", action.getShuntCompensatorId());
                return false;
            }
        }
        return false;
    }
}
