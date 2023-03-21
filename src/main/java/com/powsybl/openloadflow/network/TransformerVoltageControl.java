/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
public class TransformerVoltageControl extends DiscreteVoltageControl<LfBranch> {

    private static final int PRIORITY = 1;

    public TransformerVoltageControl(LfBus controlledBus, double targetValue, Double targetDeadband) {
        super(controlledBus, Type.TRANSFORMER, PRIORITY, targetValue, targetDeadband);
    }

    @Override
    public boolean isControllerEnabled(LfBranch controllerElement) {
        return controllerElement.isVoltageControlEnabled();
    }

    @Override
    protected boolean isControlledBySameControlType(LfBus bus) {
        return bus.isTransformerVoltageControlled();
    }

    @Override
    protected TransformerVoltageControl getControl(LfBus bus) {
        return bus.getTransformerVoltageControl().orElseThrow();
    }
}
