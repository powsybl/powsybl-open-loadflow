package com.powsybl.openloadflow.ac.equations.asym;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.Fortescue;

import java.util.Objects;

/**
 * We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
 *     where i,j are line's ends i,j included in {1,2}
 *     where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}
 *
 *  Expanded formula :
 *  T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
 *                 - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *              -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *                 + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )
 *
 *  By construction we have :
 *           [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
 *           [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
 *  [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
 *           [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
 *           [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
 *           [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymmetricalClosedBranchCoupledPowerEquationTerm extends AbstractAsymmetricalClosedBranchCoupledFlowEquationTerm {

    public AsymmetricalClosedBranchCoupledPowerEquationTerm(LfBranch branch, LfBus bus1, LfBus bus2, VariableSet<AcVariableType> variableSet,
                                                            boolean isRealPart, boolean isSide1, Fortescue.SequenceType sequenceType) {
        super(branch, bus1, bus2, variableSet, isRealPart, isSide1, sequenceType);
    }

    public static double powerdTxdV(int i, int j, int g, int h,
                                    double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh, int derivSide, int derivSequence) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) + yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == derivSide && g == derivSequence && j == derivSide && h == derivSequence) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == derivSide && g == derivSequence) {
            return ri * rj * vhj * tmpVal;
        } else if (j == derivSide && h == derivSequence) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double powerdTxdPh(int i, int j, int g, int h,
                                     double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh, int derivSide, int derivSequence) {
        if (i == derivSide && g == derivSequence && j == derivSide && h == derivSequence) {
            return 0;
        } else if (i == derivSide && g == derivSequence) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == derivSide && h == derivSequence) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double powerdTydV(int i, int j, int g, int h,
                                    double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh, int derivSide, int derivSequence) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == derivSide && g == derivSequence && j == derivSide && h == derivSequence) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == derivSide && g == derivSequence) {
            return ri * rj * vhj * tmpVal;
        } else if (j == derivSide && h == derivSequence) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double powerdTydPh(int i, int j, int g, int h,
                                     double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh, int derivSide, int derivSequence) {
        if (i == derivSide && g == derivSequence && j == derivSide && h == derivSequence) {
            return 0;
        } else if (i == derivSide && g == derivSequence) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) + yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == derivSide && h == derivSequence) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) + yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public double tx(int i, int j, int g, int h) {
        return r(i) * r(j) * v(g, i) * v(h, j) * (y.getYxijgh(i, j, g, h) * Math.cos(a(i) - a(j) + ph(g, i) - ph(h, j))
                + y.getYyijgh(i, j, g, h) * Math.sin(a(i) - a(j) + ph(g, i) - ph(h, j)));
    }

    public double ty(int i, int j, int g, int h) {
        return r(i) * r(j) * v(g, i) * v(h, j) * (y.getYxijgh(i, j, g, h) * Math.sin(a(i) - a(j) + ph(g, i) - ph(h, j))
                - y.getYyijgh(i, j, g, h) * Math.cos(a(i) - a(j) + ph(g, i) - ph(h, j)));
    }

    public double dtx(int i, int j, int g, int h, Variable<AcVariableType> variable, int iDerivative) {
        Objects.requireNonNull(variable);
        int derivationSequence = getSequenceType(variable).getNum();
        if (isPhase(variable)) {
            return powerdTxdPh(i, j, g, h,
                    r(i), r(j), a(i), a(j), v(g, i), v(h, j), ph(g, i), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        } else {
            return powerdTxdV(i, j, g, h,
                    r(i), r(j), a(i), a(j), v(g, i), v(h, j), ph(g, i), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        }
    }

    public double dty(int i, int j, int g, int h, Variable<AcVariableType> variable, int iDerivative) {
        Objects.requireNonNull(variable);
        int derivationSequence = getSequenceType(variable).getNum();
        if (isPhase(variable)) {
            return powerdTydPh(i, j, g, h,
                    r(i), r(j), a(i), a(j), v(g, i), v(h, j), ph(g, i), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        } else {
            return powerdTydV(i, j, g, h,
                    r(i), r(j), a(i), a(j), v(g, i), v(h, j), ph(g, i), ph(h, j), y.getYxijgh(i, j, g, h), y.getYyijgh(i, j, g, h), iDerivative, derivationSequence);
        }
    }

    public double pqij(boolean isRealPart, boolean isSide1, int sequenceNum) {
        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        if (isRealPart) { // P
            return tx(s1, s1, sequenceNum, 0) + tx(s1, s1, sequenceNum, 1) + tx(s1, s1, sequenceNum, 2)
                    + tx(s1, s2, sequenceNum, 0) + tx(s1, s2, sequenceNum, 1) + tx(s1, s2, sequenceNum, 2);
        } else { // Q
            return ty(s1, s1, sequenceNum, 0) + ty(s1, s1, sequenceNum, 1) + ty(s1, s1, sequenceNum, 2)
                    + ty(s1, s2, sequenceNum, 0) + ty(s1, s2, sequenceNum, 1) + ty(s1, s2, sequenceNum, 2);
        }
    }

    public double dpqij(boolean isRealPart, boolean isSide1, int sequenceNum, Variable<AcVariableType> variable, int iDerivative) {
        int s1 = 1;
        int s2 = 2;
        if (!isSide1) {
            s1 = 2;
            s2 = 1;
        }

        // iDerivative is the side of "variable" that is used for derivation
        if (isRealPart) {
            // dP
            return dtx(s1, s1, sequenceNum, 0, variable, iDerivative) + dtx(s1, s1, sequenceNum, 1, variable, iDerivative) + dtx(s1, s1, sequenceNum, 2, variable, iDerivative)
                    + dtx(s1, s2, sequenceNum, 0, variable, iDerivative) + dtx(s1, s2, sequenceNum, 1, variable, iDerivative) + dtx(s1, s2, sequenceNum, 2, variable, iDerivative);
        } else {
            // dQ
            return dty(s1, s1, sequenceNum, 0, variable, iDerivative) + dty(s1, s1, sequenceNum, 1, variable, iDerivative) + dty(s1, s1, sequenceNum, 2, variable, iDerivative)
                    + dty(s1, s2, sequenceNum, 0, variable, iDerivative) + dty(s1, s2, sequenceNum, 1, variable, iDerivative) + dty(s1, s2, sequenceNum, 2, variable, iDerivative);
        }
    }

    @Override
    public double eval() {
        return pqij(isRealPart, isSide1, sequenceType.getNum());
    }

    @Override
    public double der(Variable<AcVariableType> variable) {
        Objects.requireNonNull(variable);
        return dpqij(isRealPart, isSide1, sequenceType.getNum(), variable, sideOfDerivative(variable));
    }

    @Override
    public String getName() {
        return "ac_pq_coupled_closed_1";
    }
}
