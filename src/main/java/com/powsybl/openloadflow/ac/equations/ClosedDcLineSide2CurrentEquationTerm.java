package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcLine;
import com.powsybl.openloadflow.network.LfDcNode;

import java.util.Objects;

public class ClosedDcLineSide2CurrentEquationTerm extends AbstractClosedDcLineFlowEquationTerm {

    public ClosedDcLineSide2CurrentEquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }

    public static double i2(double v1, double v2, double r) {
        return (v1 - v2) / r;
    }

    public static double di2dv1(double r) {
        return 1 / r;
    }

    public static double di2dv2(double r) {
        return -1 / r;
    }

    @Override
    public double eval() {
        return i2(v1(), v2(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return di2dv1(r);
        } else if (variable.equals(v2Var)) {
            return di2dv2(r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_i_closed_2";
    }
}

