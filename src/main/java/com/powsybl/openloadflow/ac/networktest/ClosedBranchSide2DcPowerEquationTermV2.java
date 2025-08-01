package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

public class ClosedBranchSide2DcPowerEquationTermV2 extends AbstractClosedBranchDcFlowEquationTermV2 {

    public ClosedBranchSide2DcPowerEquationTermV2(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet, Fortescue.SequenceType.POSITIVE);
    }

    public static double calculateSensi(double v1, double v2,
                                        double dv1, double dv2, double r) {
        return dp2dv1(v2, r) * dv1 + dp2dv2(v1, v2, r) * dv2;
    }

    public static double p2(double v1, double v2, double r) {
        return -v2 * (v2 - v1) / r;
    }

    public static double dp2dv1(double v2, double r) {
        return v2 / r;
    }

    public static double dp2dv2(double v1, double v2, double r) {
        return (-2 * v2 + v1) / r;
    }

    protected double calculateSensi(double dv1, double dv2) {
        return calculateSensi(v1(), v2(), dv1, dv2, r);
    }

    @Override
    public double eval() {
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

