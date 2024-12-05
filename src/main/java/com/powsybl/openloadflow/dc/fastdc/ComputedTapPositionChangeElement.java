/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.TapPositionChange;

/**
 * @author Pierre Arvy {@literal <pierre.arvy@artelys.com>}
 */
public final class ComputedTapPositionChangeElement extends ComputedElement {

    private final TapPositionChange tapPositionChange;

    public ComputedTapPositionChangeElement(TapPositionChange tapPositionChange, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(tapPositionChange.getBranch(), equationSystem.getEquationTerm(ElementType.BRANCH, tapPositionChange.getBranch().getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
        this.tapPositionChange = tapPositionChange;
    }

    public TapPositionChange getTapPositionChange() {
        return tapPositionChange;
    }
}
