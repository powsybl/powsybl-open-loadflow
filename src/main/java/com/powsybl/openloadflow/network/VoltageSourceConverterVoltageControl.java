/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.network;
/**
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class VoltageSourceConverterVoltageControl extends VoltageControl<LfBus> implements LfCopyable<VoltageSourceConverterVoltageControl, LfNetwork> {

    public VoltageSourceConverterVoltageControl(LfBus controlledBus, int targetPriority, double targetValue) {
        super(targetValue, Type.VOLTAGE_SOURCE_CONVERTER, targetPriority, controlledBus);
    }

    @Override
    public VoltageSourceConverterVoltageControl copy(LfNetwork copyNetwork) {
        LfBus copiedBus = copyNetwork.getBusById(controlledBus.getId());
        VoltageSourceConverterVoltageControl copiedVc = new VoltageSourceConverterVoltageControl(copiedBus, targetPriority, targetValue);
        for (LfBus controllerBus : controllerElements) {
            copiedVc.addControllerElement(copyNetwork.getBusById(controllerBus.getId()));
        }
        return copiedVc;
    }
}
