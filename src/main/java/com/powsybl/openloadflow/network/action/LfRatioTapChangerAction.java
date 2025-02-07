/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.RatioTapChangerTapPositionAction;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public class LfRatioTapChangerAction extends AbstractLfTapChangerAction<RatioTapChangerTapPositionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfRatioTapChangerAction.class);

    public LfRatioTapChangerAction(String id, RatioTapChangerTapPositionAction action, LfNetwork network) {
        super(id, action, network);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (this.branch != null) {
            if (branch.getVoltageControl().isPresent()) {
                LOGGER.warn("Ratio tap changer tap position action: voltage control is present on the tap changer, tap position could be overriden.");
            }
            branch.getPiModel().setTapPosition(this.change.getNewTapPosition());
            return true;
        }
        return false;
    }
}
