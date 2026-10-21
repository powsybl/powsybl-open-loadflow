/**
 * Copyright (c) 2026, Jean-Baptiste Heyberger
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
public class ShortCircuitFaultImpedance {

    private final Complex zg; // Fault impedance to the ground

    private final Complex zb; // Fault impedance linked to the second phase

    private final Complex zc; // Fault impedance linked to the third phase

    public ShortCircuitFaultImpedance(Complex zg, Complex zb, Complex zc) {
        this.zg = zg;
        this.zb = zb;
        this.zc = zc;
    }

    public ShortCircuitFaultImpedance(Complex zg) {
        this(zg, new Complex(0.), new Complex(0.));
    }

    public Complex getZg() {
        return zg;
    }

    public Complex getZb() {
        return zb;
    }

    public Complex getZc() {
        return zc;
    }
}
