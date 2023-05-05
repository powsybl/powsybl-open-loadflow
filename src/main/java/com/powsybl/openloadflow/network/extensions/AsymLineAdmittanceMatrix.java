package com.powsybl.openloadflow.network.extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;
import com.powsybl.openloadflow.util.MatrixUtil;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class AsymLineAdmittanceMatrix {

    // This class is made to build and access the admittance terms that will be used to fill up the Jacobian :
    // The following formulation approach is used :
    //                                        side 1   ________     side 2
    // [ Iz_1 ]             [ Vz_1 ]            z-----|        |-------z
    // [ Ip_1 ]             [ Vp_1 ]            p-----|  Yzpn  |-------p
    // [ In_1 ]             [ Vn_1 ]            n-----|________|-------n
    // [ Iz_2 ] = [Yzpn] *  [ Vz_2 ]
    // [ Ip_2 ]             [ Vp_2 ]
    // [ In_2 ]             [ Vn_2 ]
    //
    // Given that at bus 1 where j is one neighbouring bus, the injection at bus 1 is equal to the sum of Powers from neighboring busses:
    // Sum[j](S_1j) =Pp_1 + j.Qp_1 = Sum[j](Vp_1.Ip_1j*)
    //               Pz_1 + j.Qz_1 = Sum[j](Vz_1.Iz_1j*)
    //               Pn_1 + j.Qn_1 = Sum[j](Vn_1.In_1j*)
    //
    // Substituting [I] by [Yzpn]*[V] allows to know the equations terms that will fill the jacobian matrix
    //
    // Step 1: Get [Yzpn]
    // ------------------
    // First step is to compute [ Yzpn ] from a 3-phase description because this is how we can describe unbalances of phases for a line:
    // For each a,b,c phase we know the following relation (only true for lines with no mutual inductances, otherwise we must handle full [Yabc] matrix):
    // [Ia_1]   [ ya_11 ya_12 ]   [Va_1]
    // [Ia_2] = [ ya_21 ya_22 ] * [Va_2]
    //            with (for a line only)  :  ya_11 = ga1 + j.ba1 + 1/za   , ya_12 = -1/za   ,   ya_21 = -1/za   ,  ya_22 = ga2 + j.ba2 + 1/za
    //
    // From the fortescue transformation we have:
    // [Ga]         [Gz]
    // [Gb] = [F] * [Gp]
    // [Gc]         [Gn]
    //     where [G] might be [V] or [I]
    //     where [F] is the fortescue transformation matrix
    //
    // Therefore we have:
    //                           [ya_11  0    0  ya_12  0   0  ]
    //                           [  0  yb_11  0    0  yb_12 0  ]
    //          [inv(F)   O  ]   [  0   0   yc_11  0   0  yc_12]   [ F  0 ]
    // [Yzpn] = [  0   inv(F)] * [ya_21  0    0  ya_22  0   0  ] * [ 0  F ]
    //                           [  0  yb_21  0    0  yb_22 0  ]
    //                           [  0   0   yc_21  0   0  yc_22]
    //
    // [Yzpn] is a complex matrix
    //
    // Step 2: Define the generic term that will be used to make the link between [Yzpn] and S[z,p,n] the apparent power
    // -----------------------------------------------------------------------------------------------------------------
    // We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
    //    where i,j are line's ends included in {1,2}
    //    where g,h are fortescue sequences included in {z,p,n}={0,1,2}
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
    // S_z_12 = T(1,1,z,z) + T(1,1,z,p) + T(1,1,z,n) + T(1,2,z,z) + T(1,2,z,p) + T(1,2,z,n)
    // S_p_12 = T(1,1,p,z) + T(1,1,p,p) + T(1,1,p,n) + T(1,2,p,z) + T(1,2,p,p) + T(1,2,p,n)
    // S_n_12 = T(1,1,n,z) + T(1,1,n,p) + T(1,1,n,n) + T(1,2,n,z) + T(1,2,n,p) + T(1,2,n,n)
    //
    // Step 5 : make the link between y_ij_gh in T() and [Yzpn]:
    // ---------------------------------------------------------
    // By construction we have :
    //          [ y_11_zz y_11_zp y_11_zn y_12_zz y_12_zp y_12_zn ]
    //          [ y_11_pz y_11_pp y_11_pn y_12_pz y_12_pp y_12_pn ]
    // [Y012] = [ y_11_nz y_11_np y_11_nn y_12_nz y_12_np y_12_nn ]
    //          [ y_21_zz y_21_zp y_21_zn y_22_zz y_22_zp y_22_zn ]
    //          [ y_21_pz y_21_pp y_21_pn y_22_pz y_22_pp y_22_pn ]
    //          [ y_21_nz y_21_np y_21_nn y_22_nz y_22_np y_22_nn ]

    public static final double EPS_VALUE = 0.00000001;

    private final DenseMatrix mY012;

    public AsymLineAdmittanceMatrix(AsymLine asymLine) {
        // input values are given in fortescue component, we build first Yzpn and deduce Yabc
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

        //bloc yz11
        add22Bloc(g12z + g1z, b12z + b1z, 1, 1, mY);
        //bloc yz12
        add22Bloc(-g12z, -b12z, 1, 4, mY);
        //bloc yp11
        add22Bloc(g12p + g1p, b12p + b1p, 2, 2, mY);
        //bloc yp12
        add22Bloc(-g12p, -b12p, 2, 5, mY);
        //bloc yn11
        add22Bloc(g12n + g1n, b12n + b1n, 3, 3, mY);
        //bloc yn12
        add22Bloc(-g12n, -b12n, 3, 6, mY);

        //bloc yz22
        add22Bloc(g21z + g2z, b21z + b2z, 4, 4, mY);
        //bloc yz21
        add22Bloc(-g21z, -b21z, 4, 1, mY);
        //bloc yp22
        add22Bloc(g21p + g2p, b21p + b2p, 5, 5, mY);
        //bloc yp21
        add22Bloc(-g21p, -b21p, 5, 2, mY);
        //bloc yn22
        add22Bloc(g21n + g2n, b21n + b2n, 6, 6, mY);
        //bloc yn21
        add22Bloc(-g21n, -b21n, 6, 3, mY);

        return mY;
    }

    private static DenseMatrix update(DenseMatrix mYzpn, boolean phaseOpenA, boolean phaseOpenB, boolean phaseOpenC) {
        // if one phase or more are disconnected we need to update Yabc and then Y012
        if (phaseOpenA || phaseOpenB || phaseOpenC) {
            var mYabc = productMatrixM1M2M3(buildTwoBlocsMatrix(Fortescue.createMatrix()),
                                            mYzpn,
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
        return mYzpn;
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

    public double getX(Side i, Side j, SequenceType g, SequenceType h) {
        return mY012.get(2 * (3 * (i.getNum() - 1) + g.getNum()), 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }

    public double getY(Side i, Side j, SequenceType g, SequenceType h) {
        return mY012.get(2 * (3 * (i.getNum() - 1) + g.getNum()) + 1, 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }
}
