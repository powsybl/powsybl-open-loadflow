package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

public class ClosedBranchI2xFlowEquationTerm extends AbstractClosedBranchAcFlowEquationTerm {

    public ClosedBranchI2xFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, Fortescue.SequenceType.POSITIVE);
    }

    public ClosedBranchI2xFlowEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                           boolean deriveA1, boolean deriveR1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1, sequenceType);
    }

    protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        return 0;
    }

    // ignoring for now rho, We have:
    // [I1x]   [ g1+g12  -b1-b12   -g12     b12   ]   [V1x]
    // [I1y]   [ b1+b12   g1+g12   -b12    -g12   ]   [V1y]
    // [I2x] = [  -g21     b21    g2+g21  -b2-b21 ] * [V2x]
    // [I2y]   [  -b21    -g21    b2+b21   g2+g21 ]   [V2y]

    public static double i2x(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -g21 * v1 * Math.cos(ph1) + b21 * v1 * Math.sin(ph1) + (g2 + g21) * v2 * Math.cos(ph2) - (b2 + b21) * v2 * Math.sin(ph2);
    }

    private static double di2xdv1(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -g21 * Math.cos(ph1) + b21 * Math.sin(ph1);
    }

    private static double di2xdv2(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return (g2 + g21) * Math.cos(ph2) - (b2 + b21) * Math.sin(ph2);
    }

    private static double di2xdph1(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return g21 * v1 * Math.sin(ph1) + b21 * v1 * Math.cos(ph1);
    }

    private static double di2xdph2(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        double g21 = g12;
        double b21 = b12;
        return -(g2 + g21) * v2 * Math.sin(ph2) - (b2 + b21) * v2 * Math.cos(ph2);
    }

    private static double di2xda1(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return 0;
    }

    private static double di2xdr1(double g2, double b2, double v1, double ph1, double r1, double v2, double ph2, double g12, double b12) {
        return 0;
    }

    @Override
    public double eval() {
        return i2x(g2, b2, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        double theta = theta1(ksi, ph1(), a1(), ph2());
        if (variable.equals(v1Var)) {
            return di2xdv1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(v2Var)) {
            return di2xdv2(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(ph1Var)) {
            return di2xdph1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(ph2Var)) {
            return di2xdph2(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(a1Var)) {
            return di2xda1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else if (variable.equals(r1Var)) {
            return di2xdr1(g1, b1, v1(), ph1(), r1(), v2(), ph2(), g12, b12);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    @Override
    protected String getName() {
        return "ac_ix_closed_1";
    }
}
