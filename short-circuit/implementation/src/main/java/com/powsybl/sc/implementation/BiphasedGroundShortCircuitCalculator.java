/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import com.powsybl.math.matrix.ComplexMatrix;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BiphasedGroundShortCircuitCalculator extends AbstractShortCircuitCalculator {

    public BiphasedGroundShortCircuitCalculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zFault, Complex initV) {
        super(zdf, zof, zFault, initV);

    }

    public void computeCurrents() {
        computeCurrentsGeneralForm();
    }

    public void computeCurrentsSimplified() {
        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = 0
        // b ---------------+------------------                  Vc = Vb = 0
        //                  |
        //                  |
        // c ---------------+------------------
        //                  |
        //                  |
        //                /////
        //
        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
        // [ Vif ] = tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, I1 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ Io ]         [ 1  1  1 ]   [ 0  ]
        // [ Id ] = 1/3 * [ 1  a  a²] * [ Ib ]   => Io + Id + Ii = 0
        // [ Ii ]         [ 1  a² a ]   [ Ic ]
        //
        // Vo = Vd = Vi = 1/3 * Va
        //
        // Step 2:
        // Using the previous expressions we get : Id, Ii, Io and Va

        // Zof and Zdf are complex impedance matrix elements :
        // Zof = tM * inv(Yo) * M   and Zdf = tM * inv(Yd) * M
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        //         -(zof + zdf) * [Vinit]
        // Id = ---------------------------
        //         Zdf * (Zdf + 2 * Zof)
        //
        //           zof * [Vinit]
        // Ii = ---------------------------
        //         Zdf * (Zdf + 2 * Zof)
        //
        //           zdf * [Vinit]
        // Io = ---------------------------
        //         Zdf * (Zdf + 2 * Zof)
        //
        //       - 3 * zof * [Vinit]
        // Va = ---------------------
        //          Zdf + 2 * Zof

        // Itotal = 3.Io and Ik"E2E = Itotal supposing that Un = Vinit * sqrt(3)

        Complex zdf2zof = zdf.add(zof.multiply(2.));

        id = initV.multiply(-1.).multiply(zof.add(zdf)).divide(zdf.multiply(zdf2zof));
        io = initV.divide(zdf2zof);
        ii = initV.multiply(zof).divide(zdf.multiply(zdf2zof));
    }

    public void computeCurrentsLitteralForm() {
        // Fixme: formula gives a slightly different result
        // The problem here is a generalized form compared to the previous one
        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = 0
        // b ---------------+------------------                  Vc = Vb = 0
        //                  |
        //                  |
        // c --------------(---------+--------
        //                  |        |
        //                 Zb        Zc
        //                  |        |
        //                  +----+---+
        //                       |
        //                       Zg
        //                       |
        //                     /////

        // System to be solved: 13 equations
        // 1- Ia = 0  => Io + Id + Ii = 0
        // 2- Vg = Zg . Ig
        // 3- Vb - Vg = Zb . Ib
        // 4- Vc - Vg = Zc . Ic
        // 5- ic + iB = ig
        // 6,7,8- [Va;Vb;Vc] = [F] . [Vo;Vd;Vi]
        // 9,10- [Io,Id] = 1/3 . [1,1;a,a²] . [Ib;Ic]
        // 11- Vo = Zo . Io
        // 12- Vd = Zd . Id + Vd_init
        // 13- Vi = Zi . Ii
        //
        // With 13 unknowns: Io, Id, Ii, Vg, Ig, Va, Vb, Vc, Ib, Ic, Vo, Vd, Vi

        // This leads to the following system
        //
        // [ Io ]         [1  1 ]                 [1 a² a ]   [ Zo . Io           ]                  [ Zb+Zg  Zg  ]
        // [ Id ] = 1/3 . [a  a²] . inv([Zbcg]) . [1 a  a²] . [ Zd . Id + Vd_init ]    with [Zbcg] = [  Zg   Zc+Zg]
        //                                                    [ Zi . (-Id - Io)   ]
        //
        // With
        //               [1  1 ]                 [1 a² a ]
        // [Zeq] = 1/3 . [a  a²] . inv([Zbcg]) . [1 a  a²]
        //
        // We can write the solution as:
        //
        // [ Io ]      ([1,0]         [ Zo  0 ])
        // [ Id ] = inv([0,1] - [Zeq].[ 0   Zd]) . [Zeq] . [0; Vd_init ; 0]
        //             (              [-Zi -Zi])

        ComplexMatrix zbcg = new ComplexMatrix(2, 2);
        zbcg.set(0, 0, zfault.getZb().add(zfault.getZg()));
        zbcg.set(1, 0, zfault.getZg());
        zbcg.set(0, 1, zfault.getZg());
        zbcg.set(1, 1, zfault.getZc().add(zfault.getZg()));

        ComplexMatrix mF1 = new ComplexMatrix(2, 2);
        Complex third = new Complex(1. / 3.);
        mF1.set(0, 0, third);
        mF1.set(1, 0, third.multiply(geta()));
        mF1.set(0, 1, third);
        mF1.set(1, 1, third.multiply(geta2()));

        ComplexMatrix mF2 = new ComplexMatrix(2, 3);
        mF2.set(0, 0, new Complex(1.));
        mF2.set(1, 0, new Complex(1.));
        mF2.set(0, 1, geta2());
        mF2.set(1, 1, geta());
        mF2.set(0, 2, geta());
        mF2.set(1, 2, geta2());

        ComplexMatrix zoZdZi = new ComplexMatrix(3, 2);
        zoZdZi.set(0, 0, zof);
        zoZdZi.set(1, 1, zdf);
        zoZdZi.set(2, 0, zdf.multiply(-1.));
        zoZdZi.set(2, 1, zdf.multiply(-1.));

        ComplexMatrix vinit = new ComplexMatrix(3, 1);
        vinit.set(1, 0, initV);

        DenseMatrix ident2 = ComplexMatrix.createIdentity(2).toRealCartesianMatrix();

        DenseMatrix mZeq = mF1.toRealCartesianMatrix()
                .times(getInverse2by2(zbcg).toRealCartesianMatrix())
                .times(mF2.toRealCartesianMatrix());

        ComplexMatrix mZeq2 = ComplexMatrix.fromRealCartesian(ident2.add(mZeq.times(zoZdZi.toRealCartesianMatrix(), -1.)).toDense());
        DenseMatrix mInvZeq2 = getInverse2by2(mZeq2).toRealCartesianMatrix();

        ComplexMatrix mIoId = ComplexMatrix.fromRealCartesian(mInvZeq2.times(mZeq).times(vinit.toRealCartesianMatrix()));

        io = mIoId.get(0, 0);
        id = mIoId.get(1, 0);
        ii = io.add(id).multiply(-1.);
    }

    public void computeCurrentsGeneralForm() {
        // The problem here is a generalized form compared to the previous one
        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = 0
        // b ---------------+------------------                  Vc = Vb = 0
        //                  |
        //                  |
        // c --------------(---------+--------
        //                  |        |
        //                 Zb        Zc
        //                  |        |
        //                  +----+---+
        //                       |
        //                       Zg
        //                       |
        //                     /////

        // System to be solved: 13 equations
        // 1- Ia = 0  => Io + Id + Ii = 0
        // 2- Vg = Zg . Ig
        // 3- Vb - Vg = Zb . Ib
        // 4- Vc - Vg = Zc . Ic
        // 5- ic + iB = ig
        // 6,7,8- [Va;Vb;Vc] = [F] . [Vo;Vd;Vi]
        // 9,10- [Io,Id] = 1/3 . [1,1;a,a²] . [Ib;Ic]
        // 11- Vo = Zo . Io
        // 12- Vd = Zd . Id + Vd_init
        // 13- Vi = Zi . Ii
        //
        // With 13 unknowns: Io, Id, Ii, Vg, Ig, Va, Vb, Vc, Ib, Ic, Vo, Vd, Vi

        //                   1   2   3   4   5   6   7   8   9   10  11  12  13
        //  eqs   vars -->   Io, Id, Ii, Vo, Vd, Vi, Vg, Ig, Va, Vb, Vc, Ib, Ic,
        //   |
        //   V
        //   1-          [   1    1   1                                           ]    Io + Id + Ii = 0
        //   2-          [                            1 -Zg                       ]    Vg - Zg . Ig = 0
        //   3-          [                           -1           1      -Zb      ]    Vb - Vg - Zb . Ib = 0
        //   4-          [                           -1               1      -Zc  ]    Vc - Vg - Zc . Ic = 0
        //   5-          [                               -1                1   1  ]    ic + ib - ig = 0
        //   6-          [                1   1   1           -1                  ]    Vo + Vd + Vi - Va = 0
        //   7-          [                1   a²  a               -1              ]    Vo + a².Vd + a.Vi - Vb = 0
        //   8-          [                1   a   a²                 -1           ]    Vo + a.Vd + a².Vi - Vc = 0
        //   9-          [  -1                                             1/3 1/3]    1/3.Ib + 1/3.Ic - Io = 0
        //   10-         [      -1                                        1/3a 1/3a²]  1/3.a.Ib + 1/3.a².Ic - Id = 0
        //   11-         [ -Zo            1                                       ]    Vo - Zo . Io = 0
        //   12-         [     -Zd            1                                   ]    Vd - Zd . Id = Vd_init
        //   13-         [         -Zi            1                               ]    Vi - Zi . Ii = 0

        Complex c1 = new Complex(1.);
        Complex mc1 = new Complex(-1.);

        ComplexMatrix mSystem = new ComplexMatrix(13, 13);
        mSystem.set(0, 0, c1);
        mSystem.set(0, 1, c1);
        mSystem.set(0, 2, c1);
        mSystem.set(1, 6, c1);
        mSystem.set(1, 7, zfault.getZg().multiply(-1.));
        mSystem.set(2, 6, mc1);
        mSystem.set(2, 9, c1);
        mSystem.set(2, 11, zfault.getZb().multiply(-1.));
        mSystem.set(3, 6, mc1);
        mSystem.set(3, 10, c1);
        mSystem.set(3, 12, zfault.getZc().multiply(-1.));
        mSystem.set(4, 7, mc1);
        mSystem.set(4, 11, c1);
        mSystem.set(4, 12, c1);
        mSystem.set(5, 3, c1);
        mSystem.set(5, 4, c1);
        mSystem.set(5, 5, c1);
        mSystem.set(5, 8, mc1);
        mSystem.set(6, 3, c1);
        mSystem.set(6, 4, geta2());
        mSystem.set(6, 5, geta());
        mSystem.set(6, 9, mc1);
        mSystem.set(7, 3, c1);
        mSystem.set(7, 4, geta());
        mSystem.set(7, 5, geta2());
        mSystem.set(7, 10, mc1);
        mSystem.set(8, 0, mc1);
        mSystem.set(8, 11, c1.multiply(1. / 3.));
        mSystem.set(8, 12, c1.multiply(1. / 3.));
        mSystem.set(9, 1, mc1);
        mSystem.set(9, 11, c1.multiply(1. / 3.).multiply(geta()));
        mSystem.set(9, 12, c1.multiply(1. / 3.).multiply(geta2()));
        mSystem.set(10, 0, zof.multiply(-1.));
        mSystem.set(10, 3, mc1);
        mSystem.set(11, 1, zdf.multiply(-1.));
        mSystem.set(11, 4, mc1);
        mSystem.set(12, 2, zdf.multiply(-1.));
        mSystem.set(12, 5, mc1);

        ComplexMatrix rhs = new ComplexMatrix(13, 1);
        rhs.set(11, 0, initV);
        DenseMatrix mRhs = rhs.toRealCartesianMatrix();

        LUDecomposition lu = mSystem.toRealCartesianMatrix().decomposeLU();
        lu.solve(mRhs);

        ComplexMatrix res = ComplexMatrix.fromRealCartesian(mRhs);
        io = res.get(0, 0);
        id = res.get(1, 0);
        ii = res.get(2, 0);
    }

    public ComplexMatrix getInverse2by2(ComplexMatrix m) {
        // TODO : put an exception if matrix is not 2x2
        Complex a = m.get(0, 0);
        Complex b = m.get(0, 1);
        Complex c = m.get(1, 0);
        Complex d = m.get(1, 1);
        Complex invDet = a.multiply(d).subtract(b.multiply(c)).reciprocal();

        ComplexMatrix mInv = new ComplexMatrix(2, 2);
        mInv.set(0, 0, d.multiply(invDet));
        mInv.set(1, 1, a.multiply(invDet));
        mInv.set(0, 1, b.multiply(invDet).multiply(-1.));
        mInv.set(1, 0, c.multiply(invDet).multiply(-1.));

        return mInv;
    }
}
