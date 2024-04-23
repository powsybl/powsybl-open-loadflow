/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.contingency.ContingencyElement;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Collection;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public final class ComputedContingencyElement {

    private int contingencyIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
    private double alphaForInjectionState = Double.NaN;
    private double alphaForFlowState = Double.NaN;
    private final ContingencyElement element;
    private final LfBranch lfBranch;
    private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

    public ComputedContingencyElement(final ContingencyElement element, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        this.element = element;
        lfBranch = lfNetwork.getBranchById(element.getId());
        branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
    }

    public int getContingencyIndex() {
        return contingencyIndex;
    }

    public void setContingencyIndex(final int index) {
        this.contingencyIndex = index;
    }

    public int getLocalIndex() {
        return localIndex;
    }

    public void setLocalIndex(final int index) {
        this.localIndex = index;
    }

    public double getAlphaForInjectionState() {
        return alphaForInjectionState;
    }

    public void setAlphaForInjectionState(final double alpha) {
        this.alphaForInjectionState = alpha;
    }

    public double getAlphaForFlowState() {
        return alphaForFlowState;
    }

    public void setAlphaForFlowState(final double alpha) {
        this.alphaForFlowState = alpha;
    }

    public ContingencyElement getElement() {
        return element;
    }

    public LfBranch getLfBranch() {
        return lfBranch;
    }

    public ClosedBranchSide1DcFlowEquationTerm getLfBranchEquation() {
        return branchEquation;
    }

    public static void setContingencyIndexes(Collection<ComputedContingencyElement> elements) {
        int index = 0;
        for (ComputedContingencyElement element : elements) {
            element.setContingencyIndex(index++);
        }
    }

    public static void setLocalIndexes(Collection<ComputedContingencyElement> elements) {
        int index = 0;
        for (ComputedContingencyElement element : elements) {
            element.setLocalIndex(index++);
        }
    }

}
