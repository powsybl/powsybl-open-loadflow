/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.AreaInterchangeTargetAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public class LfAreaInterchangeTargetAction extends AbstractLfAction<AreaInterchangeTargetAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfAreaInterchangeTargetAction.class);

    public LfAreaInterchangeTargetAction(String id, AreaInterchangeTargetAction action) {
        super(id, action);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (!networkParameters.isAreaInterchangeControl()) {
            LOGGER.warn("AreaInterchangeTargetAction action {}: area interchange control is disabled", action.getId());
        }
        LfArea area = network.getAreaById(action.getAreaId());
        if (area != null) {
            area.setInterchangeTarget(action.getInterchangeTarget() / PerUnit.SB);
            return true;
        }
        return false;
    }
}
