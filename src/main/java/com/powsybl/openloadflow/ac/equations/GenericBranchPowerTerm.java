package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import org.apache.commons.math3.util.Pair;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class GenericBranchPowerTerm {

    // We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
    //    where i,j are line's ends i,j included in {1,2}
    //    where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}

    // Expanded formula :
    // T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
    //                - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //             -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //                + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )

    // By construction we have :
    //          [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
    //          [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
    // [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
    //          [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
    //          [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
    //          [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]

    private GenericBranchPowerTerm() {

    }

    public static double powerTx(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT) {
        return eT.r(i) * eT.r(j) * eT.v(g, i) * eT.v(h, j) * (getYxijgh(i, j, g, h, eT) * Math.cos(eT.a(i) - eT.a(j) + eT.ph(g, i) - eT.ph(h, j))
                + getYyijgh(i, j, g, h, eT) * Math.sin(eT.a(i) - eT.a(j) + eT.ph(g, i) - eT.ph(h, j)));
    }

    public static double powerTy(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT) {
        return eT.r(i) * eT.r(j) * eT.v(g, i) * eT.v(h, j) * (getYxijgh(i, j, g, h, eT) * Math.sin(eT.a(i) - eT.a(j) + eT.ph(g, i) - eT.ph(h, j))
                - getYyijgh(i, j, g, h, eT) * Math.cos(eT.a(i) - eT.a(j) + eT.ph(g, i) - eT.ph(h, j)));
    }

    public static double powerdTx(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT, Variable<AcVariableType> variable, int derivativeSide) {

        Objects.requireNonNull(variable);
        Pair<Integer, Boolean> sequenceAndIsPhase = GenericBranchCurrentTerm.getSequenceAndPhaseType(variable);
        int derivationSequence = sequenceAndIsPhase.getFirst();
        boolean isPhase = sequenceAndIsPhase.getSecond();

        if (isPhase) {
            return powerdTxdPh(i, j, g, h,
                    eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(g, i), eT.v(h, j), eT.ph(g, i), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        } else {
            return powerdTxdV(i, j, g, h,
                    eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(g, i), eT.v(h, j), eT.ph(g, i), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        }
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

    public static double powerdTy(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT, Variable<AcVariableType> variable, int derivativeSide) {

        Objects.requireNonNull(variable);
        Pair<Integer, Boolean> sequenceAndIsPhase = GenericBranchCurrentTerm.getSequenceAndPhaseType(variable);
        int derivationSequence = sequenceAndIsPhase.getFirst();
        boolean isPhase = sequenceAndIsPhase.getSecond();

        if (isPhase) {
            return powerdTydPh(i, j, g, h,
                    eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(g, i), eT.v(h, j), eT.ph(g, i), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        } else {
            return powerdTydV(i, j, g, h,
                    eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(g, i), eT.v(h, j), eT.ph(g, i), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        }
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

    public static double getYxijgh(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT) {
        return eT.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
    }

    public static double getYyijgh(int i, int j, int g, int h, AsymmetricalClosedBranchCoupledPowerEquationTerm eT) {
        return eT.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h));
    }
}
