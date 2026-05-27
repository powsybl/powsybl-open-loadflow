/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.AbstractTapChangerTapPositionAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfLegBranch;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public abstract class AbstractLfTapChangerAction<A extends AbstractTapChangerTapPositionAction> extends AbstractLfAction<A> {

    protected TapPositionChange change;

    protected LfBranch branch;

    AbstractLfTapChangerAction(A action, LfNetwork network) {
        super(action);
        String branchId = action.getSide().map(side -> LfLegBranch.getId(side, action.getTransformerId())).orElseGet(action::getTransformerId);
        branch = network.getBranchById(branchId);
        if (branch != null) {
            if (branch.getPiModel() instanceof SimplePiModel) {
                throw new UnsupportedOperationException("Tap position action: only one tap in branch " + branch.getId());
            }
            change = new TapPositionChange(branch, action.getTapPosition(), action.isRelativeValue());
        }
    }

    @Override
    public boolean isValid() {
        return branch != null;
    }

    public TapPositionChange getChange() {
        return change;
    }
}
