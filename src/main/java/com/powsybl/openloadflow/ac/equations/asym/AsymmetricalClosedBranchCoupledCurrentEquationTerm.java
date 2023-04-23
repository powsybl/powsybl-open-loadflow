package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 *  We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_j-a_i)) * y*_ij_gh * V_hj
 *     where i,j are line's ends i,j included in {1,2}
 *     where g,h are fortescue sequences g,h included in {o,d,i} (o = zero = homopolar = 0, d = direct = positive = 1, i = inverse = negative = 2)
 *
 *  Expanded formula :
 *  T(i,j,g,h) =     rho_i * rho_j * V_hj * yx_ij_gh * cos(a_j - a_i + th_hj)
 *                 - rho_i * rho_j * V_hj * yy_ij_gh * sin(a_j - a_i + th_hj)
 *              +j(  rho_i * rho_j * V_hj * yx_ij_gh * sin(a_j - a_i + th_hj)
 *                 + rho_i * rho_j * V_hj * yy_ij_gh * cos(a_j - a_i + th_hj) )
 *
 *  By construction we have :
 *           [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
 *           [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
 *  [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
 *           [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
 *           [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
 *           [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]
 *
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalClosedBranchCoupledCurrentEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledCurrentEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                              boolean isRealPart, boolean isSide1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, isRealPart, isSide1, sequenceType);
    }

    public double tx(int i, int j, int g, int h) {
        return r(i) * r(j) * v(h, j) * (y.getYxijgh(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j))
                - y.getYyijgh(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j)));
    }

    public double ty(int i, int j, int g, int h) {
        return r(i) * r(j) * v(h, j) * (y.getYxijgh(i, j, g, h) * Math.sin(a(j) - a(i) + ph(h, j))
                + y.getYyijgh(i, j, g, h) * Math.cos(a(j) - a(i) + ph(h, j)));
    }

    public static double idTxdV(int j, int h,
                                double ri, double rj, double ai, double aj,
                                double thhj, double yxijgh, double yyijgh,
                                int derivSide, int derivSequence) {
        if (j == derivSide && h == derivSequence) {
            return ri * rj * (yxijgh * Math.cos(aj - ai + thhj) - yyijgh * Math.sin(aj - ai + thhj));
        }
        return 0;
    }

    public static double idTxdPh(int j, int h,
                                 double ri, double rj, double ai, double aj,
                                 double vhj, double thhj, double yxijgh, double yyijgh,
                                 int derivSide, int derivSequence) {
        if (j == derivSide && h == derivSequence) {
            return ri * rj * vhj * (-yxijgh * Math.sin(aj - ai + thhj) - yyijgh * Math.cos(aj - ai + thhj));
        }
        return 0;
    }

    public double dtx(int i, int j, int g, int h, Variable<AcVariableType> variable, int iDerivative) {
        Objects.requireNonNull(variable);
        int derivationSequence = getSequenceType(variable).getNum();
        if (isPhase(variable)) {
            return idTxdPh(j, h, r(i), r(j), a(i), a(j), v(h, j), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        } else {
            return idTxdV(j, h, r(i), r(j), a(i), a(j), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        }
    }

    public static double idTydv(int j, int h,
                                double ri, double rj, double ai, double aj,
                                double thhj, double yxijgh, double yyijgh,
                                int derivSide, int derivSequence) {
        if (j == derivSide && h == derivSequence) {
            return ri * rj * (yxijgh * Math.sin(aj - ai + thhj) + yyijgh * Math.cos(aj - ai + thhj));
        }
        return 0;
    }

    public static double idTydPh(int j, int h,
                                 double ri, double rj, double ai, double aj,
                                 double vhj, double thhj, double yxijgh, double yyijgh,
                                 int derivSide, int derivSequence) {
        if (j == derivSide && h == derivSequence) {
            return ri * rj * vhj * (yxijgh * Math.cos(aj - ai + thhj) - yyijgh * Math.sin(aj - ai + thhj));
        }
        return 0;
    }

    public double dty(int i, int j, int g, int h, Variable<AcVariableType> variable, int iDerivative) {
        Objects.requireNonNull(variable);
        int derivationSequence = getSequenceType(variable).getNum();
        if (isPhase(variable)) {
            return idTydPh(j, h, r(i), r(j), a(i), a(j), v(h, j), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        } else {
            return idTydv(j, h, r(i), r(j), a(i), a(j), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        }
    }

    public double ixIyij(boolean isRealPart, boolean isSide1, int sequenceNum) {
        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        if (isRealPart) { // Ix
            return tx(s1, s1, sequenceNum, 0) + tx(s1, s1, sequenceNum, 1) + tx(s1, s1, sequenceNum, 2)
                    + tx(s1, s2, sequenceNum, 0) + tx(s1, s2, sequenceNum, 1) + tx(s1, s2, sequenceNum, 2);
        } else { // Iy
            return ty(s1, s1, sequenceNum, 0) + ty(s1, s1, sequenceNum, 1) + ty(s1, s1, sequenceNum, 2)
                    + ty(s1, s2, sequenceNum, 0) + ty(s1, s2, sequenceNum, 1) + ty(s1, s2, sequenceNum, 2);
        }
    }

    public double dIxIyij(boolean isRealPart, boolean isSide1, int sequenceNum, Variable<AcVariableType> variable, int iDerivative) {
        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        // iDerivative is the side of "variable" that is used for derivation
        if (isRealPart) {
            // dIx
            return dtx(s1, s1, sequenceNum, 0, variable, iDerivative) + dtx(s1, s1, sequenceNum, 1, variable, iDerivative) + dtx(s1, s1, sequenceNum, 2, variable, iDerivative)
                    + dtx(s1, s2, sequenceNum, 0, variable, iDerivative) + dtx(s1, s2, sequenceNum, 1, variable, iDerivative) + dtx(s1, s2, sequenceNum, 2, variable, iDerivative);
        } else {
            // dIy
            return dty(s1, s1, sequenceNum, 0, variable, iDerivative) + dty(s1, s1, sequenceNum, 1, variable, iDerivative) + dty(s1, s1, sequenceNum, 2, variable, iDerivative)
                    + dty(s1, s2, sequenceNum, 0, variable, iDerivative) + dty(s1, s2, sequenceNum, 1, variable, iDerivative) + dty(s1, s2, sequenceNum, 2, variable, iDerivative);
        }
    }

    @Override
    public double eval() {
        return ixIyij(isRealPart, isSide1, sequenceType.getNum());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return dIxIyij(isRealPart, isSide1, sequenceType.getNum(), variable, sideOfDerivative(variable));
    }

    @Override
    public String getName() {
        return "ac_ixiy_coupled_closed_1";
    }

}
