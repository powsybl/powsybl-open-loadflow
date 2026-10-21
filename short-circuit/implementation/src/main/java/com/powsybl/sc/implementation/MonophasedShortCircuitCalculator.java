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
public class MonophasedShortCircuitCalculator extends AbstractShortCircuitCalculator {

    public MonophasedShortCircuitCalculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zFault, Complex initV) {
        super(zdf, zof, zFault, initV);

    }

    public void computeCurrents() {

        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = Ib = 0
        // b ---------------x------------------
        // c ---------------+------------------                  Vc = Zf * Ic
        //                  |
        //                 Zground
        //                  |
        //                /////

        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = tM * [ V(init) ] - tM * inv(Yd) * M * [ Idf ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, I1 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ Io ]         [ 1  1  1 ]   [ 0  ]              [ 1 ]
        // [ Id ] = 1/3 * [ 1  a  a²] * [ 0  ] = 1/3 * Ic * [ a²]
        // [ Ii ]         [ 1  a² a ]   [ Ic ]              [ a ]
        //
        // Step 2: get Ic1 :
        // Given:  Vc = a² * Vo + Vd + a * Vi  and replacing it in Vc = Zf * Ic we get :
        //
        //            a * tM * [Vinit]
        // Ic = -----------------------------
        //       1/3 * (Zof + 2 * Zdf) + Zground
        //
        // Where Zof and Zdf are complex impedance matrix elements :
        // Zof = tM * inv(Yo) * M   and Zdf = tM * inv(Yd) * M
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        // Complex expression of Ic :
        //            a * Vd(init)                 a * Vd(init)
        // Ic = ----------------------------- = -----------------
        //       1/3 * (Zof + 2 * Zdf) + Zground            Zt

        Complex z1 = zdf.multiply(2.).add(zof);
        Complex z2 = z1.divide(3.);
        Complex zt = zfault.getZg().add(z2);
        Complex ic = initV.multiply(geta()).divide(zt);

        io = ic.divide(3.);
        id = io.multiply(geta2());
        ii = io.multiply(geta());
    }
}
