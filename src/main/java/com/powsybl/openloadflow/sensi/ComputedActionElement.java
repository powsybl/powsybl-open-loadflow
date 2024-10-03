/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfAction;

/**
 * @author Pierre Arvy {@literal <pierre.arvy@artelys.com>}
 */
public final class ComputedActionElement extends ComputedElement {

    private final LfAction action;

    public ComputedActionElement(final LfAction action, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(action.getTapPositionChange().branch(),
                equationSystem.getEquationTerm(ElementType.BRANCH, action.getTapPositionChange().branch().getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
        this.action = action;
    }

    public LfAction getAction() {
        return action;
    }
}
