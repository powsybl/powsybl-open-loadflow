package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

public class ClosedBranchSide1DcPowerEquationTermV2 extends AbstractClosedBranchDcFlowEquationTermV2 {

    public ClosedBranchSide1DcPowerEquationTermV2(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet, Fortescue.SequenceType.POSITIVE);
    }

    public static double calculateSensi(double v1, double v2,
                                        double dv1, double dv2, double r) {
        return dp1dv1(v1, v2, r) * dv1 + dp1dv2(v1, v2, r) * dv2;
    }

    protected double calculateSensi(double dv1, double dv2) {
        return calculateSensi(v1(), v2(), dv1, dv2, r);
    }

    public static double p1(double v1, double v2, double r) {
        return v1 * (v2 - v1) / r;
    }

    public static double dp1dv1(double v1, double v2, double r) {
        return (v2 - 2 * v1) / r;
    }

    public static double dp1dv2(double v1, double v2, double r) {
        return v1 / r;
    }

    @Override
    public double eval() {
        System.out.println("##############################_____P1_____##############################");
        System.out.println(v1());
        System.out.println(v2());
        System.out.println(r);
        System.out.println(p1(v1(), v2(), r));
        return p1(v1(), v2(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1(v1(), v2(), r);
        } else if (variable.equals(v2Var)) {
            return dp1dv2(v1(), v2(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_p_closed_1";
    }
}
