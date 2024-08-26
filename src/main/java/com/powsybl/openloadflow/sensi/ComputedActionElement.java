package com.powsybl.openloadflow.sensi;


import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.*;

import java.util.Collection;

public class ComputedActionElement {

    private int actionIndex = -1; // index of the element in the rhs for +1-1
    private int localIndex = -1; // local index of the element : index of the element in the matrix used in the setAlphas method
    private double alphaForPostContingencyAndActionState = Double.NaN;
    private final LfAction action;
    private final LfBranch lfBranch;
    private final ClosedBranchSide1DcFlowEquationTerm branchEquation;

    public ComputedActionElement(final LfAction action, LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem) {
        this.action = action;
        lfBranch = action.getTapPositionChange().branch();
        branchEquation = equationSystem.getEquationTerm(ElementType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
    }

    public int getActionIndex() {
        return actionIndex;
    }

    public void setActionIndex(final int index) {
        this.actionIndex = index;
    }

    public int getLocalIndex() {
        return localIndex;
    }

    private void setLocalIndex(final int index) {
        this.localIndex = index;
    }

    public double getAlphaForPostContingencyAndActionState() {
        return alphaForPostContingencyAndActionState;
    }

    public void setAlphaForPostContingencyAndActionState(double alphaForPostContingencyAndActionState) {
        this.alphaForPostContingencyAndActionState = alphaForPostContingencyAndActionState;
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

    public static void setActionIndexes(Collection<ComputedActionElement> elements) {
        int index = 0;
        for (ComputedActionElement element : elements) {
            element.setActionIndex(index++);
        }
    }

    public static void setLocalIndexes(Collection<ComputedActionElement> elements) {
        int index = 0;
        for (ComputedActionElement element : elements) {
            element.setLocalIndex(index++);
        }
    }
}
