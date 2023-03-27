package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

public class ClosedBranchI1yFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchI1yFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchI1yFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        /*double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta1(ksi, ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        double sinKsi = FastMath.sin(ksi);
        return dp1dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp1dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp1dv1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dv1
                + dp1dv2(y, v1, r1, sinTheta) * dv2
                + dp1da1(y, v1, r1, v2, cosTheta) * da1
                + dp1dr1(y, sinKsi, g1, v1, r1, v2, sinTheta) * dr1;*/
        return 0;
    }

    // ignoring for now rho, We have:
    // [I1x]   [ g1+g12  -b1-b12   -g12     b12   ]   [V1x]
    // [I1y]   [ b1+b12   g1+g12   -b12    -g12   ]   [V1y]
    // [I2x] = [  -g21     b21    g2+g21  -b2-b21 ] * [V2x]
    // [I2y]   [  -b21    -g21    b2+b21   g2+g21 ]   [V2y]

    public static double i1y(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return (b1 + b12) * v1 * Math.cos(ph1) + (g1 + g12) * v1 * Math.sin(ph1) - b12 * v2 * Math.cos(ph2) - g12 * v2 * Math.sin(ph2);
    }

    private static double di1ydv1(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return (b1 + b12) * Math.cos(ph1) + (g1 + g12) * Math.sin(ph1);
    }

    private static double di1ydv2(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return -b12 * Math.cos(ph2) - g12 * Math.sin(ph2);
    }

    private static double di1ydph1(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return -(b1 + b12) * v1 * Math.sin(ph1) + (g1 + g12) * v1 * Math.cos(ph1);
    }

    private static double di1ydph2(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return b12 * v2 * Math.sin(ph2) - g12 * v2 * Math.cos(ph2);
    }

    private static double di1yda1(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return 0;
    }

    private static double di1ydr1(double g1, double b1, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return 0;
    }

    @Override
    public double eval() {
        return i1y(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1(ksi, ph1(), a1(), ph2());
        if (variable.equals(v1Var)) {
            return di1ydv1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(v2Var)) {
            return di1ydv2(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(ph1Var)) {
            return di1ydph1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(ph2Var)) {
            return di1ydph2(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(a1Var)) {
            return di1yda1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(r1Var)) {
            return di1ydr1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_ix_closed_1";
    }
}
