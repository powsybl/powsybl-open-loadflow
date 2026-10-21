/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class TwoWindingsTransformerNorm {

    public static final String NAME = "twoWindingsTransformerNorm";

    private double kNorm; // coef used by the chosen norm to modify R, X, Ro and Xo

    public TwoWindingsTransformerNorm(double kNorm) {
        this.kNorm = kNorm;
    }

    public double getkNorm() {
        return kNorm;
    }

    public void setkNorm(double kNorm) {
        this.kNorm = kNorm;
    }
}
