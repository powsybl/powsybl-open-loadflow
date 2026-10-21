/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.util.extensions;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class AdmittanceConstants {

    private AdmittanceConstants() {
    }

    public static final double COEF_XO_XD = 0.33; // xd/xo = 1/3 and xmo/x"d = 1/3

    public static final double INFINITE_IMPEDANCE_ADMITTANCE_VALUE = 0.00000001;
    // This value represents the case where have a hompoloar transformer connecting two different connex areas through an infinite impedance.
    // This may create a singular matrix. As a consequence we replace the zero admittance value by a very small one.
}
