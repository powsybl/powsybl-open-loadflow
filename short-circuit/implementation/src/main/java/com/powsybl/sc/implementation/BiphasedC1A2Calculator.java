/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class BiphasedC1A2Calculator extends BiphasedCommonSupportShortCircuitCalculator {

    public BiphasedC1A2Calculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zFault,
                                  Complex initV,
                                  Complex v2dInit,
                                  Complex zo12, Complex zo22, Complex zo21,
                                  Complex zd12, Complex zd22, Complex zd21) {
        super(zdf, zof, zFault, initV, v2dInit, zo12, zo22, zo21, zd12, zd22, zd21);

        //Description of the fault (short circuit between c1 and a2) :
        // a1 ---------------x------------------  by definition : Ia1 = Ib1 = Ib2 = Ic2 = 0
        // b1 ---------------x------------------                  Ic1 = -Ia2
        // c1 ---------------+------------------                  Vc1 = Zf * Ic1 + Va2
        //                   |
        //                  Zf
        //                   |
        // a2 ---------------+------------------
        // b2 ---------------x------------------
        // c2 ---------------x------------------

        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, V2, I1 and I2 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ I1o ]         [ 1  1  1 ]   [ 0  ]               [ 1 ]
        // [ I1d ] = 1/3 * [ 1  a  a²] * [ 0  ] = 1/3 * Ic1 * [ a²]
        // [ I1i ]         [ 1  a² a ]   [ Ic1]               [ a ]
        //
        // [ I2o ]               [ 1 ]
        // [ I2d ] = -1/3 *Ic1 * [ 1 ]
        // [ I2i ]               [ 1 ]
        //
        // Step 2: get Ic1 :
        // Given:  Vc1 = V1o + a * V1d + a² * V1i        and       Va2 = V2o + V2d + V2i
        // and replacing them in:  Vc1 = Zf * Ic1 + Va2
        // we get
        //                                              a * V1d(init) - V2d(init)
        // Ic1 = -----------------------------------------------------------------------------------------------------------------
        //        Zf + 1/3*(Zd_11 - a*Zd_12 + Zd_22-a²*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a²*Zi_12 + Zi_11 - a*Zi_21)
        //
        //
        // Where Zo_ij and Zd_ij are complex impedance matrix elements at nodes 1 and 2:
        // Zo = tM * inv(Yo) * M   and Zd = tM * inv(Yd) * M
        //
        // Then, for example, we have with complex variables :
        // [ V1of ]          [ I1of ]     [ Zo_11 Zo_12 ]   [ I1of ]
        // [ V2of ] = -Zof * [ I2of ] = - [ Zo_21 Zo_22 ] * [ I2of ]
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        computeZt(); // computes rt and xt
        computeIc(); // computes Ic of common support from rt and xt
        computeCurrents(); // computes Io, Id, Ii for both supports from computed Ic
        computeVoltages(); // computes Vo, Vd, Vi from computed currents and computed terms of the impedance matrix

    }

    @Override
    public void computeIc() {

        // Compute Ic1 :
        // Complex expression :
        //                                              a * V1d(init) - V2d(init)
        // Ic1 = ------------------------------------------------------------------------------------------------------------------
        //        Zf + 1/3*(Zd_11 - a*Zd_12 + Zd_22-a²*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a²*Zi_12 + Zi_11 - a*Zi_21)
        //

        //compute the numerator matrix = a * V1d(init) - V2d(init)
        Complex numerator = geta().multiply(initV).subtract(v2dInit);
        ic = numerator.divide(zt);
    }

    @Override
    public void computeZt() {
        // Zt = Zf + 1/3*(Zd_11 - a*Zd_12 + Zd_22 -a²*Zd_21 + Zo_11 - Zo_21 + Zo_22 - Zo_12 + Zi_22 - a²*Zi_12 + Zi_11 - a*Zi_21)
        Complex ztmp = zdf.subtract(zd12.multiply(geta()))
                .add(zd22)
                .subtract(zd21.multiply(geta2()))
                .add(zof)
                .subtract(zo21)
                .add(zo22)
                .subtract(zo12)
                .add(zi22)
                .subtract(zi12.multiply(geta2()))
                .add(zif)
                .subtract(zi21.multiply(geta()));

        zt = zfault.getZg().add(ztmp.divide(3.));
    }

    @Override
    public void computeCurrents() {
        // step 3:
        // get the currents vectors
        // [ I1o ]               [ 1 ]
        // [ I1d ] = 1/3 * Ic1 * [ a²]
        // [ I1i ]               [ a ]
        //
        // [ I2o ]               [ 1 ]
        // [ I2d ] = -1/3 *Ic1 * [ 1 ]
        // [ I2i ]               [ 1 ]

        io = ic.divide(3.);
        id = io.multiply(geta2());
        ii = io.multiply(geta());

        i2o = io.multiply(-1.);
        i2d = i2o;
        i2i = i2o;
    }
}
