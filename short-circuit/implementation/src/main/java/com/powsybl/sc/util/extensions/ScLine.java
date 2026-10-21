/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

import org.apache.commons.math3.complex.Complex;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class ScLine {

    private final Complex zo; // Zo : value of the homopolar impedance (in pu, same base as Z) expressed at the leg2 side

    ScLine(Complex zo) {
        this.zo = zo;
    }

    public Complex getZo() {
        return zo;
    }
}
