/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.asym;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public enum FlowType {
    I1X(0),
    I1Y(1),
    I2X(2),
    I2Y(3);

    private final int index;

    FlowType(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
