/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.network.LfBranch;

import java.util.Collection;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class ComputedElement {
    private int computedElementIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
    private double alphaForWoodburyComputation = Double.NaN;
    private final LfBranch lfBranch;
    private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

    public ComputedElement(LfBranch lfBranch, ClosedBranchSide1DcFlowEquationTerm branchEquation) {
        this.lfBranch = lfBranch;
        this.branchEquation = branchEquation;
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

    protected void setLocalIndex(final int index) {
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

    public ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
        return branchEquation;
    }

    public static void setComputedElementIndexes(Collection<? extends ComputedElement> elements) {
        int index = 0;
        for (ComputedElement element : elements) {
            element.setComputedElementIndex(index++);
        }
    }

    public static void setLocalIndexes(Collection<? extends ComputedElement> elements) {
        int index = 0;
        for (ComputedElement element : elements) {
            element.setLocalIndex(index++);
        }
    }
}
