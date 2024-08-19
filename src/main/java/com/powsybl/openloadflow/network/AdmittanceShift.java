/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AdmittanceShift {

    private double g;

    private double b;

    public AdmittanceShift() {
        this(0d, 0d);
    }

    public AdmittanceShift(double g, double b) {
        this.g = g;
        this.b = b;
    }

    public double getG() {
        return g;
    }

    public double getB() {
        return b;
    }

    public void add(AdmittanceShift other) {
        Objects.requireNonNull(other);
        g += other.getG();
        b += other.getB();
    }

    @Override
    public String toString() {
        return "AdmittanceShift(" +
                "g=" + g +
                ", b=" + b +
                ')';
    }
}
