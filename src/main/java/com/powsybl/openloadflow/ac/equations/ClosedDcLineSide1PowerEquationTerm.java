package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfDcLine;
import com.powsybl.openloadflow.network.LfDcNode;

import java.util.Objects;

public class ClosedDcLineSide1PowerEquationTerm extends AbstractClosedDcLineFlowEquationTerm {

    public ClosedDcLineSide1PowerEquationTerm(LfDcLine dcLine, LfDcNode dcNode1, LfDcNode dcNode2, VariableSet<AcVariableType> variableSet) {
        super(dcLine, dcNode1, dcNode2, variableSet);
    }

    public static double p1(double v1, double v2, double r) {
        return -v1 * (v1 - v2) / r;
    }

    public static double dp1dv1(double v1, double v2, double r) {
        return -(2 * v1 - v2) / r;
    }

    public static double dp1dv2(double v1, double r) {
        return v1 / r;
    }

    @Override
    public double eval() {
        return p1(v1(), v2(), r);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        if (variable.equals(v1Var)) {
            return dp1dv1(v1(), v2(), r);
        } else if (variable.equals(v2Var)) {
            return dp1dv2(v1(), r);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    public String getName() {
        return "dc_p_closed_1";
    }
}
