package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Variable;

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

    public static double tx(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm) {
        double ri = equationTerm.r(i);
        double rj = equationTerm.r(j);
        double ai = equationTerm.a(i);
        double aj = equationTerm.a(j);
        double vgi = equationTerm.v(g, i);
        double vhj = equationTerm.v(h, j);
        double thgi = equationTerm.ph(g, i);
        double thhj = equationTerm.ph(h, j);
        double yxijgh = equationTerm.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
        double conjYyijgh = -equationTerm.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h)); // we use conjugate value
        //    where i,j are line's ends i,j included in {1,2}
        //    where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}

        return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - conjYyijgh * Math.sin(ai - aj + thgi - thhj));
    }

    public static double ty(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm) {
        double ri = equationTerm.r(i);
        double rj = equationTerm.r(j);
        double ai = equationTerm.a(i);
        double aj = equationTerm.a(j);
        double vgi = equationTerm.v(g, i);
        double vhj = equationTerm.v(h, j);
        double thgi = equationTerm.ph(g, i);
        double thhj = equationTerm.ph(h, j);
        double yxijgh = equationTerm.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
        double conjYyijgh = -equationTerm.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h)); // we use conjugate value
        //    where i,j are line's ends i,j included in {1,2}
        //    where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}

        return ri * rj * vgi * vhj * (yxijgh * Math.sin(ai - aj + thgi - thhj) + conjYyijgh * Math.cos(ai - aj + thgi - thhj));
    }

    public static double dtx(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> variable, int di) {

        // di is the side of the derivation variable and belongs to {1,2}
        double ri = equationTerm.r(i);
        double rj = equationTerm.r(j);
        double ai = equationTerm.a(i);
        double aj = equationTerm.a(j);
        double vgi = equationTerm.v(g, i);
        double vhj = equationTerm.v(h, j);
        double thgi = equationTerm.ph(g, i);
        double thhj = equationTerm.ph(h, j);
        double yxijgh = equationTerm.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
        double conjYyijgh = -equationTerm.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h));
        //    where i,j are line's ends i,j included in {1,2}
        //    where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}

        Objects.requireNonNull(variable);
        if (variable.getType() == AcVariableType.BUS_V && di == 1) {
            return dtxdv1(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V && di == 2) {
            return dtxdv2(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI && di == 1) {
            return dtxdph1(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI && di == 2) {
            return dtxdph2(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_NEGATIVE && di == 1) {
            return dtxdv1i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_NEGATIVE && di == 2) {
            return dtxdv2i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_NEGATIVE && di == 1) {
            return dtxdph1i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_NEGATIVE && di == 2) {
            return dtxdph2i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_ZERO && di == 1) {
            return dtxdv1h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_ZERO && di == 2) {
            return dtxdv2h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_ZERO && di == 1) {
            return dtxdph1h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_ZERO && di == 2) {
            return dtxdph2h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    public static double dtxdv1(int i, int j, int g, int h,
                                double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 1 && g == 1 && j == 1 && h == 1) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 1) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 1) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdv2(int i, int j, int g, int h,
                                double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 2 && g == 1 && j == 2 && h == 1) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 1) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 1) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdph1(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 1 && j == 1 && h == 1) {
            return 0;
        } else if (i == 1 && g == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtxdph2(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 1 && j == 2 && h == 1) {
            return 0;
        } else if (i == 2 && g == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtxdv1i(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 1 && g == 2 && j == 1 && h == 2) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 2) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 2) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdv2i(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 2 && g == 2 && j == 2 && h == 2) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 2) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 2) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdph1i(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 2 && j == 1 && h == 2) {
            return 0;
        } else if (i == 1 && g == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtxdph2i(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 2 && j == 2 && h == 2) {
            return 0;
        } else if (i == 2 && g == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtxdv1h(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 1 && g == 0 && j == 1 && h == 0) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 0) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 0) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdv2h(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj);
        if (i == 2 && g == 0 && j == 2 && h == 0) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 0) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 0) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtxdph1h(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 0 && j == 1 && h == 0) {
            return 0;
        } else if (i == 1 && g == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtxdph2h(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 0 && j == 2 && h == 0) {
            return 0;
        } else if (i == 2 && g == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) - yyijgh * Math.cos(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dty(int i, int j, int g, int h, ClosedBranchDisymCoupledPowerEquationTerm equationTerm, Variable<AcVariableType> variable, int di) {

        // di is the side of the derivation variable and belongs to {1,2}
        double ri = equationTerm.r(i);
        double rj = equationTerm.r(j);
        double ai = equationTerm.a(i);
        double aj = equationTerm.a(j);
        double vgi = equationTerm.v(g, i);
        double vhj = equationTerm.v(h, j);
        double thgi = equationTerm.ph(g, i);
        double thhj = equationTerm.ph(h, j);
        double yxijgh = equationTerm.getmY012().get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
        double conjYyijgh = -equationTerm.getmY012().get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h)); // we use conjugate
        //    where i,j are line's ends i,j included in {1,2}
        //    where g,h are fortescue sequences g,h included in {o,d,i} = {0,1,2}

        Objects.requireNonNull(variable);
        if (variable.getType() == AcVariableType.BUS_V && di == 1) {
            return dtydv1(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V && di == 2) {
            return dtydv2(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI && di == 1) {
            return dtydph1(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI && di == 2) {
            return dtydph2(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_NEGATIVE && di == 1) {
            return dtydv1i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_NEGATIVE && di == 2) {
            return dtydv2i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_NEGATIVE && di == 1) {
            return dtydph1i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_NEGATIVE && di == 2) {
            return dtydph2i(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_ZERO && di == 1) {
            return dtydv1h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_V_ZERO && di == 2) {
            return dtydv2h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_ZERO && di == 1) {
            return dtydph1h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else if (variable.getType() == AcVariableType.BUS_PHI_ZERO && di == 2) {
            return dtydph2h(i, j, g, h, ri, rj, ai, aj, vgi, vhj, thgi, thhj, yxijgh, conjYyijgh);
        } else {
            throw new IllegalStateException("Unknown variable: " + variable);
        }
    }

    public static double dtydv1(int i, int j, int g, int h,
                                double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 1 && g == 1 && j == 1 && h == 1) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 1) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 1) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydv2(int i, int j, int g, int h,
                                double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 2 && g == 1 && j == 2 && h == 1) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 1) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 1) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydph1(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 1 && j == 1 && h == 1) {
            return 0;
        } else if (i == 1 && g == 1) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtydph2(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 1 && j == 2 && h == 1) {
            return 0;
        } else if (i == 2 && g == 1) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 1) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtydv1i(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 1 && g == 2 && j == 1 && h == 2) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 2) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 2) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydv2i(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 2 && g == 2 && j == 2 && h == 2) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 2) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 2) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydph1i(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 2 && j == 1 && h == 2) {
            return 0;
        } else if (i == 1 && g == 2) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtydph2i(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 2 && j == 2 && h == 2) {
            return 0;
        } else if (i == 2 && g == 2) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 2) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtydv1h(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 1 && g == 0 && j == 1 && h == 0) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 1 && g == 0) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 1 && h == 0) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydv2h(int i, int j, int g, int h,
                                 double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        double tmpVal = yxijgh * Math.sin(ai - aj + thgi - thhj) + yyijgh * Math.cos(ai - aj + thgi - thhj);
        if (i == 2 && g == 0 && j == 2 && h == 0) {
            return 2 * ri * rj * vgi * tmpVal;
        } else if (i == 2 && g == 0) {
            return ri * rj * vhj * tmpVal;
        } else if (j == 2 && h == 0) {
            return ri * rj * vgi * tmpVal;
        }
        return 0;
    }

    public static double dtydph1h(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 1 && g == 0 && j == 1 && h == 0) {
            return 0;
        } else if (i == 1 && g == 0) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 1 && h == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

    public static double dtydph2h(int i, int j, int g, int h,
                                  double ri, double rj, double ai, double aj, double vgi, double vhj, double thgi, double thhj, double yxijgh, double yyijgh) {
        if (i == 2 && g == 0 && j == 2 && h == 0) {
            return 0;
        } else if (i == 2 && g == 0) {
            return ri * rj * vgi * vhj * (yxijgh * Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        } else if (j == 2 && h == 0) {
            return ri * rj * vgi * vhj * (yxijgh * -Math.cos(ai - aj + thgi - thhj) - yyijgh * Math.sin(ai - aj + thgi - thhj));
        }
        return 0;
    }

}
