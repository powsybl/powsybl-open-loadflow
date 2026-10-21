/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
package com.powsybl.sc.extensions;

public final class FortescueConstants {

    private FortescueConstants() {
    }

    public static final double DEFAULT_COEFF_RO = 1;
    public static final double DEFAULT_COEFF_XO = 1;

    public static final GeneratorFortescueType.GeneratorType DEFAULT_GENERATOR_FORTESCUE_TYPE = GeneratorFortescueType.GeneratorType.ROTATING_MACHINE;
}
