package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

public class ClosedBranchSide2DcPowerDMREquationTerm extends AbstractClosedBranchDcFlowDMREquationTerm {

    public ClosedBranchSide2DcPowerDMREquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }

    public static double p2(double v1, double v2, double v1R, double v2R, double r) {
        // -U2 * (U2 - U1) / r
        return (v2 - v2R) * ((v1 - v1R) - (v2 - v2R)) / r;
    }

    public static double dp2dv2(double v1, double v2, double v1R, double v2R, double r) {
        return (v1 - v1R + 2 * v2R - 2 * v2) / r;
    }

    public static double dp2dv1(double v2, double v2R, double r) {
        return (v2 - v2R) / r;
    }

    public static double dp2dv2R(double v1, double v2, double v1R, double v2R, double r) {
        return (v1R - v1 + 2 * v2 - 2 * v2R) / r;
    }

    public static double dp2dv1R(double v2, double v2R, double r) {
        return (v2 - v2R) / r;
    }


    @Override
    public double eval() {
        return p2(v1(), v2(), v1R(), v2R(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp2dv2(v1(), v2(), v1R(), v2R(), r);
        } else if (variable.equals(v2Var)) {
            return dp2dv1(v1(), v1R(), r);
        } else if (variable.equals(v1RVar)) {
            return dp2dv2R(v1(), v2(), v1R(), v2R(), r);
        } else if (variable.equals(v2RVar)) {
            return dp2dv1R(v1(), v1R(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}

