package com.powsybl.openloadflow.network.extensions;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.util.ComplexMatrix;
import org.apache.commons.math3.complex.Complex;

import java.util.List;

public class AsymThreePhaseTransfo {

    public static final String PROPERTY_ASYMMETRICAL = "ThreePhase";

    private final Complex rho; // rho expressed at side 1 of each single phase transformer

    private final ComplexMatrix ya;
    private final ComplexMatrix yb;
    private final ComplexMatrix yc;

    private final ComplexMatrix yp11;
    private final ComplexMatrix yp12;
    private final ComplexMatrix yp21;
    private final ComplexMatrix yp22;

    private final ComplexMatrix mZg1;
    private final boolean isVo1Zero;

    private final ComplexMatrix mZg2;
    private final boolean isVo2Zero;

    private final DenseMatrix yabc;

    public AsymThreePhaseTransfo(LegConnectionType leg1ConnectionType, LegConnectionType leg2ConnectionType, StepType stepType,
                                 ComplexMatrix ya, ComplexMatrix yb, ComplexMatrix yc, Complex rho, Complex zG1, Complex zG2,
                                 List<Boolean> phaseConnections) {
        this.rho = rho;
        this.ya = ya;
        this.yb = yb;
        this.yc = yc;

        // Building terms of the fixed system:
        // [I'abc1] = [ y'11  y'12 ] [V'abc1]
        // [I'abc2]   [ y'21  y'22 ] [V'abc1]
        this.yp11 = buildFixedSystemTermYij(1, 1);
        this.yp12 = buildFixedSystemTermYij(1, 2);
        this.yp21 = buildFixedSystemTermYij(2, 1);
        this.yp22 = buildFixedSystemTermYij(2, 2);

        ComplexMatrix c1I = ComplexMatrix.complexMatrixIdentity(3);
        ComplexMatrix c2I = ComplexMatrix.complexMatrixIdentity(3);

        // Building terms of the variable system
        switch (leg1ConnectionType) {
            case DELTA:
                mZg1 = new ComplexMatrix(3, 3);
                isVo1Zero = true;
                c1I = complexMatrixP(true);
                break;

            case Y_GROUNDED:
                mZg1 = ComplexMatrix.getMatrixScaled(complexMatrixFull(), zG1);
                isVo1Zero = true;
                break;

            case Y:
                mZg1 = new ComplexMatrix(3, 3);
                isVo1Zero = false;
                break;

            default:
                throw new IllegalStateException("Unknown leg1 type : ");
        }

        switch (leg2ConnectionType) {
            case DELTA:
                mZg2 = new ComplexMatrix(3, 3);
                isVo2Zero = true;
                c2I = complexMatrixP(true);
                break;

            case Y_GROUNDED:
                mZg2 = ComplexMatrix.getMatrixScaled(complexMatrixFull(), zG2);
                isVo2Zero = true;
                break;

            case Y:
                mZg2 = new ComplexMatrix(3, 3);
                isVo2Zero = false;
                break;

            default:
                throw new IllegalStateException("Unknown leg2 type : ");
        }

        ComplexMatrix c1V = ComplexMatrix.complexMatrixIdentity(3);
        ComplexMatrix c2V = ComplexMatrix.complexMatrixIdentity(3);

        if (stepType == StepType.STEP_DOWN && ((leg2ConnectionType == LegConnectionType.Y_GROUNDED || leg2ConnectionType == LegConnectionType.Y)
                && leg1ConnectionType == LegConnectionType.DELTA)) {
            c1I = complexMatrixP(false);
            c1V = new ComplexMatrix(3, 3);
            Complex mOne = new Complex(-1., 0.);
            c1V.set(1, 3, mOne);
            c1V.set(2, 1, mOne);
            c1V.set(3, 2, mOne);
        } else if (stepType == StepType.STEP_UP && ((leg1ConnectionType == LegConnectionType.Y_GROUNDED || leg1ConnectionType == LegConnectionType.Y)
                && leg2ConnectionType == LegConnectionType.DELTA)) {
            c2I = complexMatrixP(false);
            c2V = new ComplexMatrix(3, 3);
            Complex mOne = new Complex(-1., 0.);
            c2V.set(1, 3, mOne);
            c2V.set(2, 1, mOne);
            c2V.set(3, 2, mOne);
        }

        // Step 1: Computation of matrices after voltage substitution in the fixed system
        // [I'abc1] = inv([Id]-[y'11][Zg1]).( [y'11].[c1].[Vabc1] + [y'11].[1;1;1].vo1 +
        //                                    [y'12].[c2].[Vabc2] + [y'12].[1;1;1].vo2 + [y'12].[Zg2].[I'abc2])

        // [I'abc2] = inv([Id]-[y'22][Zg2]).( [y'22].[c2].[Vabc2] + [y'22].[1;1;1].vo2 +
        //                                    [y'21].[c1].[Vabc1] + [y'21].[1;1;1].vo1 + [y'21].[Zg1].[I'abc1])

        // Equivalent to:
        // [I'abc1] = [M1].[Vabc1] + [M2].[Vabc2] + [M3].[I'abc2] + [M4].vo1 + [M5].vo2
        // [I'abc2] = [M6].[Vabc1] + [M7].[Vabc2] + [M8].[I'abc1] + [M9].vo1 + [M10].vo2

        DenseMatrix b1 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix(); // second member for matrix inversion

        DenseMatrix mId3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix();
        DenseMatrix yinv1 = mId3.add(yp11.getRealCartesianMatrix().times(mZg1.getRealCartesianMatrix()), 1., -1.); // matrix to be inverted
        yinv1.decomposeLU().solve(b1);

        DenseMatrix m1 = b1.times(yp11.getRealCartesianMatrix().times(c1V.getRealCartesianMatrix()));
        DenseMatrix m2 = b1.times(yp12.getRealCartesianMatrix().times(c2V.getRealCartesianMatrix()));
        DenseMatrix m3 = b1.times(yp12.getRealCartesianMatrix().times(mZg2.getRealCartesianMatrix()));
        DenseMatrix m4 = b1.times(yp11.getRealCartesianMatrix().times(getFullMinusVector3().getRealCartesianMatrix()));
        DenseMatrix m5 = b1.times(yp12.getRealCartesianMatrix().times(getFullMinusVector3().getRealCartesianMatrix()));

        DenseMatrix yinv2 = mId3.add(yp22.getRealCartesianMatrix().times(mZg2.getRealCartesianMatrix()), 1., -1.); // matrix to be inverted
        DenseMatrix b2 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix(); // second member for matrix inversion
        yinv2.decomposeLU().solve(b2);

        DenseMatrix m6 = b2.times(yp21.getRealCartesianMatrix().times(c1V.getRealCartesianMatrix()));
        DenseMatrix m7 = b2.times(yp22.getRealCartesianMatrix().times(c2V.getRealCartesianMatrix()));
        DenseMatrix m8 = b2.times(yp21.getRealCartesianMatrix().times(mZg1.getRealCartesianMatrix()));
        DenseMatrix m9 = b2.times(yp21.getRealCartesianMatrix().times(getFullMinusVector3().getRealCartesianMatrix()));
        DenseMatrix m10 = b2.times(yp22.getRealCartesianMatrix().times(getFullMinusVector3().getRealCartesianMatrix()));

        // step 2: computation of matrices after I' substitution
        // [I'abc1] = inv([Id]-[M3].[M8]).([M1].[Vabc1] + [M2].[Vabc2] + [M3].([M6].[Vabc1] + [M7].[Vabc2] + [M9].vo1 + [M10].vo2) + [M4].vo1 + [M5].vo2)
        // Leading to:
        // [I'abc1] = [M11].[Vabc1] + [M12].[Vabc2] + [M13].vo1 + [M14].vo2

        DenseMatrix yinv3 = mId3.add(m3.times(m8), 1., -1.); // matrix to be inverted
        DenseMatrix b3 = ComplexMatrix.complexMatrixIdentity(3).getRealCartesianMatrix(); // second member for matrix inversion
        yinv3.decomposeLU().solve(b3);

        DenseMatrix m11 = b3.times(m1.add(m3.times(m6))).toDense();
        DenseMatrix m12 = b3.times(m2.add(m3.times(m7))).toDense();
        DenseMatrix m13 = b3.times(m4.add(m3.times(m9))).toDense();
        DenseMatrix m14 = b3.times(m5.add(m3.times(m10))).toDense();

        // [I'abc2] = [M6].[Vabc1] + [M7].[Vabc2] + [M8].([M11].[Vabc1] + [M12].[Vabc2] + [M13].vo1 + [M14].vo2) + [M9].vo1 + [M10].vo2
        // Leading to:
        // [I'abc2] = [M15].[Vabc1] + [M16].[Vabc2] + [M17].vo1 + [M18].vo2

        DenseMatrix m15 = m6.add(m8.times(m11)).toDense();
        DenseMatrix m16 = m7.add(m8.times(m12)).toDense();
        DenseMatrix m17 = m9.add(m8.times(m13)).toDense();
        DenseMatrix m18 = m10.add(m8.times(m14)).toDense();

        // step 3: integration of vo1 and vo2 in the admittance system
        // Case 1 :  where vo1 and vo2 are not zero:
        // we use the relations :  [1 1 1].[I'abc1] = 0  and  [1 1 1].[I'abc2] = 0
        // from the last computed matrices we obtain the two complex equations:
        //
        // 0 = [Ma].[Vabc1] + [Mb].[Vabc2] + mc.vo1 + md.vo2
        // 0 = [Me].[Vabc1] + [Mf].[Vabc2] + mg.vo1 + mh.vo2
        //
        // this can be rewritten as:
        //
        // [vo1] = -inv([mc md]).[ [Ma] [Mb] ].[ [Vabc1] ]   and given [I"abc1] = [M13].vo1 + [M14].vo2     are the terms to be replaced
        // [vo2]        [mg mh]  [ [Me] [Mf] ] [ [Vabc2] ]         and [I"abc2] = [M17].vo1 + [M18].vo2
        //
        // We have:
        // [I"abc1] = - [ [M13] [M14] ].inv([mc md]).[ [Ma] [Mb] ].[ [Vabc1] ] which give the total admittance to be added to the previous equation system
        // [I"abc2]     [ [M17] [M18] ]     [mg mh]  [ [Me] [Mf] ] [ [Vabc2] ]
        //
        // Case 2 : vo2 = 0
        // then vo1 = -1/mc.([Ma] [Mb]).[ [Vabc1] ]
        //                              [ [Vabc2] ]
        // which gives:
        // [I"abc1] = -  [ [M13] [M14] ].[ 1/mc  0 ].[ [Ma] [Mb] ].[ [Vabc1] ] which give the total admittance to be added to the previous equation system
        // [I"abc2]      [ [M17] [M18] ] [ 0     0 ] [ [Me] [Mf] ] [ [Vabc2] ]
        //
        // Case 3 : vo1 = 0
        // then vo2 = -1/mh.([Me] [Mf]).[ [Vabc1] ]
        //                              [ [Vabc2] ]
        // which gives:
        // [I"abc1] = - [ [M13] [M14] ].[ 0    0  ].[ [Ma] [Mb] ].[ [Vabc1] ] which give the total admittance [Y"abc] to be added to the previous equation system
        // [I"abc2]     [ [M17] [M18] ] [ 0  1/mh ] [ [Me] [Mf] ] [ [Vabc2] ]

        DenseMatrix fullRowReal = getFullRowVector3().getRealCartesianMatrix();

        DenseMatrix ma = fullRowReal.times(m11);
        DenseMatrix mb = fullRowReal.times(m12);
        DenseMatrix mc = fullRowReal.times(m13);
        DenseMatrix md = fullRowReal.times(m14);

        DenseMatrix me = fullRowReal.times(m15);
        DenseMatrix mf = fullRowReal.times(m16);
        DenseMatrix mg = fullRowReal.times(m17);
        DenseMatrix mh = fullRowReal.times(m18);

        DenseMatrix m19 = buildFromBlocs(m13, m14, m17, m18);
        DenseMatrix m20 = buildFromBlocs(ma, mb, me, mf);
        DenseMatrix inverse;
        if (!isVo1Zero && !isVo2Zero) {
            DenseMatrix inv4 = buildFromBlocs(mc, md, mg, mh);
            DenseMatrix b4 = ComplexMatrix.complexMatrixIdentity(2).getRealCartesianMatrix(); // second member for matrix inversion
            inv4.decomposeLU().solve(b4);
            inverse = b4;
        } else if (!isVo1Zero) {
            Complex mcComplex = new Complex(mc.get(0, 0), mc.get(1, 0));
            ComplexMatrix invmc = new ComplexMatrix(2, 2);
            invmc.set(1, 1, mcComplex.reciprocal());
            inverse = invmc.getRealCartesianMatrix();
        } else if (!isVo2Zero) {
            Complex mhComplex = new Complex(mh.get(0, 0), mh.get(1, 0));
            ComplexMatrix invmh = new ComplexMatrix(2, 2);
            invmh.set(2, 2, mhComplex.reciprocal());
            inverse = invmh.getRealCartesianMatrix();
        } else {
            // vo1 and vo2 are zero
            inverse = new ComplexMatrix(2, 2).getRealCartesianMatrix();
        }

        DenseMatrix yppabc = m19.times(inverse, -1).times(m20).toDense();
        DenseMatrix ypabc = buildFromBlocs(m11, m12, m15, m16);

        // the final admittance matrix [Yabc] of the three phase transformer must verify:
        //
        // [Iabc1] = [ t[c1]   0   ].[I'abc1] = [ t[c1]   0   ]. ( [Y'abc] + [Y"abc] ).[I'abc1] = [Yabc].[I'abc1]
        // [Iabc2]   [   0   t[c2] ] [I'abc2]   [   0   t[c2] ]                        [I'abc2]          [I'abc2]

        DenseMatrix zeroBloc = new ComplexMatrix(3, 3).getRealCartesianMatrix();
        ComplexMatrix tc1 = ComplexMatrix.getTransposed(c1I);
        ComplexMatrix tc2 = ComplexMatrix.getTransposed(c2I);
        DenseMatrix tc1tc2 = buildFromBlocs(tc1.getRealCartesianMatrix(), zeroBloc, zeroBloc, tc2.getRealCartesianMatrix());
        DenseMatrix yabcTmp = tc1tc2.times(ypabc.add(yppabc)).toDense();

        // step 4: handling a phase disconnection if necessary:
        // for now we implement only one phase disconnection
        int numDisconnection = 0;
        for (int i = 0; i < phaseConnections.size(); i++) {
            if (phaseConnections.get(i).equals(Boolean.FALSE)) {
                numDisconnection = i + 1;
                break;
            }
        }

        if (numDisconnection > 0) {
            ComplexMatrix disconnectionMatrix = ComplexMatrix.complexMatrixIdentity(6);
            ComplexMatrix complexYabc = ComplexMatrix.getComplexMatrixFromRealCartesian(yabcTmp);
            Complex diagTerm = complexYabc.getTerm(numDisconnection, numDisconnection);
            for (int j = 1; j <= 6; j++) {
                if (j != numDisconnection) {
                    Complex term = complexYabc.getTerm(numDisconnection, j).multiply(-1).divide(diagTerm);
                    disconnectionMatrix.set(numDisconnection, j, term);
                } else {
                    disconnectionMatrix.set(j, j, new Complex(0., 0.));
                }
            }
            // then multiply the disconnection matrix by [Yabc] to get the final matrix [Yabc]
            yabcTmp = yabcTmp.times(disconnectionMatrix.getRealCartesianMatrix());
        }

        this.yabc = yabcTmp;
    }

    public DenseMatrix getYabc() {
        return yabc;
    }

    private ComplexMatrix buildFixedSystemTermYij(int i, int j) {
        ComplexMatrix yij = new ComplexMatrix(3, 3);
        Complex ri = rho.conjugate();
        Complex rj = rho;
        Complex one = new Complex(1., 0);

        if (i == 2) {
            ri = one;
        }

        if (j == 2) {
            rj = one;
        }

        yij.set(1, 1, ya.getTerm(i, j).multiply(ri.multiply(rj)));
        yij.set(2, 2, yb.getTerm(i, j).multiply(ri.multiply(rj)));
        yij.set(3, 3, yc.getTerm(i, j).multiply(ri.multiply(rj)));

        return yij;
    }

    // Test
    public static ComplexMatrix complexMatrixP(boolean isForward) {
        ComplexMatrix complexMatrix = ComplexMatrix.complexMatrixIdentity(3);

        // Test artificial invertability with epsilon
        Complex mOne = new Complex(-1., 0.);
        if (!isForward) {
            // Step-down configuration
            //       [ 1  0 -1]
            // [P] = [-1  1  0]
            //       [ 0 -1  1]
            complexMatrix.set(1, 3, mOne);
            complexMatrix.set(2, 1, mOne);
            complexMatrix.set(3, 2, mOne);
        } else {
            // Step-up configuration
            //       [ 1 -1  0 ]
            // [P] = [ 0  1 -1 ]
            //       [-1  0  1 ]
            complexMatrix.set(1, 2, mOne);
            complexMatrix.set(2, 3, mOne);
            complexMatrix.set(3, 1, mOne);
        }

        return complexMatrix;
    }

    public static ComplexMatrix complexMatrixFull() {
        //          [ 1  1  1]
        // [Full] = [ 1  1  1]
        //          [ 1  1  1]
        ComplexMatrix complexMatrix = ComplexMatrix.complexMatrixIdentity(3);
        Complex one = new Complex(1., 0.);
        complexMatrix.set(1, 2, one);
        complexMatrix.set(1, 3, one);
        complexMatrix.set(2, 1, one);
        complexMatrix.set(2, 3, one);
        complexMatrix.set(3, 1, one);
        complexMatrix.set(3, 2, one);

        return complexMatrix;
    }

    public static ComplexMatrix getFullMinusVector3() {
        // vector = [1;1;1]
        ComplexMatrix complexMatrix = new ComplexMatrix(3, 1);
        Complex mOne = new Complex(-1., 0.);
        complexMatrix.set(1, 1, mOne);
        complexMatrix.set(2, 1, mOne);
        complexMatrix.set(3, 1, mOne);

        return complexMatrix;
    }

    public static ComplexMatrix getFullRowVector3() {
        ComplexMatrix row3 = new ComplexMatrix(1, 3);
        Complex one = new Complex(1., 0);
        row3.set(1, 1, one);
        row3.set(1, 2, one);
        row3.set(1, 3, one);

        return row3;
    }

    public static DenseMatrix buildFromBlocs(DenseMatrix b11, DenseMatrix b12, DenseMatrix b21, DenseMatrix b22) {
        int b11nbCol = b11.getColumnCount();
        int b11nbRow = b11.getRowCount();
        int b22nbCol = b22.getColumnCount();
        int b22nbRow = b22.getRowCount();

        if (b11nbCol != b21.getColumnCount()
                || b12.getColumnCount() != b22nbCol
                || b11nbRow != b12.getRowCount()
                || b21.getRowCount() != b22nbRow) {
            throw new MatrixException("Incompatible matrices dimensions");
        }

        DenseMatrix m = new DenseMatrix(b11nbRow + b22nbRow, b11nbCol + b22nbCol);
        for (int i = 0; i < b11nbRow; i++) {
            for (int j = 0; j < b11nbCol; j++) {
                m.add(i, j, b11.get(i, j));
            }

            for (int j = b11nbCol; j < b11nbCol + b22nbCol; j++) {
                m.add(i, j, b12.get(i, j - b11nbCol));
            }
        }

        for (int i = b11nbRow; i < b11nbRow + b22nbRow; i++) {
            for (int j = 0; j < b11nbCol; j++) {
                m.add(i, j, b21.get(i - b11nbRow, j));
            }

            for (int j = b11nbCol; j < b11nbCol + b22nbCol; j++) {
                m.add(i, j, b22.get(i - b11nbRow, j - b11nbCol));
            }
        }

        return m;
    }

}
