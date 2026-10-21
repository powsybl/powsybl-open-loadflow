/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.implementation;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public abstract class AbstractShortCircuitCalculator {

    Complex io;
    Complex id;
    Complex ii;

    Complex zdf;
    Complex zof;
    ShortCircuitFaultImpedance zfault;

    Complex initV;

    protected AbstractShortCircuitCalculator(Complex zdf, Complex zof, ShortCircuitFaultImpedance zfault,
                                             Complex initV) {
        this.zdf = zdf;
        this.zof = zof;
        this.zfault = zfault;
        this.initV = initV;
    }

    public Complex getIo() {
        return io;
    }

    public Complex getId() {
        return id;
    }

    public Complex getIi() {
        return ii;
    }

    public abstract void computeCurrents();

    public Complex geta() {
        return new Complex(-0.5, FastMath.sqrt(3.) / 2);
    }

    public Complex geta2() {
        return geta().multiply(geta());
    }

}
