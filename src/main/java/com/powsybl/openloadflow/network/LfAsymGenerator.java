/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public class LfAsymGenerator {

    private final double bz;
    private final double gz;
    private final double gn;
    private final double bn;

    public LfAsymGenerator(double gz, double bz, double gn, double bn) {
        this.gz = gz;
        this.bz = bz;
        this.gn = gn;
        this.bn = bn;
    }

    public double getGz() {
        return gz;
    }

    public double getGn() {
        return gn;
    }

    public double getBz() {
        return bz;
    }

    public double getBn() {
        return bn;
    }
}
