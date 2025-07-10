package com.powsybl.openloadflow.ac.networktest.excluded;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.networktest.AbstractClosedBranchDcFlowEquationTermV2;
import com.powsybl.openloadflow.ac.networktest.LfDcLine;
import com.powsybl.openloadflow.ac.networktest.LfDcNode;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;
public class ClosedBranchSide1DcTensionEquationTermV2 extends AbstractClosedBranchDcFlowEquationTermV2 {

    public ClosedBranchSide1DcTensionEquationTermV2(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet, Fortescue.SequenceType.POSITIVE);
    }


    public static double calculateSensi(double p1, double p2, double v2,
                                        double dp1, double dp2, double dv2) {
        return dv1dp1(v2, p2)*dp1 + dv1dp2(p1,p2,v2)*dp2 + dv1dv2(p1,p2)*dv2;
    }

    protected double calculateSensi(double dp1, double dp2, double dv1, double dv2){
        return calculateSensi(p1(), p2(), v2(), dp1, dp2, dv2);
    }

    public static double v1(double p1, double p2, double v2) {
        return p1*v2/p2;
    }

    public static double dv1dp1(double v2, double p2) {
        return v2*p2;
    }

    public static double dv1dv2(double p1, double p2) {
        return p1/p2;
    }

    public static double dv1dp2(double p1, double p2, double v2) {
        return -p1*v2/(p2*p2);
    }

    public static double dv1dv1() {
        return 1;
    }


    @Override
    public double eval() {
        return v1(p1(), p2(), v2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(p1Var)) {
            return dv1dp1(v2(), p2());
        } else if (variable.equals(p2Var)) {
            return dv1dp2(p1(), p2(), v2());
        } else if (variable.equals(v2Var)) {
            return dv1dv2(p1(), p2());
        } else if (variable.equals(v1Var)) {
            return dv1dv1();
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_v_closed_1";
    }
}

