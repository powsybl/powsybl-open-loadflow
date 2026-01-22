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

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public class LfAreaInterchangeTargetAction extends AbstractLfAction<AreaInterchangeTargetAction> {

    private final LfArea area;

    public LfAreaInterchangeTargetAction(String id, AreaInterchangeTargetAction action, LfNetwork network) {
        super(id, action);
        this.area = network.getAreaById(action.getAreaId());
    }

    @Override
    public boolean isValid() {
        return area != null;
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (isValid()) {
            area.setInterchangeTarget(action.getInterchangeTarget() / PerUnit.SB);
            return true;
        }
        return false;
    }
}
