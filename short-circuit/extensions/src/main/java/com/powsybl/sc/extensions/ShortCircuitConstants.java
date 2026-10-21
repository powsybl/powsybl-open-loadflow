/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
public final class ShortCircuitConstants {

    private ShortCircuitConstants() {
    }

    public static final double DEFAULT_TRANS_RD = 0;
    public static final double DEFAULT_SUB_TRANS_RD = 0;
    public static final double DEFAULT_COEFF_K = 1;
    public static final double DEFAULT_COS_PHI = 0.85;
    public static final double DEFAULT_RATED_U = 100.;

    public static final double DEFAULT_TRANS_XD = 20.; //supposed in ohms // TODO : check most standard value
    public static final double DEFAULT_SUB_TRANS_XD = 20.; //supposed in ohms // TODO : check most standard value
    public static final double DEFAULT_STEP_UP_XD = 0.; //supposed in ohms // TODO : check most standard value

    public static final double DEFAULT_CQ = 1.1;
    public static final double DEFAULT_R1_X1_RATIO = 1.;
    public static final double DEFAULT_IKQ = 10;
}
