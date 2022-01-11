/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class BusState extends BusDcState {

    private final double angle;
    private final double voltage;
    private final double loadTargetQ;
    private final double generationTargetQ;
    private final boolean voltageControlEnabled;
    private final DiscreteVoltageControl.Mode transformerVoltageControlMode;
    private final DiscreteVoltageControl.Mode shuntVoltageControlMode;
    private final boolean disabled;

    public BusState(LfBus bus) {
        super(bus);
        this.angle = bus.getAngle();
        this.voltage = bus.getV();
        this.loadTargetQ = bus.getLoadTargetQ();
        this.generationTargetQ = bus.getGenerationTargetQ();
        this.voltageControlEnabled = bus.isVoltageControlEnabled();
        transformerVoltageControlMode = bus.getTransformerVoltageControl().map(TransformerVoltageControl::getMode).orElse(null);
        shuntVoltageControlMode = bus.getShuntVoltageControl().map(ShuntVoltageControl::getMode).orElse(null);
        this.disabled = bus.isDisabled();
    }

    @Override
    public void restore() {
        super.restore();
        element.setAngle(angle);
        element.setV(voltage);
        element.setLoadTargetQ(loadTargetQ);
        element.setGenerationTargetQ(generationTargetQ);
        element.setVoltageControlEnabled(voltageControlEnabled);
        element.setVoltageControlSwitchOffCount(0);
        if (transformerVoltageControlMode != null) {
            element.getTransformerVoltageControl().ifPresent(control -> control.setMode(transformerVoltageControlMode));
        }
        if (shuntVoltageControlMode != null) {
            element.getShuntVoltageControl().ifPresent(control -> control.setMode(shuntVoltageControlMode));
        }
        element.setDisabled(disabled);
    }

    public static BusState save(LfBus bus) {
        return new BusState(bus);
    }
}
