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
        this.isRealPart = isActive;
        this.isSide1 = isSide1;
        this.sequenceNum = sequenceNum;
    }

    private final boolean isRealPart; // true if active power asked, false if reactive power asked
    private final boolean isSide1; // true if p1 or q1, false if p2 or q2
    private final int sequenceNum; // 0 = zero, 1 = positive, 2 = negative

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

    public static double pqij(boolean isRealPart, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledPowerEquationTerm eqTerm) {

        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        if (isRealPart) { // P
            return tx(s1, s1, sequenceNum, 0, eqTerm) + tx(s1, s1, sequenceNum, 1, eqTerm) + tx(s1, s1, sequenceNum, 2, eqTerm)
                    + tx(s1, s2, sequenceNum, 0, eqTerm) + tx(s1, s2, sequenceNum, 1, eqTerm) + tx(s1, s2, sequenceNum, 2, eqTerm);
        } else { // Q
            return ty(s1, s1, sequenceNum, 0, eqTerm) + ty(s1, s1, sequenceNum, 1, eqTerm) + ty(s1, s1, sequenceNum, 2, eqTerm)
                    + ty(s1, s2, sequenceNum, 0, eqTerm) + ty(s1, s2, sequenceNum, 1, eqTerm) + ty(s1, s2, sequenceNum, 2, eqTerm);
        }
    }

    public static double dpqij(boolean isRealPart, boolean isSide1, int sequenceNum, ClosedBranchDisymCoupledPowerEquationTerm eqTerm, Variable<AcVariableType> var, int di) {

        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        // di is the side of "variable" that is used for derivation
        if (isRealPart) {
            // dP
            return dtx(s1, s1, sequenceNum, 0, eqTerm, var, di) + dtx(s1, s1, sequenceNum, 1, eqTerm, var, di) + dtx(s1, s1, sequenceNum, 2, eqTerm, var, di)
                    + dtx(s1, s2, sequenceNum, 0, eqTerm, var, di) + dtx(s1, s2, sequenceNum, 1, eqTerm, var, di) + dtx(s1, s2, sequenceNum, 2, eqTerm, var, di);
        } else {
            // dQ
            return dty(s1, s1, sequenceNum, 0, eqTerm, var, di) + dty(s1, s1, sequenceNum, 1, eqTerm, var, di) + dty(s1, s1, sequenceNum, 2, eqTerm, var, di)
                    + dty(s1, s2, sequenceNum, 0, eqTerm, var, di) + dty(s1, s2, sequenceNum, 1, eqTerm, var, di) + dty(s1, s2, sequenceNum, 2, eqTerm, var, di);
        }
    }

    @Override
    public double eval() {
        return pqij(isRealPart, isSide1, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {

        int di = 0; // side of the derivation variable
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

        return dpqij(isRealPart, isSide1, sequenceNum, this, variable, di);
    }

    @Override
    protected String getName() {
        return "ac_p_d_closed_1";
    }
}
