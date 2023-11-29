/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.extensions.AsymBusVariableType;
import com.powsybl.openloadflow.network.extensions.AsymThreePhaseTransfo;
import com.powsybl.openloadflow.util.ComplexMatrix;
import com.powsybl.openloadflow.util.Fortescue;
import com.powsybl.openloadflow.util.Fortescue.SequenceType;
import com.powsybl.openloadflow.util.MatrixUtil;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.Pair;

/**
 * This class is made to build and access the admittance terms that will be used to fill up the Jacobian :
 *  The following formulation approach is used :
 *                                         side 1   ________     side 2
 *  [ Iz_1 ]             [ Vz_1 ]            z-----|        |-------z
 *  [ Ip_1 ]             [ Vp_1 ]            p-----|  Yzpn  |-------p
 *  [ In_1 ]             [ Vn_1 ]            n-----|________|-------n
 *  [ Iz_2 ] = [Yzpn] *  [ Vz_2 ]
 *  [ Ip_2 ]             [ Vp_2 ]
 *  [ In_2 ]             [ Vn_2 ]
 *
 *  Given that at bus 1 where j is one neighbouring bus, the injection at bus 1 is equal to the sum of Powers from neighboring busses:
 *  Sum[j](S_1j) =Pp_1 + j.Qp_1 = Sum[j](Vp_1.Ip_1j*)
 *                Pz_1 + j.Qz_1 = Sum[j](Vz_1.Iz_1j*)
 *                Pn_1 + j.Qn_1 = Sum[j](Vn_1.In_1j*)
 *
 *  Substituting [I] by [Yzpn]*[V] allows to know the equations terms that will fill the jacobian matrix
 *
 *  Step 1: Get [Yzpn]
 *  ------------------
 *  First step is to compute [ Yzpn ] from a 3-phase description because this is how we can describe unbalances of phases for a line:
 *  For each a,b,c phase we know the following relation (only true for lines with no mutual inductances, otherwise we must handle full [Yabc] matrix):
 *  [Ia_1]   [ ya_11 ya_12 ]   [Va_1]
 *  [Ia_2] = [ ya_21 ya_22 ] * [Va_2]
 *             with (for a line only)  :  ya_11 = ga1 + j.ba1 + 1/za   , ya_12 = -1/za   ,   ya_21 = -1/za   ,  ya_22 = ga2 + j.ba2 + 1/za
 *
 *  From the fortescue transformation we have:
 *  [Ga]         [Gz]
 *  [Gb] = [F] * [Gp]
 *  [Gc]         [Gn]
 *      where [G] might be [V] or [I]
 *      where [F] is the fortescue transformation matrix
 *
 *  Therefore we have:
 *                            [ya_11  0    0  ya_12  0   0  ]
 *                            [  0  yb_11  0    0  yb_12 0  ]
 *           [inv(F)   O  ]   [  0   0   yc_11  0   0  yc_12]   [ F  0 ]
 *  [Yzpn] = [  0   inv(F)] * [ya_21  0    0  ya_22  0   0  ] * [ 0  F ]
 *                            [  0  yb_21  0    0  yb_22 0  ]
 *                            [  0   0   yc_21  0   0  yc_22]
 *
 *  [Yzpn] is a complex matrix
 *
 *  Step 2: Define the generic term that will be used to make the link between [Yzpn] and S[z,p,n] the apparent power
 *  -----------------------------------------------------------------------------------------------------------------
 *  We define T(i,j,g,h) = rho_i * rho_j * exp(j(a_i-a_j)) * y*_ij_gh * V_gi * V*_hj
 *     where i,j are line's ends included in {1,2}
 *     where g,h are fortescue sequences included in {z,p,n}={0,1,2}
 *
 *
 *  Step 3 : express the expanded value of T(i,j,g,h):
 *  --------------------------------------------------
 *  T(i,j,g,h) =     rho_i * rho_j * V_gi * V_hj * yx_ij_gh * cos(a_i - a_j + th_gi - th_hj)
 *                 - rho_i * rho_j * V_gi * V_hj * yy_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *              -j(  rho_i * rho_j * V_gi * V_hj * yx_ij_gh * sin(a_i - a_j + th_gi - th_hj)
 *                 + rho_i * rho_j * V_gi * V_hj * yy_ij_gh * cos(a_i - a_j + th_gi - th_hj) )
 *
 *  Step 4 : express the apparent powers with T():
 *  ----------------------------------------------
 *  S_z_12 = T(1,1,z,z) + T(1,1,z,p) + T(1,1,z,n) + T(1,2,z,z) + T(1,2,z,p) + T(1,2,z,n)
 *  S_p_12 = T(1,1,p,z) + T(1,1,p,p) + T(1,1,p,n) + T(1,2,p,z) + T(1,2,p,p) + T(1,2,p,n)
 *  S_n_12 = T(1,1,n,z) + T(1,1,n,p) + T(1,1,n,n) + T(1,2,n,z) + T(1,2,n,p) + T(1,2,n,n)
 *
 *  Step 5 : make the link between y_ij_gh in T() and [Yzpn]:
 *  ---------------------------------------------------------
 *  By construction we have :
 *           [ y_11_zz y_11_zp y_11_zn y_12_zz y_12_zp y_12_zn ]
 *           [ y_11_pz y_11_pp y_11_pn y_12_pz y_12_pp y_12_pn ]
 *  [Yzpn] = [ y_11_nz y_11_np y_11_nn y_12_nz y_12_np y_12_nn ]
 *           [ y_21_zz y_21_zp y_21_zn y_22_zz y_22_zp y_22_zn ]
 *           [ y_21_pz y_21_pp y_21_pn y_22_pz y_22_pp y_22_pn ]
 *           [ y_21_nz y_21_np y_21_nn y_22_nz y_22_np y_22_nn ]
 *
 * @author Jean-Baptiste Heyberger {@literal <jbheyberger at gmail.com>}
 */
public class LfAsymLineAdmittanceMatrix {

    public static final double EPS_VALUE = 0.00000001;

    private final DenseMatrix mYzpn;

    public LfAsymLineAdmittanceMatrix(LfAsymLine asymLine) {
        if (asymLine.getYabc() != null) {
            mYzpn = update(build(asymLine.getYabc(), asymLine),
                    asymLine.isPhaseOpenA(), asymLine.isPhaseOpenB(), asymLine.isPhaseOpenC());
        } else {
            // input values are given in fortescue component, we build first Yzpn and deduce Yabc
            mYzpn = update(build(asymLine.getPiZeroComponent(), asymLine.getPiPositiveComponent(), asymLine.getPiNegativeComponent()),
                    asymLine.isPhaseOpenA(), asymLine.isPhaseOpenB(), asymLine.isPhaseOpenC());
        }
    }

    private static DenseMatrix build(ComplexMatrix yabc, LfAsymLine asymLine) {

        // depending on the fortescue representation at each side, the used matrix for the equation system will vary

        // we propose to decompose the building of the final matrix using 4 transformation matrices that will vary depending on the configuration
        // [I123i] = [M1] * [M2] * [Yabc] * [M3] * [M4]
        // [I123j]   [  ]   [  ]   [    ]   [  ]   [  ]
        // where
        // M1 = is a permutation matrix to match the active equations and the active variables
        // M2 = is the inverse fortescue transformation (if necessary)
        // M3 = is the fortescue transformation (if necessary)
        // M4 = is a permutation matrix to match the active equations and the active variables

        Pair<DenseMatrix, DenseMatrix> blocs1M1M2AndM3M4 = getBlocsM1M2AndM3M4(asymLine, asymLine.isSide1FortescueRepresentation(), asymLine.getSide1VariableType(), asymLine.isHasPhaseA1(), asymLine.isHasPhaseB1(), asymLine.isHasPhaseC1());
        Pair<DenseMatrix, DenseMatrix> blocs2M1M2AndM3M4 = getBlocsM1M2AndM3M4(asymLine, asymLine.isSide2FortescueRepresentation(), asymLine.getSide2VariableType(), asymLine.isHasPhaseA2(), asymLine.isHasPhaseB2(), asymLine.isHasPhaseC2());

        ComplexMatrix zeroBloc = new ComplexMatrix(3, 3);
        DenseMatrix zeroBlocReal = zeroBloc.toRealCartesianMatrix();
        DenseMatrix m1m2 = AsymThreePhaseTransfo.buildFromBlocs(blocs1M1M2AndM3M4.getFirst(), zeroBlocReal, zeroBlocReal, blocs2M1M2AndM3M4.getFirst());
        DenseMatrix m3m4 = AsymThreePhaseTransfo.buildFromBlocs(blocs1M1M2AndM3M4.getSecond(), zeroBlocReal, zeroBlocReal, blocs2M1M2AndM3M4.getSecond());

        return productMatrixM1M2M3(m1m2, yabc.toRealCartesianMatrix(), m3m4);
    }

    private static Pair<DenseMatrix, DenseMatrix> getBlocsM1M2AndM3M4(LfAsymLine asymLine, boolean isSideFortescueRepresented, AsymBusVariableType asymBusVariableType, boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {

        boolean hasLinePhaseA = asymLine.isHasPhaseA1() && asymLine.isHasPhaseA2();
        boolean hasLinePhaseB = asymLine.isHasPhaseB1() && asymLine.isHasPhaseB2();
        boolean hasLinePhaseC = asymLine.isHasPhaseC1() && asymLine.isHasPhaseC2();

        if (isSideFortescueRepresented && (!hasPhaseA || !hasPhaseB || !hasPhaseC)) {
            throw new IllegalStateException("Fortescue representation and missing phase not yet handled for branches ");
        }

        // First we build the blocs that will be used to build M1, M2, M3 and M4
        ComplexMatrix ft = ComplexMatrix.createIdentity(3); // fortescue transform at side 1
        ComplexMatrix invFt = ComplexMatrix.createIdentity(3); // fortescue inverse at side 1
        if (isSideFortescueRepresented) {
            ft = Fortescue.createComplexMatrix(false);
            invFt = Fortescue.createComplexMatrix(true);
        }

        ComplexMatrix phaseId1 = getPhaseIdMatrix(hasLinePhaseA, hasLinePhaseB, hasLinePhaseC);
        ComplexMatrix permutationMatrix = getPermutationMatrix(hasPhaseA, hasPhaseB, hasPhaseC);

        // bloc1 M1 * M2 = transpose([permut]) * [invft] * [phaseId]
        DenseMatrix blocM1M2 = permutationMatrix.transpose().toRealCartesianMatrix().times(invFt.toRealCartesianMatrix().times(phaseId1.toRealCartesianMatrix()));
        // bloc1 M3 * M4 = [phaseId] * [ft] * [permut]
        DenseMatrix blocM3M4 = phaseId1.toRealCartesianMatrix().times(ft.toRealCartesianMatrix().times(permutationMatrix.toRealCartesianMatrix()));

        // Delta config : if delta config, we cancel columns related to Vzero and lines related ti Izero
        ComplexMatrix cancelZeroSequence = ComplexMatrix.createIdentity(3);
        cancelZeroSequence.set(1, 1, Complex.ZERO);
        DenseMatrix cancelZeroSequenceReal = cancelZeroSequence.toRealCartesianMatrix();
        if (asymBusVariableType == AsymBusVariableType.DELTA) {
            blocM1M2 = cancelZeroSequenceReal.times(blocM1M2);
            blocM3M4 = blocM3M4.times(cancelZeroSequenceReal);
        }

        return new Pair<>(blocM1M2, blocM3M4);
    }

    private static DenseMatrix build(SimplePiModel piZeroComponent, SimplePiModel piPositiveComponent, SimplePiModel piNegativeComponent) {

        Complex zz = new Complex(piZeroComponent.getR(), piZeroComponent.getX());
        Complex y1z = new Complex(piZeroComponent.getG1(), piZeroComponent.getB1());
        Complex y2z = new Complex(piZeroComponent.getG2(), piZeroComponent.getB2());
        Complex y12z = zz.reciprocal();

        Complex zp = new Complex(piPositiveComponent.getR(), piPositiveComponent.getX());
        Complex y1p = new Complex(piPositiveComponent.getG1(), piPositiveComponent.getB1());
        Complex y2p = new Complex(piPositiveComponent.getG2(), piPositiveComponent.getB2());
        Complex y12p = zp.reciprocal();

        Complex zn = new Complex(piNegativeComponent.getR(), piNegativeComponent.getX());
        Complex y1n = new Complex(piNegativeComponent.getG1(), piNegativeComponent.getB1());
        Complex y2n = new Complex(piNegativeComponent.getG2(), piNegativeComponent.getB2());
        Complex y12n = zn.reciprocal();

        ComplexMatrix mYc = new ComplexMatrix(6, 6);
        mYc.set(1, 1, y12z.add(y1z));
        mYc.set(1, 4, y12z.multiply(-1));
        mYc.set(2, 2, y12p.add(y1p));
        mYc.set(2, 5, y12p.multiply(-1));
        mYc.set(3, 3, y12n.add(y1n));
        mYc.set(3, 6, y12n.multiply(-1));

        mYc.set(4, 4, y12z.add(y2z));
        mYc.set(4, 1, y12z.multiply(-1));
        mYc.set(5, 5, y12p.add(y2p));
        mYc.set(5, 2, y12p.multiply(-1));
        mYc.set(6, 6, y12n.add(y2n));
        mYc.set(6, 3, y12n.multiply(-1));

        return mYc.toRealCartesianMatrix();
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

    public double getX(TwoSides i, TwoSides j, SequenceType g, SequenceType h) {
        return mYzpn.get(2 * (3 * (i.getNum() - 1) + g.getNum()), 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }

    public double getY(TwoSides i, TwoSides j, SequenceType g, SequenceType h) {
        return mYzpn.get(2 * (3 * (i.getNum() - 1) + g.getNum()) + 1, 2 * (3 * (j.getNum() - 1) + h.getNum()));
    }

    public static ComplexMatrix getPhaseIdMatrix(boolean hasPhaseA, boolean hasPhaseB, boolean hasPhaseC) {
        Complex one = Complex.ONE;
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

        Complex one = Complex.ONE;
        ComplexMatrix permutationMatrix = new ComplexMatrix(3, 3); // depends on the missing phases

        if (hasPhaseA && hasPhaseB && hasPhaseC) {
            permutationMatrix = ComplexMatrix.createIdentity(3);
        } else if (hasPhaseB && hasPhaseC) {
            // BCN config
            permutationMatrix.set(1, 3, one);
            permutationMatrix.set(2, 1, one);
            permutationMatrix.set(3, 2, one);
        } else if (hasPhaseA && hasPhaseC) {
            // ACN config
            permutationMatrix.set(1, 1, one);
            permutationMatrix.set(2, 3, one);
            permutationMatrix.set(3, 2, one);
        } else if (hasPhaseA && hasPhaseB) {
            // ABN config
            permutationMatrix.set(1, 1, one);
            permutationMatrix.set(2, 2, one);
            permutationMatrix.set(3, 3, one);
        } else if (hasPhaseA) {
            // AN config
            permutationMatrix.set(1, 2, one);
        } else if (hasPhaseB) {
            // BN config
            permutationMatrix.set(2, 2, one);
        } else if (hasPhaseC) {
            // CN config
            permutationMatrix.set(3, 2, one);
        } else {
            throw new IllegalStateException("unknown phase config");
        }

        return permutationMatrix;
    }
}
