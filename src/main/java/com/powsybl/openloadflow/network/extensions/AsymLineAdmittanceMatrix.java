package com.powsybl.openloadflow.network.extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.MatrixUtil;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLineAdmittanceMatrix {

    // This class is made to build and access the admittance terms that will be used to fill up the Jacobian :
    // The following formulation approach is used :
    //                                        side 1   ________     side 2
    // [ I0_1 ]             [ V0_1 ]            0-----|        |-------0
    // [ I1_1 ]             [ V1_1 ]            1-----|  Y012  |-------1
    // [ I2_1 ]             [ V2_1 ]            2-----|________|-------2
    // [ I0_2 ] = [Y012] *  [ V0_2 ]
    // [ I1_2 ]             [ V1_2 ]
    // [ I2_2 ]             [ V2_2 ]
    //
    // Given that at bus 1 where j is one neighbouring bus, the injection at bus 1 is equal to the sum of Powers from neighboring busses:
    // Sum[j](S_1j) = P_1 + j.Q_1  = Sum[j](V1_1.I1_1j*)
    //               P0_1 + j.Q0_1 = Sum[j](V0_1.I0_1j*)
    //               P2_1 + j.Q2_1 = Sum[j](V2_1.I2_1j*)
    //
    // Substituting [I] by [Y012]*[V] allows to know the equations terms that will fill the jacobian matrix
    //
    // Step 1: Get [Y012]
    // ------------------
    // First step is to compute [ Y012 ] from a 3-phase description because this is how we can describe unbalances of phases for a line:
    // For each a,b,c phase we know the following relation (only true for lines with no mutual inductances, otherwise we must handle full [Yabc] matrix):
    // [Ia_1]   [ ya_11 ya_12 ]   [Va_1]
    // [Ia_2] = [ ya_21 ya_22 ] * [Va_2]
    //            with (for a line only)  :  ya_11 = ga1 + j.ba1 + 1/za   , ya_12 = -1/za   ,   ya_21 = -1/za   ,  ya_22 = ga2 + j.ba2 + 1/za
    //
    // From the fortescue transformation we have:
    // [Ga]         [G0]
    // [Gb] = [F] * [G1]
    // [Gc]         [G2]
    //     where [G] might be [V] or [I]
    //     where [F] is the fortescue transformation matrix
    //
    // Therefore we have:
    //                           [ya_11  0    0  ya_12  0   0  ]
    //                           [  0  yb_11  0    0  yb_12 0  ]
    //          [inv(F)   O  ]   [  0   0   yc_11  0   0  yc_12]   [ F  0 ]
    // [Y012] = [  0   inv(F)] * [ya_21  0    0  ya_22  0   0  ] * [ 0  F ]
    //                           [  0  yb_21  0    0  yb_22 0  ]
    //                           [  0   0   yc_21  0   0  yc_22]
    //
    // [Y012] is a complex matrix
    //
    // Step 2: Define the generic term that will be used to make the link between [Y012] and S[0,1,2] the apparent power
    // -----------------------------------------------------------------------------------------------------------------
    // We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
    //    where i,j are line's ends included in {1,2}
    //    where g,h are fortescue sequences included in {o,d,i}={0,1,2}
    //
    //
    // Step 3 : express the expanded value of T(i,j,g,h):
    // --------------------------------------------------
    // T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
    //                - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //             -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
    //                + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )
    //
    // Step 4 : express the apparent powers with T():
    // ----------------------------------------------
    // S_0_12 = T(1,1,o,o) + T(1,1,o,d) + T(1,1,o,i) + T(1,2,o,o) + T(1,2,o,d) + T(1,2,o,i)
    // S_1_12 = T(1,1,d,o) + T(1,1,d,d) + T(1,1,d,i) + T(1,2,d,o) + T(1,2,d,d) + T(1,2,d,i)
    // S_2_12 = T(1,1,i,o) + T(1,1,i,d) + T(1,1,i,i) + T(1,2,i,o) + T(1,2,i,d) + T(1,2,i,i)
    //
    // Step 5 : make the link between y_ij_gh in T() and [Y012]:
    // ---------------------------------------------------------
    // By construction we have :
    //          [ y_11_oo y_11_od y_11_oi y_12_oo y_12_od y_12_oi ]
    //          [ y_11_do y_11_dd y_11_di y_12_do y_12_dd y_12_di ]
    // [Y012] = [ y_11_io y_11_id y_11_ii y_12_io y_12_id y_12_ii ]
    //          [ y_21_oo y_21_od y_21_oi y_22_oo y_22_od y_22_oi ]
    //          [ y_21_do y_21_dd y_21_di y_22_do y_22_dd y_22_di ]
    //          [ y_21_io y_21_id y_21_ii y_22_io y_22_id y_22_ii ]

    public static final double EPS_VALUE = 0.00000001;

    private final DenseMatrix mY012;

    public AsymLineAdmittanceMatrix(AsymLine asymLine) {
        // input values are given in fortescue component, we build first Y012 and deduce Yabc
        mY012 = update(build(asymLine.getPiZeroComponent(), asymLine.getPiPositiveComponent(), asymLine.getPiNegativeComponent()),
                       asymLine.isPhaseOpenA(), asymLine.isPhaseOpenB(), asymLine.isPhaseOpenC());
    }

    private static DenseMatrix build(SimplePiModel piZeroComponent, SimplePiModel piPositiveComponent, SimplePiModel piNegativeComponent) {
        DenseMatrix mY = new DenseMatrix(12, 12);

        double rz = piZeroComponent.getR();
        double xz = piZeroComponent.getX();
        double g1z = piZeroComponent.getG1();
        double g2z = piZeroComponent.getG2();
        double b1z = piZeroComponent.getB1();
        double b2z = piZeroComponent.getB2();
        double g12z = rz / (rz * rz + xz * xz);
        double b12z = -xz / (rz * rz + xz * xz);

        double g21z = g12z;
        double b21z = b12z;

        double rp = piPositiveComponent.getR();
        double xp = piPositiveComponent.getX();
        double g1p = piPositiveComponent.getG1();
        double g2p = piPositiveComponent.getG2();
        double b1p = piPositiveComponent.getB1();
        double b2p = piPositiveComponent.getB2();
        double g12p = rp / (rp * rp + xp * xp);
        double b12p = -xp / (rp * rp + xp * xp);

        double g21p = g12p;
        double b21p = b12p;

        double rn = piNegativeComponent.getR();
        double xn = piNegativeComponent.getX();
        double g1n = piNegativeComponent.getG1();
        double g2n = piNegativeComponent.getG2();
        double b1n = piNegativeComponent.getB1();
        double b2n = piNegativeComponent.getB2();
        double g12n = rn / (rn * rn + xn * xn);
        double b12n = -xn / (rn * rn + xn * xn);

        double g21n = g12n;
        double b21n = b12n;

        //bloc ya11
        add22Bloc(g12z + g1z, b12z + b1z, 1, 1, mY);
        //bloc ya12
        add22Bloc(-g12z, -b12z, 1, 4, mY);
        //bloc yb11
        add22Bloc(g12p + g1p, b12p + b1p, 2, 2, mY);
        //bloc yb12
        add22Bloc(-g12p, -b12p, 2, 5, mY);
        //bloc yc11
        add22Bloc(g12n + g1n, b12n + b1n, 3, 3, mY);
        //bloc yc12
        add22Bloc(-g12n, -b12n, 3, 6, mY);

        //bloc ya22
        add22Bloc(g21z + g2z, b21z + b2z, 4, 4, mY);
        //bloc ya21
        add22Bloc(-g21z, -b21z, 4, 1, mY);
        //bloc yb22
        add22Bloc(g21p + g2p, b21p + b2p, 5, 5, mY);
        //bloc yb21
        add22Bloc(-g21p, -b21p, 5, 2, mY);
        //bloc yc22
        add22Bloc(g21n + g2n, b21n + b2n, 6, 6, mY);
        //bloc yc21
        add22Bloc(-g21n, -b21n, 6, 3, mY);

        return mY;
    }

    private static DenseMatrix update(DenseMatrix mY012, boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC) {
        // if one phase or more are disconnected we need to update Yabc and then Y012
        if (phaseOpenA || phaseOpenB || phaseOpenC) {
            var mYabc = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.createMatrix()),
                                            mY012,
                                            buildTwoBlocsMatrix(Fortescue.createInverseMatrix()));

            if (phaseOpenA) {
                // we cancel all lines and columns that impact Va or Ia
                cancelComponentMatrix(mYabc, 1);
            }

            if (phaseOpenB) {
                // we cancel all lines and columns that impact Vb or Ib
                cancelComponentMatrix(mYabc, 2);
            }

            if (phaseOpenC) {
                // we cancel all lines and columns that impact Vc or Ic
                cancelComponentMatrix(mYabc, 3);
            }

            return productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.createInverseMatrix()),
                                       mYabc,
                                       buildTwoBlocsMatrix(Fortescue.createMatrix()));
        }
        return mY012;
    }

    private static DenseMatrix buildTwoBlocsMatrix(DenseMatrix m66) {
        // expected 6x6 matrix in input to build a 12x12 matrix
        DenseMatrix mFbloc = new DenseMatrix(12, 12);
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                mFbloc.add(i, j, m66.get(i, j));
                mFbloc.add(i + 6, j + 6, m66.get(i, j));
            }
        }

        return mFbloc;
    }

    private static void cancelComponentMatrix(DenseMatrix m, int component) {
        MatrixUtil.resetRow(m, 2 * component - 2);
        MatrixUtil.resetRow(m, 2 * component - 1);
        MatrixUtil.resetRow(m, 2 * component + 4);
        MatrixUtil.resetRow(m, 2 * component + 5);

        MatrixUtil.resetColumn(m, 2 * component - 2);
        MatrixUtil.resetColumn(m, 2 * component - 1);
        MatrixUtil.resetColumn(m, 2 * component + 4);
        MatrixUtil.resetColumn(m, 2 * component + 5);
    }

    private static void add22Bloc(double mx, double my, int i, int j, DenseMatrix m) {
        m.add(2 * (i - 1), 2 * (j - 1), mx);
        m.add(2 * (i - 1), 2 * (j - 1) + 1, -my);
        m.add(2 * (i - 1) + 1, 2 * (j - 1), my);
        m.add(2 * (i - 1) + 1, 2 * (j - 1) + 1, mx);
    }

    private static DenseMatrix productMatrixM1M2M3(DenseMatrix m1, DenseMatrix m2, DenseMatrix m3) {
        DenseMatrix m2M3 = m2.times(m3);
        DenseMatrix mResult = m1.times(m2M3);

        // clean matrix (reset to zero very low values) in case after fortescue and inverse multiplication
        MatrixUtil.clean(mResult, EPS_VALUE);

        return mResult;
    }

    public boolean isCoupled() {
        // checking values of extra diagonal bloc term to see if equations between the three sequences are independant
        boolean coupled = false;
        for (int i = 1; i <= 6; i++) {
            for (int j = 1; j <= 6; j++) {
                if (i != j && isResidualExistsBloc(mY012, i, j)) {
                    coupled = true;
                    break;
                }
            }
        }
        return coupled;
    }

    private static boolean isResidualExistsBloc(DenseMatrix m, int i, int j) {
        double residual = Math.abs(m.get(2 * i - 2, 2 * j - 2)) + Math.abs(m.get(2 * i - 1, 2 * j - 2));
        return residual > EPS_VALUE;
    }

    public double getYxijgh(int i, int j, int g, int h) {
        return mY012.get(2 * (3 * (i - 1) + g), 2 * (3 * (j - 1) + h));
    }

    public double getYyijgh(int i, int j, int g, int h) {
        return mY012.get(2 * (3 * (i - 1) + g) + 1, 2 * (3 * (j - 1) + h));
    }
}
