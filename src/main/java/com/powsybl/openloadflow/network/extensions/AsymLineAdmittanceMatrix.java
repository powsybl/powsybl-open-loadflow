/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com> ,
 *                     Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.Side;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;
import com.powsybl.openloadflow.util.MatrixUtil;
import org.apache.commons.math3.complex.Complex;

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
    // [Yzpn] = [ y_11_nz y_11_np y_11_nn y_12_nz y_12_np y_12_nn ]
    //          [ y_21_zz y_21_zp y_21_zn y_22_zz y_22_zp y_22_zn ]
    //          [ y_21_pz y_21_pp y_21_pn y_22_pz y_22_pp y_22_pn ]
    //          [ y_21_nz y_21_np y_21_nn y_22_nz y_22_np y_22_nn ]

    public static final double EPS_VALUE = 0.00000001;

    private final DenseMatrix mYzpn;

    public AsymLineAdmittanceMatrix(AsymBranch asymLine) {
        if (asymLine.getYabc() != null) {
            mYzpn = update(build(asymLine.getYabc(), asymLine),
                    asymLine.isPhaseOpenA(), asymLine.isPhaseOpenB(), asymLine.isPhaseOpenC());
        } else {
            // input values are given in fortescue component, we build first Yzpn and deduce Yabc
            mYzpn = update(build(asymLine.getPiZeroComponent(), asymLine.getPiPositiveComponent(), asymLine.getPiNegativeComponent()),
                    asymLine.isPhaseOpenA(), asymLine.isPhaseOpenB(), asymLine.isPhaseOpenC());
        }

    }

    private static DenseMatrix build(ComplexMatrix yabc, AsymBranch asymLine) {

        // depending on the fortescue representation at each side, the used matrix for the equation system will vary
        boolean isSide1FortescueRepresented = asymLine.isSide1FortescueRepresentation();
        boolean isSide2FortescueRepresented = asymLine.isSide2FortescueRepresentation();

        boolean hasPhaseA1 = asymLine.isHasPhaseA1();
        boolean hasPhaseB1 = asymLine.isHasPhaseB1();
        boolean hasPhaseC1 = asymLine.isHasPhaseC1();

        boolean hasPhaseA2 = asymLine.isHasPhaseA2();
        boolean hasPhaseB2 = asymLine.isHasPhaseB2();
        boolean hasPhaseC2 = asymLine.isHasPhaseC2();

        // TODO : put a test to forbid to use fortescue variables if a phase is missing

        // we propose to decompose the building of the final matrix using 4 transformation matrices that will vary depending on the configuration
        // [I123i] = [M1] * [M2] * [Yabc] * [M3] * [M4]
        // [I123j]   [  ]   [  ]   [    ]   [  ]   [  ]
        // where
        // M1 = is a permutation matrix to match the active equations and the active variables
        // M2 = is the inverse fortescue transformation (if necessary)
        // M3 = is the fortescue transformation (if necessary)
        // M4 = is a permutation matrix to match the active equations and the active variables

        // First we build the blocs that will be used to build M1, M2, M3 and M4
        ComplexMatrix ft1 = ComplexMatrix.complexMatrixIdentity(3); // fortescue transform at side 1
        ComplexMatrix invFt1 = ComplexMatrix.complexMatrixIdentity(3); // fortescue inverse at side 1
        if (isSide1FortescueRepresented) {
            ft1 = Fortescue.createComplexMatrix(false);
            invFt1 = Fortescue.createComplexMatrix(true);
        }

        ComplexMatrix ft2 = ComplexMatrix.complexMatrixIdentity(3); // fortescue transform at side 2
        ComplexMatrix invFt2 = ComplexMatrix.complexMatrixIdentity(3); // fortescue inverse at side 2
        if (isSide2FortescueRepresented) {
            ft2 = Fortescue.createComplexMatrix(false);
            invFt2 = Fortescue.createComplexMatrix(true);
        }

        boolean hasPhaseA = hasPhaseA1 && hasPhaseA2;
        boolean hasPhaseB = hasPhaseB1 && hasPhaseB2;
        boolean hasPhaseC = hasPhaseC1 && hasPhaseC2;

        ComplexMatrix phaseId1 = getPhaseIdMatrix(hasPhaseA, hasPhaseB, hasPhaseC);
        ComplexMatrix phaseId2 = getPhaseIdMatrix(hasPhaseA, hasPhaseB, hasPhaseC);

        ComplexMatrix permutationMatrix1 = getPermutationMatrix(hasPhaseA1, hasPhaseB1, hasPhaseC1);
        ComplexMatrix permutationMatrix2 = getPermutationMatrix(hasPhaseA2, hasPhaseB2, hasPhaseC2);

        ComplexMatrix zeroBloc = new ComplexMatrix(3, 3);
        DenseMatrix zeroBlocReal = zeroBloc.getRealCartesianMatrix();

        // bloc1 M1 * M2 = transpose([permut1]) * [invft1] * [phaseId1]
        // bloc2 M1 * M2 = transpose([permut2]) * [invft2] * [phaseId2]  the inverse of a permutation matrix is its transposed
        DenseMatrix bloc1M1M2 = ComplexMatrix.getTransposed(permutationMatrix1).getRealCartesianMatrix().times(invFt1.getRealCartesianMatrix().times(phaseId1.getRealCartesianMatrix()));
        DenseMatrix bloc2M1M2 = ComplexMatrix.getTransposed(permutationMatrix2).getRealCartesianMatrix().times(invFt2.getRealCartesianMatrix().times(phaseId2.getRealCartesianMatrix()));
        // bloc1 M3 * M4 = [phaseId1] * [ft1] * [permut1]
        // bloc2 M3 * M4 = [phaseId2] * [ft2] * [permut2]   the inverse of a permutation matrix is its transposed
        DenseMatrix bloc1M3M4 = phaseId1.getRealCartesianMatrix().times(ft1.getRealCartesianMatrix().times(permutationMatrix1.getRealCartesianMatrix()));
        DenseMatrix bloc2M3M4 = phaseId2.getRealCartesianMatrix().times(ft2.getRealCartesianMatrix().times(permutationMatrix2.getRealCartesianMatrix()));

        // Delta config : if delta config, we cancel columns related to Vzero and lines related ti Izero
        ComplexMatrix cancelZeroSequence = ComplexMatrix.complexMatrixIdentity(3);
        cancelZeroSequence.set(1, 1, new Complex(0., 0.));
        DenseMatrix cancelZeroSequenceReal = cancelZeroSequence.getRealCartesianMatrix();
        if (asymLine.getSide1VariableType() == AsymBusVariableType.DELTA) {
            bloc1M1M2 = cancelZeroSequenceReal.times(bloc1M1M2);
            bloc1M3M4 = bloc1M3M4.times(cancelZeroSequenceReal);
        }

        if (asymLine.getSide2VariableType() == AsymBusVariableType.DELTA) {
            bloc2M1M2 = cancelZeroSequenceReal.times(bloc2M1M2);
            bloc2M3M4 = bloc2M3M4.times(cancelZeroSequenceReal);
        }

        DenseMatrix m1m2 = AsymThreePhaseTransfo.buildFromBlocs(bloc1M1M2, zeroBlocReal, zeroBlocReal, bloc2M1M2);
        DenseMatrix m3m4 = AsymThreePhaseTransfo.buildFromBlocs(bloc1M3M4, zeroBlocReal, zeroBlocReal, bloc2M3M4);

        return productMatrixM1M2M3(m1m2, yabc.getRealCartesianMatrix(), m3m4);
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
                if (i != j && isResidualExistsBloc(mYzpn, i, j)) {
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
        return mYzpn.get(2 * (3 * (i.getNum() - 1) + g.getNum()), 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }

    public double getY(Side i, Side j, SequenceType g, SequenceType h) {
        return mYzpn.get(2 * (3 * (i.getNum() - 1) + g.getNum()) + 1, 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }

    public static ComplexMatrix getPhaseIdMatrix(boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {
        Complex one = new Complex(1., 0.);
        ComplexMatrix phaseId = new ComplexMatrix(3, 3); // identity if phase exist, zero else
        if (hasPhaseA) {
            phaseId.set(1, 1, one);
        }
        if (hasPhaseB) {
            phaseId.set(2, 2, one);
        }
        if (hasPhaseC) {
            phaseId.set(3, 3, one);
        }
        return phaseId;
    }

    public static ComplexMatrix getPermutationMatrix(boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {

        Complex one = new Complex(1., 0.);
        ComplexMatrix permutationMatrix = new ComplexMatrix(3, 3); // depends on the missing phases

        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            permutationMatrix = ComplexMatrix.complexMatrixIdentity(3);
        } else if (!hasPhaseA && hasPhaseB && hasPhaseC) {
            // BCN config
            permutationMatrix.set(1, 3, one);
            permutationMatrix.set(2, 1, one);
            permutationMatrix.set(3, 2, one);
        } else if (hasPhaseA && !hasPhaseB && hasPhaseC) {
            // ACN config
            permutationMatrix.set(1, 1, one);
            permutationMatrix.set(2, 3, one);
            permutationMatrix.set(3, 2, one);
        } else if (hasPhaseA && hasPhaseB && !hasPhaseC) {
            // ABN config
            permutationMatrix.set(1, 1, one);
            permutationMatrix.set(2, 2, one);
            permutationMatrix.set(3, 3, one);
        } else if (hasPhaseA && !hasPhaseB && !hasPhaseC) {
            // AN config
            permutationMatrix.set(1, 2, one);
        } else if (!hasPhaseA && hasPhaseB && !hasPhaseC) {
            // BN config
            permutationMatrix.set(2, 2, one);
        } else if (!hasPhaseA && !hasPhaseB && hasPhaseC) {
            // CN config
            permutationMatrix.set(3, 2, one);
        } else {
            throw new IllegalStateException("unknown phase config");
        }

        return permutationMatrix;
    }
}
