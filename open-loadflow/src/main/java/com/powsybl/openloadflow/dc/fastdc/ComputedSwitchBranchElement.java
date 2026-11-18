/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
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

import java.util.Objects;

/**
 * @author Pierre Arvy {@literal <pierre.arvy@artelys.com>}
 */
public final class ComputedSwitchBranchElement extends AbstractComputedElement implements ComputedElement {

    private final boolean enabled; // indicates whether the action opens or closes the branch

    public static ComputedSwitchBranchElement create(LfBranch lfBranch, boolean enabled, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        Objects.requireNonNull(lfBranch);
        Objects.requireNonNull(equationSystem);
        ClosedBranchSide1DcFlowEquationTerm branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
        return new ComputedSwitchBranchElement(lfBranch, enabled, branchEquation);
    }

    private ComputedSwitchBranchElement(LfBranch lfBranch, boolean enabled, ClosedBranchSide1DcFlowEquationTerm branchEquation) {
        super(lfBranch, branchEquation);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void applyToConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        LfBranch lfBranch = getLfBranch();
        if (lfBranch.getBus1() != null && lfBranch.getBus2() != null) {
            if (enabled) {
                connectivity.addEdge(lfBranch.getBus1(), lfBranch.getBus2(), lfBranch);
            } else {
                connectivity.removeEdge(lfBranch);
            }
        }
    }
}
