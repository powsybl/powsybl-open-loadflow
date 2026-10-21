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
public class BiphasedShortCircuitCalculator extends AbstractShortCircuitCalculator {

    public BiphasedShortCircuitCalculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zFault, Complex initV) {
        super(zdf, zof, zFault, initV);

    }

    public void computeCurrents() {
        //Description of the fault:
        // a ---------------x------------------  by definition : Ia = 0
        // b ---------------+------------------                  Ib = -Ic
        //                  |
        //                 Zf
        //                  |
        // c ---------------+------------------                  Vb = Zf * Ib + Vc
        //
        //Problem to solve:
        // Given the equalities above, we need to solve for V and I the following problem:
        // [ Vof ] = -tM * inv(Yo) * M * [ Iof ]
        // [ Vdf ] = -tM * inv(Yd) * M * [ Idf ] + tM * [ V(init) ]
        // [ Vif ] = -tM * inv(Yd) * M * [ Iif ]
        // Where:
        // - Yo and Yd are the full network admittance matrices (zero and direct fortescue components)
        // - M is the extraction matrix to get the subvectors V1, I1 of the full network vectors [V] and [I]
        //
        // Step 1: express the currents in fortescue basis :
        //
        // [ Io ]         [ 1  1  1 ]   [ 0  ]              [  0   ]
        // [ Id ] = 1/3 * [ 1  a  a²] * [ Ib ] = 1/3 * Ib * [a - a²]
        // [ Ii ]         [ 1  a² a ]   [-Ib ]              [a²- a ]
        //
        // Step 2: get Ib :
        // Given:  Vb = Vo + a² * Vd + a * Vi  and Vc = Vo + a * Vd + a² * Vi
        // replacing them in Vb = Zf * Ib + Vc we get :
        //
        //          j * sqrt(3) * tM * [Vinit]
        // Ib =  -----------------------------
        //              Zdf + Zif +Zf
        //
        // Where Zof and Zdf are complex impedance matrix elements :
        // Zof = tM * inv(Yo) * M   and Zdf = tM * inv(Yd) * M
        //
        // Step 3: compute the short circuit voltages:
        // From computed Ic1 we get complex values : I1o, I1d, I1i, I2o, I2d, I2i using step 1 formulas expressed with Ic1
        // Then compute the voltages from current values

        Complex zt = zdf.multiply(2).add(zfault.getZb());

        Complex ib = initV.multiply(-Math.sqrt(3.)).divide(zt).multiply(new Complex(0., 1.));

        // Compute the currents from step 1 formula :
        io = new Complex(0.);
        id = ib.divide(3.).multiply(geta().subtract(geta2()));
        ii = id.multiply(-1.);

    }
}
