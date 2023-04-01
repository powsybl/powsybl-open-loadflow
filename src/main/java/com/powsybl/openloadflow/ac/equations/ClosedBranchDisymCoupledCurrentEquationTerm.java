package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ClosedBranchDisymCoupledCurrentEquationTerm extends AbstractClosedBranchDisymCoupledFlowEquationTerm {

    public ClosedBranchDisymCoupledCurrentEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                     boolean deriveA1, boolean deriveR1, boolean isActive, boolean isSide1, int sequenceNum) {
        super(branch, bus1, bus2, variableSet, deriveA1, deriveR1);
        this.isActive = isActive;
        this.isSide1 = isSide1;
        this.sequenceNum = sequenceNum;
    }

    private final boolean isActive; // true if active power asked, false if reactive power asked
    private final boolean isSide1; // true if i1x or i1y, false if i2x or i2y
    private final int sequenceNum; // 0 = zero, 1 = positive, 2 = negative

    public static double tx(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm equationTerm) {
        return GenericBranchCurrentTerm.tx(i, j, g, h, equationTerm);
    }

    public static double ty(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm equationTerm) {
        return GenericBranchCurrentTerm.ty(i, j, g, h, equationTerm);
    }

    public static double dtx(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm equationTerm, Variable<AcVariableType> var, int di) {
        return GenericBranchCurrentTerm.dtx(i, j, g, h, equationTerm, var, di);
    }

    public static double dty(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm equationTerm, Variable<AcVariableType> var, int di) {
        return GenericBranchCurrentTerm.dty(i, j, g, h, equationTerm, var, di);
    }

    public static double pqij(boolean isActive, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledCurrentEquationTerm eqTerm) {

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

    public static double dpqij(boolean isActive, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledCurrentEquationTerm eqTerm, Variable<AcVariableType> var, int di) {

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
        return pqij(isActive, isSide1, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {

        int di; // side of the derivation variable
        if (variable.equals(v1Var) || variable.equals(v1VarZero) || variable.equals(v1VarNegative)
                || variable.equals(ph1Var) || variable.equals(ph1VarZero) || variable.equals(ph1VarNegative)
                || variable.equals(a1Var) || variable.equals(r1Var)) {
            di = 1;
        } else if (variable.equals(v2Var) || variable.equals(v2VarZero) || variable.equals(v2VarNegative)
                || variable.equals(ph2Var) || variable.equals(ph2VarZero) || variable.equals(ph2VarNegative)) {
            di = 2;
        } else {
            throw new IllegalStateException("Unknown variable type");
        }
        Objects.requireNonNull(variable);

        return dpqij(isActive, isSide1, sequenceNum, this, variable, di);
    }

    @Override
    protected String getName() {
        return "ac_p_d_closed_1";
    }

}
