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
public class GeneratorNorm {

    public static final String NAME = "generatorNorm";

    private double kG; // coeff related to the application of a norm, possibly modifying x"d or x'd

    public GeneratorNorm(double kG) {
        this.kG = kG;
    }

    public double getkG() {
        return kG;
    }

    public void setkG(double kG) {
        this.kG = kG;
    }
}
