package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;
import org.apache.commons.math3.util.Pair;

import java.util.Objects;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class GenericBranchCurrentTerm {

    // We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_j-a_i)) * y*_ij_gh * V_hj
    //    where i,j are line's ends i,j included in {1,2}
    //    where g,h are fortescue sequences g,h included in {o,d,i} (o = zero = homopolar = 0, d = direct = positive = 1, i = inverse = negative = 2)

    // Expanded formula :
    // T(i,j,g,h) =     rho_i * rho_j * V_hj * yx_ij_gh * cos(a_j - a_i + th_hj)
    //                - rho_i * rho_j * V_hj * yy_ij_gh * sin(a_j - a_i + th_hj)
    //             +j(  rho_i * rho_j * V_hj * yx_ij_gh * sin(a_j - a_i + th_hj)
    //                + rho_i * rho_j * V_hj * yy_ij_gh * cos(a_j - a_i + th_hj) )

    // By construction we have :
    //          [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
    //          [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
    // [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
    //          [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
    //          [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
    //          [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]

    private GenericBranchCurrentTerm() {

    }

    public static double iTx(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT) {
        return eT.r(i) * eT.r(j) * eT.v(h, j) * (getYxijgh(i, j, g, h, eT) * Math.cos(eT.a(j) - eT.a(i) + eT.ph(h, j))
                        - getYyijgh(i, j, g, h, eT) * Math.sin(eT.a(j) - eT.a(i) + eT.ph(h, j)));
    }

    public static double iTy(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT) {
        return eT.r(i) * eT.r(j) * eT.v(h, j) * (getYxijgh(i, j, g, h, eT) * Math.sin(eT.a(j) - eT.a(i) + eT.ph(h, j))
                        + getYyijgh(i, j, g, h, eT) * Math.cos(eT.a(j) - eT.a(i) + eT.ph(h, j)));
    }

    public static double idTx(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT, Variable<AcVariableType> variable, int derivativeSide) {

        Objects.requireNonNull(variable);
        Pair<Integer, Boolean> sequenceAndIsPhase = getSequenceAndPhaseType(variable);
        int derivationSequence = sequenceAndIsPhase.getFirst();
        boolean isPhase = sequenceAndIsPhase.getSecond();

        if (isPhase) {
            return idTxdPh(j, h, eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(h, j), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        } else {
            return idTxdV(j, h, eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        }
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

    public static double idTy(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT, Variable<AcVariableType> variable, int derivativeSide) {

        Objects.requireNonNull(variable);
        Pair<Integer, Boolean> sequenceAndIsPhase = getSequenceAndPhaseType(variable);
        int derivationSequence = sequenceAndIsPhase.getFirst();
        boolean isPhase = sequenceAndIsPhase.getSecond();

        if (isPhase) {
            return idTydPh(j, h, eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.v(h, j), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
        } else {
            return idTydv(j, h, eT.r(i), eT.r(j), eT.a(i), eT.a(j), eT.ph(h, j), getYxijgh(i, j, g, h, eT), getYyijgh(i, j, g, h, eT), derivativeSide, derivationSequence);
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

    public static double getYxijgh(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT) {
        return eT.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
    }

    public static double getYyijgh(int i, int j, int g, int h, ClosedBranchDisymCoupledCurrentEquationTerm eT) {
        return eT.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h));
    }

    public static Pair<Integer, Boolean> getSequenceAndPhaseType(Variable<AcVariableType> variable) {
        int derivationSequence;
        boolean isPhase;
        switch (variable.getType()) {
            case BUS_V:
                derivationSequence = 1;
                isPhase = false;
                break;

            case BUS_PHI:
                derivationSequence = 1;
                isPhase = true;
                break;

            case BUS_V_NEGATIVE:
                derivationSequence = 2;
                isPhase = false;
                break;

            case BUS_PHI_NEGATIVE:
                derivationSequence = 2;
                isPhase = true;
                break;

            case BUS_V_ZERO:
                derivationSequence = 0;
                isPhase = false;
                break;

            case BUS_PHI_ZERO:
                derivationSequence = 0;
                isPhase = true;
                break;

            default:
                throw new IllegalStateException("Unknown variable: ");
        }

        return new Pair<>(derivationSequence, isPhase);
    }

}
