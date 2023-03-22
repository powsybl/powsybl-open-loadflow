package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ClosedBranchDisymCoupledPowerEquationTerm extends AbstractClosedBranchDisymCoupledFlowEquationTerm {

    public ClosedBranchDisymCoupledPowerEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, boolean isActive, boolean isSide1, int sequenceNum) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
        this.isActive = isActive;
        this.isSide1 = isSide1;
        this.sequenceNum = sequenceNum;
    }

    private final boolean isActive; // true if active power asked, false if reactive power asked
    private final boolean isSide1; // true if p1 or q1, false if p2 or q2
    private final int sequenceNum; // 0 = hompolar, 1 = direct, 2 = inverse

    /* protected double calculateSensi(double dph1, double dph2, double dv1, double dv2, double da1, double dr1) {
        double v1 = v1();
        double r1 = r1();
        double v2 = v2();
        double theta = theta1(ph1(), a1(), ph2());
        double cosTheta = FastMath.cos(theta);
        double sinTheta = FastMath.sin(theta);
        return dp1dph1(y, v1, r1, v2, cosTheta) * dph1
                + dp1dph2(y, v1, r1, v2, cosTheta) * dph2
                + dp1dv1(y, FastMath.sin(ksi), g1, v1, r1, v2, sinTheta) * dv1
                + dp1dv2(y, v1, r1, sinTheta) * dv2;
    }*/ // TODO : check sensi is  useful here

    public static double tx(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm) {
        return GenericBranchPowerTerm.tx(i, j, g, h, equationTerm);
    }

    public static double ty(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm) {
        return GenericBranchPowerTerm.ty(i, j, g, h, equationTerm);
    }

    public static double dtx(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> var, int di) {
        return GenericBranchPowerTerm.dtx(i, j, g, h, equationTerm, var, di);
    }

    public static double dty(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> var, int di) {
        return GenericBranchPowerTerm.dty(i, j, g, h, equationTerm, var, di);
    }

    public static double pqij(boolean isActive, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledPowerEquationTerm eqTerm) {

        if (isActive && isSide1 && sequenceNum == 1) { // P1
            return tx(1, 1, 1, 0, eqTerm) + tx(1, 1, 1, 1, eqTerm) + tx(1, 1, 1, 2, eqTerm)
                    + tx(1, 2, 1, 0, eqTerm) + tx(1, 2, 1, 1, eqTerm) + tx(1, 2, 1, 2, eqTerm);
        } else if (!isActive && isSide1 && sequenceNum == 1) { // Q1
            return ty(1, 1, 1, 0, eqTerm) + ty(1, 1, 1, 1, eqTerm) + ty(1, 1, 1, 2, eqTerm)
                    + ty(1, 2, 1, 0, eqTerm) + ty(1, 2, 1, 1, eqTerm) + ty(1, 2, 1, 2, eqTerm);
        } else if (isActive && !isSide1 && sequenceNum == 1) { // P2
            return tx(2, 2, 1, 0, eqTerm) + tx(2, 2, 1, 1, eqTerm) + tx(2, 2, 1, 2, eqTerm)
                    + tx(2, 1, 1, 0, eqTerm) + tx(2, 1, 1, 1, eqTerm) + tx(2, 1, 1, 2, eqTerm);
        } else if (!isActive && !isSide1 && sequenceNum == 1) { // Q2
            return ty(2, 2, 1, 0, eqTerm) + ty(2, 2, 1, 1, eqTerm) + ty(2, 2, 1, 2, eqTerm)
                    + ty(2, 1, 1, 0, eqTerm) + ty(2, 1, 1, 1, eqTerm) + ty(2, 1, 1, 2, eqTerm);
        } else if (isActive && isSide1 && sequenceNum == 0) { // Po1
            return tx(1, 1, 0, 0, eqTerm) + tx(1, 1, 0, 1, eqTerm) + tx(1, 1, 0, 2, eqTerm)
                    + tx(1, 2, 0, 0, eqTerm) + tx(1, 2, 0, 1, eqTerm) + tx(1, 2, 0, 2, eqTerm);
        } else if (!isActive && isSide1 && sequenceNum == 0) { // Qo1
            return ty(1, 1, 0, 0, eqTerm) + ty(1, 1, 0, 1, eqTerm) + ty(1, 1, 0, 2, eqTerm)
                    + ty(1, 2, 0, 0, eqTerm) + ty(1, 2, 0, 1, eqTerm) + ty(1, 2, 0, 2, eqTerm);
        } else if (isActive && !isSide1 && sequenceNum == 0) { // Po2
            return tx(2, 2, 0, 0, eqTerm) + tx(2, 2, 0, 1, eqTerm) + tx(2, 2, 0, 2, eqTerm)
                    + tx(2, 1, 0, 0, eqTerm) + tx(2, 1, 0, 1, eqTerm) + tx(2, 1, 0, 2, eqTerm);
        } else if (!isActive && !isSide1 && sequenceNum == 0) { // Qo2
            return ty(2, 2, 0, 0, eqTerm) + ty(2, 2, 0, 1, eqTerm) + ty(2, 2, 0, 2, eqTerm)
                    + ty(2, 1, 0, 0, eqTerm) + ty(2, 1, 0, 1, eqTerm) + ty(2, 1, 0, 2, eqTerm);
        } else if (isActive && isSide1 && sequenceNum == 2) { // Pi1
            return tx(1, 1, 2, 0, eqTerm) + tx(1, 1, 2, 1, eqTerm) + tx(1, 1, 2, 2, eqTerm)
                    + tx(1, 2, 2, 0, eqTerm) + tx(1, 2, 2, 1, eqTerm) + tx(1, 2, 2, 2, eqTerm);
        } else if (!isActive && isSide1 && sequenceNum == 2) { // Qi1
            return ty(1, 1, 2, 0, eqTerm) + ty(1, 1, 2, 1, eqTerm) + ty(1, 1, 2, 2, eqTerm)
                    + ty(1, 2, 2, 0, eqTerm) + ty(1, 2, 2, 1, eqTerm) + ty(1, 2, 2, 2, eqTerm);
        } else if (isActive && !isSide1 && sequenceNum == 2) { // Pi2
            return tx(2, 2, 2, 0, eqTerm) + tx(2, 2, 2, 1, eqTerm) + tx(2, 2, 2, 2, eqTerm)
                    + tx(2, 1, 2, 0, eqTerm) + tx(2, 1, 2, 1, eqTerm) + tx(2, 1, 2, 2, eqTerm);
        } else if (!isActive && !isSide1 && sequenceNum == 2) { // Qi2
            return ty(2, 2, 2, 0, eqTerm) + ty(2, 2, 2, 1, eqTerm) + ty(2, 2, 2, 2, eqTerm)
                    + ty(2, 1, 2, 0, eqTerm) + ty(2, 1, 2, 1, eqTerm) + ty(2, 1, 2, 2, eqTerm);
        } else {
            throw new IllegalStateException("Unknow variable type");
        }
    }

    /*private static double dp1dv1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {

        return r1 * (2 * g1 * r1 * v1 + 2 * y * r1 * v1 * sinKsi - y * R2 * v2 * sinTheta);
    }

    private static double dp1dv2(double y, double v1, double r1, double sinTheta) {
        return -y * r1 * R2 * v1 * sinTheta;
    }

    private static double dp1dph1(double y, double v1, double r1, double v2, double cosTheta) {
        return y * r1 * R2 * v1 * v2 * cosTheta;
    }

    private static double dp1dph2(double y, double v1, double r1, double v2, double cosTheta) {
        return -dp1dph1(y, v1, r1, v2, cosTheta);
    }

    private static double dp1da1(double y, double v1, double r1, double v2, double cosTheta) {
        return dp1dph1(y, v1, r1, v2, cosTheta);
    }

    private static double dp1dr1(double y, double sinKsi, double g1, double v1, double r1, double v2, double sinTheta) {
        return v1 * (2 * r1 * v1 * (g1 + y * sinKsi) - y * R2 * v2 * sinTheta);
    }*/

    public static double dpqij(boolean isActive, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledPowerEquationTerm eqTerm, Variable<AcVariableType> var, int di) {

        // di is the side of "variable" that is used for derivation
        if (isActive && isSide1 && sequenceNum == 1) {
            // dP1
            return dtx(1, 1, 1, 0, eqTerm, var, di) + dtx(1, 1, 1, 1, eqTerm, var, di) + dtx(1, 1, 1, 2, eqTerm, var, di)
                    + dtx(1, 2, 1, 0, eqTerm, var, di) + dtx(1, 2, 1, 1, eqTerm, var, di) + dtx(1, 2, 1, 2, eqTerm, var, di);
        } else if (!isActive && isSide1 && sequenceNum == 1) {
            // dQ1
            return dty(1, 1, 1, 0, eqTerm, var, di) + dty(1, 1, 1, 1, eqTerm, var, di) + dty(1, 1, 1, 2, eqTerm, var, di)
                    + dty(1, 2, 1, 0, eqTerm, var, di) + dty(1, 2, 1, 1, eqTerm, var, di) + dty(1, 2, 1, 2, eqTerm, var, di);
        } else if (isActive && !isSide1 && sequenceNum == 1) {
            // dP2
            return dtx(2, 2, 1, 0, eqTerm, var, di) + dtx(2, 2, 1, 1, eqTerm, var, di) + dtx(2, 2, 1, 2, eqTerm, var, di)
                    + dtx(2, 1, 1, 0, eqTerm, var, di) + dtx(2, 1, 1, 1, eqTerm, var, di) + dtx(2, 1, 1, 2, eqTerm, var, di);
        } else if (!isActive && !isSide1 && sequenceNum == 1) {
            // dQ2
            return dty(2, 2, 1, 0, eqTerm, var, di) + dty(2, 2, 1, 1, eqTerm, var, di) + dty(2, 2, 1, 2, eqTerm, var, di)
                    + dty(2, 1, 1, 0, eqTerm, var, di) + dty(2, 1, 1, 1, eqTerm, var, di) + dty(2, 1, 1, 2, eqTerm, var, di);
        } else if (isActive && isSide1 && sequenceNum == 0) {
            // dPo1
            return dtx(1, 1, 0, 0, eqTerm, var, di) + dtx(1, 1, 0, 1, eqTerm, var, di) + dtx(1, 1, 0, 2, eqTerm, var, di)
                    + dtx(1, 2, 0, 0, eqTerm, var, di) + dtx(1, 2, 0, 1, eqTerm, var, di) + dtx(1, 2, 0, 2, eqTerm, var, di);
        } else if (!isActive && isSide1 && sequenceNum == 0) {
            // dQo1
            return dty(1, 1, 0, 0, eqTerm, var, di) + dty(1, 1, 0, 1, eqTerm, var, di) + dty(1, 1, 0, 2, eqTerm, var, di)
                    + dty(1, 2, 0, 0, eqTerm, var, di) + dty(1, 2, 0, 1, eqTerm, var, di) + dty(1, 2, 0, 2, eqTerm, var, di);
        } else if (isActive && !isSide1 && sequenceNum == 0) {
            // dPo2
            return dtx(2, 2, 0, 0, eqTerm, var, di) + dtx(2, 2, 0, 1, eqTerm, var, di) + dtx(2, 2, 0, 2, eqTerm, var, di)
                    + dtx(2, 1, 0, 0, eqTerm, var, di) + dtx(2, 1, 0, 1, eqTerm, var, di) + dtx(2, 1, 0, 2, eqTerm, var, di);

        } else if (!isActive && !isSide1 && sequenceNum == 0) {
            // dQo2
            return dty(2, 2, 0, 0, eqTerm, var, di) + dty(2, 2, 0, 1, eqTerm, var, di) + dty(2, 2, 0, 2, eqTerm, var, di)
                    + dty(2, 1, 0, 0, eqTerm, var, di) + dty(2, 1, 0, 1, eqTerm, var, di) + dty(2, 1, 0, 2, eqTerm, var, di);
        } else if (isActive && isSide1 && sequenceNum == 2) {
            // dPi1
            return dtx(1, 1, 2, 0, eqTerm, var, di) + dtx(1, 1, 2, 1, eqTerm, var, di) + dtx(1, 1, 2, 2, eqTerm, var, di)
                    + dtx(1, 2, 2, 0, eqTerm, var, di) + dtx(1, 2, 2, 1, eqTerm, var, di) + dtx(1, 2, 2, 2, eqTerm, var, di);
        } else if (!isActive && isSide1 && sequenceNum == 2) {
            // dQi1
            return dty(1, 1, 2, 0, eqTerm, var, di) + dty(1, 1, 2, 1, eqTerm, var, di) + dty(1, 1, 2, 2, eqTerm, var, di)
                    + dty(1, 2, 2, 0, eqTerm, var, di) + dty(1, 2, 2, 1, eqTerm, var, di) + dty(1, 2, 2, 2, eqTerm, var, di);
        } else if (isActive && !isSide1 && sequenceNum == 2) {
            // dPi2
            return dtx(2, 2, 2, 0, eqTerm, var, di) + dtx(2, 2, 2, 1, eqTerm, var, di) + dtx(2, 2, 2, 2, eqTerm, var, di)
                    + dtx(2, 1, 2, 0, eqTerm, var, di) + dtx(2, 1, 2, 1, eqTerm, var, di) + dtx(2, 1, 2, 2, eqTerm, var, di);
        } else if (!isActive && !isSide1 && sequenceNum == 2) {
            // dQi2
            return dty(2, 2, 2, 0, eqTerm, var, di) + dty(2, 2, 2, 1, eqTerm, var, di) + dty(2, 2, 2, 2, eqTerm, var, di)
                    + dty(2, 1, 2, 0, eqTerm, var, di) + dty(2, 1, 2, 1, eqTerm, var, di) + dty(2, 1, 2, 2, eqTerm, var, di);
        } else {
            throw new IllegalStateException("Unknown variable type");
        }
    }

    @Override
    public double eval() {
        //return p1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta1(ksi, ph1(), a1(), ph2())));
        // TODO : test with negative sign ????
        double pQij = pqij(isActive, isSide1, sequenceNum, this);

        /*String seq = "_o";
        if (sequenceNum == 1) {
            seq = "_d";
        } else if (sequenceNum == 2) {
            seq = "_i";
        }

        int side = 2;
        if (isSide1) {
            side = 1;
        }

        String pq = "Q";
        if (isActive) {
            pq = "P";
        }

        System.out.println("========>  Branch : " + this.getName() + " has " + pq + side + seq + " = " + pQij);*/
        return pQij;
        //return pqij(isActive, isSide1, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {

        int di = 0; // side of the derivation variable
        if (variable.equals(v1Var) || variable.equals(v1VarHom) || variable.equals(v1VarInv)
                || variable.equals(ph1Var) || variable.equals(ph1VarHom) || variable.equals(ph1VarInv)
                || variable.equals(a1Var) || variable.equals(r1Var)) {
            di = 1;
        } else if (variable.equals(v2Var) || variable.equals(v2VarHom) || variable.equals(v2VarInv)
                || variable.equals(ph2Var) || variable.equals(ph2VarHom) || variable.equals(ph2VarInv)) {
            di = 2;
        } else {
            throw new IllegalStateException("Unknown variable type");
        }
        Objects.requireNonNull(variable);

        return dpqij(isActive, isSide1, sequenceNum, this, variable, di);
        /*
        double theta = theta1(ksi, ph1(), a1(), ph2());
        if (variable.equals(v1Var)) {
            return dp1dv1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta));
        } else if (variable.equals(v2Var)) {
            return dp1dv2(y, v1(), r1(), FastMath.sin(theta));
        } else if (variable.equals(ph1Var)) {
            return dp1dph1(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(ph2Var)) {
            return dp1dph2(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(a1Var)) {
            return dp1da1(y, v1(), r1(), v2(), FastMath.cos(theta));
        } else if (variable.equals(r1Var)) {
            return dp1dr1(y, FastMath.sin(ksi), g1, v1(), r1(), v2(), FastMath.sin(theta));
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
        */
    }

    @Override
    protected String getName() {
        return "ac_p_d_closed_1";
    }
}
