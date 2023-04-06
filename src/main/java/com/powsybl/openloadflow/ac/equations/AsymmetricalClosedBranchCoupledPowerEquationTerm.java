package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalClosedBranchCoupledPowerEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledPowerEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                            boolean isRealPart, boolean isSide1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, isRealPart, isSide1, sequenceType);
    }

    public static double tx(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm equationTerm) {
        return GenericBranchPowerTerm.powerTx(i, j, g, h, equationTerm);
    }

    public static double ty(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm equationTerm) {
        return GenericBranchPowerTerm.powerTy(i, j, g, h, equationTerm);
    }

    public static double dtx(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> variable, int iDerivative) {
        return GenericBranchPowerTerm.powerdTx(i, j, g, h, equationTerm, variable, iDerivative);
    }

    public static double dty(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> variable, int iDerivative) {
        return GenericBranchPowerTerm.powerdTy(i, j, g, h, equationTerm, variable, iDerivative);
    }

    public static double pqij(boolean isRealPart, boolean isSide1, int sequenceNum, AsymmetricalClosedBranchCoupledPowerEquationTerm eqTerm) {

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

    public static double dpqij(boolean isRealPart, boolean isSide1, int sequenceNum, AsymmetricalClosedBranchCoupledPowerEquationTerm eqTerm, Variable<AcVariableType> variable, int iDerivative) {

        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        // iDerivative is the side of "variable" that is used for derivation
        if (isRealPart) {
            // dP
            return dtx(s1, s1, sequenceNum, 0, eqTerm, variable, iDerivative) + dtx(s1, s1, sequenceNum, 1, eqTerm, variable, iDerivative) + dtx(s1, s1, sequenceNum, 2, eqTerm, variable, iDerivative)
                    + dtx(s1, s2, sequenceNum, 0, eqTerm, variable, iDerivative) + dtx(s1, s2, sequenceNum, 1, eqTerm, variable, iDerivative) + dtx(s1, s2, sequenceNum, 2, eqTerm, variable, iDerivative);
        } else {
            // dQ
            return dty(s1, s1, sequenceNum, 0, eqTerm, variable, iDerivative) + dty(s1, s1, sequenceNum, 1, eqTerm, variable, iDerivative) + dty(s1, s1, sequenceNum, 2, eqTerm, variable, iDerivative)
                    + dty(s1, s2, sequenceNum, 0, eqTerm, variable, iDerivative) + dty(s1, s2, sequenceNum, 1, eqTerm, variable, iDerivative) + dty(s1, s2, sequenceNum, 2, eqTerm, variable, iDerivative);
        }
    }

    @Override
    public double eval() {
        return pqij(isRealPart, isSide1, sequenceNum, this);
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return dpqij(isRealPart, isSide1, sequenceNum, this, variable, sideOfDerivative(variable));
    }

    @Override
    public String getName() {
        return "ac_pq_coupled_closed_1";
    }
}
