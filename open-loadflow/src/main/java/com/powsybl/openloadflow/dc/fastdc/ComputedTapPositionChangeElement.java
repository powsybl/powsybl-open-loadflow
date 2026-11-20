/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.na.equations.EquationSystem;
import com.powsybl.na.equations.dc.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.na.equations.dc.DcEquationType;
import com.powsybl.na.equations.dc.DcVariableType;
import com.powsybl.na.graph.GraphConnectivity;
import com.powsybl.na.network.ElementType;
import com.powsybl.na.network.LfBranch;
import com.powsybl.na.network.LfBus;
import com.powsybl.na.network.TapPositionChange;

/**
 * @author Pierre Arvy {@literal <pierre.arvy@artelys.com>}
 */
public final class ComputedTapPositionChangeElement extends AbstractComputedElement implements ComputedElement {

    private final TapPositionChange tapPositionChange;

    public ComputedTapPositionChangeElement(TapPositionChange tapPositionChange, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        super(tapPositionChange.getBranch(), equationSystem.getEquationTerm(ElementType.BRANCH, tapPositionChange.getBranch().getNum(), ClosedBranchSide1DcFlowEquationTerm.class));
        this.tapPositionChange = tapPositionChange;
    }

    public TapPositionChange getTapPositionChange() {
        return tapPositionChange;
    }

    @Override
    public void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        // nothing to do
    }
}
