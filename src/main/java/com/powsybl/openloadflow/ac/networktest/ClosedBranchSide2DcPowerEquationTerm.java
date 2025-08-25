package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;

import java.util.Objects;

public class ClosedBranchSide2DcPowerEquationTerm extends AbstractClosedBranchDcFlowEquationTerm {

    public ClosedBranchSide2DcPowerEquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }

    public static double p2(double v1, double v2, double r) {
        return v2 * (v1 - v2) / r;
    }

    public static double dp2dv1(double v2, double r) {
        return v2 / r;
    }

    public static double dp2dv2(double v1, double v2, double r) {
        return (-2 * v2 + v1) / r;
    }

    @Override
    public double eval() {
        System.out.println("##############################_____V1 V2 P2 side2_____##############################");
        System.out.println(v1());
        System.out.println(v2());
        System.out.println(p2(v1(), v2(), r));
        return p2(v1(), v2(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp2dv1(v2(), r);
        } else if (variable.equals(v2Var)) {
            return dp2dv2(v1(), v2(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_2";
    }
}

