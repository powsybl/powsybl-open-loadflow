package com.powsybl.openloadflow.ac.networktest.excluded;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.networktest.AbstractClosedBranchDcFlowEquationTermV2;
import com.powsybl.openloadflow.ac.networktest.LfDcLine;
import com.powsybl.openloadflow.ac.networktest.LfDcNode;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;
public class ClosedBranchSide2DcCurrentMagnitudeEquationTermV2 extends AbstractClosedBranchDcFlowEquationTermV2 {

    public ClosedBranchSide2DcCurrentMagnitudeEquationTermV2(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode2, dcNode2, variableSet, Fortescue.SequenceType.POSITIVE);
    }

    public double calculateSensi(double p2, double v2,
                                 double dv2, double dp2) {
        return di2dp2(v2)*dp2+di2dv2(v2,p2)*dv2;
    }

    protected double calculateSensi(double dp2, double dv2){
        return calculateSensi(p2(), v2(), dv2, dp2);
    }

    public static double i2(double v2, double p2) {
        return p2/v2;
    }

    public static double di2dv2(double v2, double p2) {
        return -p2/(v2*v2);
    }

    public static double di2dp2(double v2) {
        return 1/v2;
    }

    public static double di2dp1(double v1) {
        return -1/v1;
    }

    public static double di2dv1(double v1, double p1) {
        return -p1/(v1*v1);
    }





    @Override
    public double eval() {
        return i2(v2(), v2());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2dv1(v1(), p1());
        } else if (variable.equals(v2Var)) {
            return di2dv2(v2(), p2());
        } else if (variable.equals(p1Var)) {
            return di2dp1(v1());
        } else if (variable.equals(p2Var)) {
            return di2dp2(v2());
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_i_closed_2";
    }
}