/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class BusModeState extends ElementState<LfBus> {

    private final DiscreteVoltageControl.Mode transformerVoltageControlMode;
    private final DiscreteVoltageControl.Mode shuntVoltageControlMode;

    public BusModeState(LfBus bus) {
        super(bus);
        transformerVoltageControlMode = bus.getTransformerVoltageControl().map(TransformerVoltageControl::getMode).orElse(null);
        shuntVoltageControlMode = bus.getShuntVoltageControl().map(ShuntVoltageControl::getMode).orElse(null);
    }

    @Override
    public void restore() {
        if (transformerVoltageControlMode != null) {
            element.getTransformerVoltageControl().ifPresent(control -> control.setMode(transformerVoltageControlMode));
        }
        if (shuntVoltageControlMode != null) {
            element.getShuntVoltageControl().ifPresent(control -> control.setMode(shuntVoltageControlMode));
        }
    }

    public static BusModeState save(LfBus bus) {
        return new BusModeState(bus);
    }
}
