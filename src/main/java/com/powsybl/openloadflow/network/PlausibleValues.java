/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * Network related plausible values.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class PlausibleValues {

    private PlausibleValues() {
    }

    public static final double MIN_REACTIVE_RANGE = 1; // MVar
    public static final double MAX_REACTIVE_RANGE = 10000; // MVar
    public static final int ACTIVE_POWER_LIMIT = 10000; // MW
    public static final double MIN_TARGET_VOLTAGE_PU = 0.8; // pu
    public static final double MAX_TARGET_VOLTAGE_PU = 1.2; // pu
}
