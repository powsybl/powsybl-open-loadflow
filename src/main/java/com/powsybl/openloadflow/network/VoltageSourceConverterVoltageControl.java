/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.network;
/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class VoltageSourceConverterVoltageControl extends VoltageControl<LfBus> {

    public VoltageSourceConverterVoltageControl(LfBus controlledBus, int targetPriority, double targetValue) {
        super(targetValue, Type.VOLTAGE_SOURCE_CONVERTER, targetPriority, controlledBus);
    }

}
