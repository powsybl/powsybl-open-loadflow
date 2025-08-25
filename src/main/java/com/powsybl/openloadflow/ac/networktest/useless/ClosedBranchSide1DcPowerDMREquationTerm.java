package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

public class ClosedBranchSide1DcPowerDMREquationTerm extends AbstractClosedBranchDcFlowDMREquationTerm {

    public ClosedBranchSide1DcPowerDMREquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }


    public static double p1(double v1, double v2, double v1R, double v2R, double r) {
        // -U1 * (U1 - U2) / r
        return -(v1 - v1R) * ((v1 - v1R) - (v2 - v2R)) / r;
    }

    public static double dp1dv1(double v1, double v2, double v1R, double v2R, double r) {
        return (v2 - v2R + 2 * v1R - 2 * v1)/ r;
    }

    public static double dp1dv2(double v1, double v1R, double r) {
        return (v1 - v1R) / r;
    }

    public static double dp1dv1R(double v1, double v2, double v1R, double v2R, double r) {
        return (v2R - v2 + 2 * v1 - 2 * v1R) / r;
    }

    public static double dp1dv2R(double v1, double v1R, double r) {
        return -(v1 - v1R) / r;
    }


    @Override
    public double eval() {
        return p1(v1(), v2(), v1R(), v2R(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1(v1(), v2(), v1R(), v2R(), r);
        } else if (variable.equals(v2Var)) {
            return dp1dv2(v1(), v1R(), r);
        } else if (variable.equals(v1RVar)) {
            return dp1dv1R(v1(), v2(), v1R(), v2R(), r);
        } else if (variable.equals(v2RVar)) {
            return dp1dv2R(v1(), v1R(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
