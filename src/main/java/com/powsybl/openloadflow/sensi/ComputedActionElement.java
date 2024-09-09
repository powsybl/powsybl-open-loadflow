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
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Collection;

public final class ComputedActionElement extends ComputedElement {

    private final LfAction action;
    private final LfBranch lfBranch;
    private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

    public ComputedActionElement(final LfAction action, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        this.action = action;
        lfBranch = action.getTapPositionChange().branch();
        branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
    }

    public LfAction getAction() {
        return action;
    }

    public LfBranch getLfBranch() {
        return lfBranch;
    }

    public ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
        return branchEquation;
    }

    public static void setComputedActionIndexes(Collection<ComputedActionElement> elements) {
        int index = 0;
        for (ComputedElement element : elements) {
            element.setComputedElementIndex(index++);
        }
    }

    public static void setLocalIndexes(Collection<ComputedActionElement> elements) {
        int index = 0;
        for (ComputedActionElement element : elements) {
            element.setLocalIndex(index++);
        }
    }
}
