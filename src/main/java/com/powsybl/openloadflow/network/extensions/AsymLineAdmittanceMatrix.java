package com.powsybl.openloadflow.network.extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.util.Fortescue;

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

    private DenseMatrix mY012;

    public AsymLineAdmittanceMatrix(AsymLine asymLine) {
        // input values are given in fortescue component, we build first Y012 and deduce Yabc
        mY012 = buildAdmittanceMatrix(asymLine);

        // if one phase or more are disconnected we need to update Yabc and then Y012
        if (asymLine.isPhaseOpenA() || asymLine.isPhaseOpenB() || asymLine.isPhaseOpenC()) {
            var mYabc = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.createMatrix()),
                                                                mY012,
                                                                buildTwoBlocsMatrix(Fortescue.createInverseMatrix()));

            if (asymLine.isPhaseOpenA()) {
                // we cancel all lines and columns that impact Va or Ia
                cancelComponentMatrix(mYabc, 1);
            }

            if (asymLine.isPhaseOpenB()) {
                // we cancel all lines and columns that impact Vb or Ib
                cancelComponentMatrix(mYabc, 2);
            }

            if (asymLine.isPhaseOpenC()) {
                // we cancel all lines and columns that impact Vc or Ic
                cancelComponentMatrix(mYabc, 3);
            }

            mY012 = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.createInverseMatrix()),
                                        mYabc,
                                        buildTwoBlocsMatrix(Fortescue.createMatrix()));
        }
    }

    public DenseMatrix buildAdmittanceMatrix(AsymLine asymLine) {
        AsymLinePiValues piValues = asymLine.getPiValues();

        DenseMatrix mY = new DenseMatrix(12, 12);

        double r1 = piValues.getPiComponent1().getR();
        double x1 = piValues.getPiComponent1().getX();
        double g1i = piValues.getPiComponent1().getG1();
        double g1j = piValues.getPiComponent1().getG2();
        double b1i = piValues.getPiComponent1().getB1();
        double b1j = piValues.getPiComponent1().getB2();
        double g1ij = r1 / (r1 * r1 + x1 * x1);
        double b1ij = -x1 / (r1 * r1 + x1 * x1);

        double g1ji = g1ij;
        double b1ji = b1ij;

        double r2 = piValues.getPiComponent2().getR();
        double x2 = piValues.getPiComponent2().getX();
        double g2i = piValues.getPiComponent2().getG1();
        double g2j = piValues.getPiComponent2().getG2();
        double b2i = piValues.getPiComponent2().getB1();
        double b2j = piValues.getPiComponent2().getB2();
        double g2ij = r2 / (r2 * r2 + x2 * x2);
        double b2ij = -x2 / (r2 * r2 + x2 * x2);

        double g2ji = g2ij;
        double b2ji = b2ij;

        double r3 = piValues.getPiComponent3().getR();
        double x3 = piValues.getPiComponent3().getX();
        double g3i = piValues.getPiComponent3().getG1();
        double g3j = piValues.getPiComponent3().getG2();
        double b3i = piValues.getPiComponent3().getB1();
        double b3j = piValues.getPiComponent3().getB2();
        double g3ij = r3 / (r3 * r3 + x3 * x3);
        double b3ij = -x3 / (r3 * r3 + x3 * x3);

        double g3ji = g3ij;
        double b3ji = b3ij;

        //bloc ya11
        add22Bloc(g1ij + g1i, b1ij + b1i, 1, 1, mY);
        //bloc ya12
        add22Bloc(-g1ij, -b1ij, 1, 4, mY);
        //bloc yb11
        add22Bloc(g2ij + g2i, b2ij + b2i, 2, 2, mY);
        //bloc yb12
        add22Bloc(-g2ij, -b2ij, 2, 5, mY);
        //bloc yc11
        add22Bloc(g3ij + g3i, b3ij + b3i, 3, 3, mY);
        //bloc yc12
        add22Bloc(-g3ij, -b3ij, 3, 6, mY);

        //bloc ya22
        add22Bloc(g1ji + g1j, b1ji + b1j, 4, 4, mY);
        //bloc ya21
        add22Bloc(-g1ji, -b1ji, 4, 1, mY);
        //bloc yb22
        add22Bloc(g2ji + g2j, b2ji + b2j, 5, 5, mY);
        //bloc yb21
        add22Bloc(-g2ji, -b2ji, 5, 2, mY);
        //bloc yc22
        add22Bloc(g3ji + g3j, b3ji + b3j, 6, 6, mY);
        //bloc yc21
        add22Bloc(-g3ji, -b3ji, 6, 3, mY);

        return mY;
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

    private static void cancelLineMatrix(DenseMatrix m, int iCancel) {
        for (int j = 0; j < m.getColumnCount(); j++) {
            m.set(iCancel, j, 0);
        }
    }

    private static void cancelColumnMatrix(DenseMatrix m, int jCancel) {
        for (int i = 0; i < m.getRowCount(); i++) {
            m.set(i, jCancel, 0);
        }
    }

    private static void cancelComponentMatrix(DenseMatrix m, int component) {
        cancelLineMatrix(m, 2 * component - 2);
        cancelLineMatrix(m, 2 * component - 1);
        cancelLineMatrix(m, 2 * component + 4);
        cancelLineMatrix(m, 2 * component + 5);

        cancelColumnMatrix(m, 2 * component - 2);
        cancelColumnMatrix(m, 2 * component - 1);
        cancelColumnMatrix(m, 2 * component + 4);
        cancelColumnMatrix(m, 2 * component + 5);
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

        // clean matrix in case after fortescue and inverse multiplication
        for (int i = 0; i < mResult.getRowCount(); i++) {
            for (int j = 0; j < mResult.getColumnCount(); j++) {
                if (Math.abs(mResult.get(i, j)) < EPS_VALUE) {
                    mResult.set(i, j, 0.);
                }
            }
        }

        return mResult;
    }

    public DenseMatrix getmY012() {
        return mY012;
    }

    public boolean isAdmittanceCoupled() {
        // checking values of extra diagonal bloc term to see if equations between the three sequences are independant
        boolean isCoupled = false;
        for (int i = 1; i <= 6; i++) {
            for (int j = 1; j <= 6; j++) {
                if (i != j && isResidualExistsBloc(mY012, i, j)) {
                    isCoupled = true;
                    break;
                }
            }
        }
        return isCoupled;
    }

    private static boolean isResidualExistsBloc(DenseMatrix m, int i, int j) {
        double residual = Math.abs(m.get(2 * i - 2, 2 * j - 2)) + Math.abs(m.get(2 * i - 1, 2 * j - 2));
        return residual > EPS_VALUE;
    }
}
