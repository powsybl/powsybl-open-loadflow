/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
abstract class AbstractComputedBranchElement {
    private int computedElementIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
    private double alphaForWoodburyComputation = Double.NaN;
    private final LfBranch lfBranch;
    private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

    protected AbstractComputedBranchElement(LfBranch lfBranch, ClosedBranchSide1DcFlowEquationTerm branchEquation) {
        this.lfBranch = Objects.requireNonNull(lfBranch);
        this.branchEquation = Objects.requireNonNull(branchEquation);
    }

    public LfBranch getLfElement() {
        return lfBranch;
    }

    public LfBus getBus1() {
        return lfBranch.getBus1();
    }

    public LfBus getBus2() {
        return lfBranch.getBus2();
    }

    public int getPh1VarRow() {
        return branchEquation.getPh1Var().getRow();
    }

    public int getPh2VarRow() {
        return branchEquation.getPh2Var().getRow();
    }

    public int getComputedElementIndex() {
        return computedElementIndex;
    }

    public void setComputedElementIndex(final int index) {
        this.computedElementIndex = index;
    }

    public int getLocalIndex() {
        return localIndex;
    }

    public void setLocalIndex(final int index) {
        this.localIndex = index;
    }

    public double getAlphaForWoodburyComputation() {
        return alphaForWoodburyComputation;
    }

    public void setAlphaForWoodburyComputation(double alphaForPostContingencyStates) {
        this.alphaForWoodburyComputation = alphaForPostContingencyStates;
    }

    public LfBranch getLfBranch() {
        return lfBranch;
    }

    public EquationTerm<DcVariableType, DcEquationType> getEquation() {
        return branchEquation;
    }
}
