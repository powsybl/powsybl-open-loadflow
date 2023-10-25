/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PreviousValueVoltageInitializer implements VoltageInitializer {

    private final UniformValueVoltageInitializer defaultVoltageInitializer = new UniformValueVoltageInitializer();

    private final boolean defaultToUniformValue;

    public PreviousValueVoltageInitializer() {
        this(false);
    }

    public PreviousValueVoltageInitializer(boolean defaultToUniformValue) {
        this.defaultToUniformValue = defaultToUniformValue;
    }

    @Override
    public void prepare(LfNetwork network) {
        // nothing to do
    }

    @Override
    public double getMagnitude(LfBus bus) {
        double v = bus.getV();
        if (Double.isNaN(v)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getMagnitude(bus);
            } else {
                throw new PowsyblException("Voltage magnitude is undefined for bus '" + bus.getId() + "'");
            }
        }
        return v;
    }

    @Override
    public double getAngle(LfBus bus) {
        double angle = bus.getAngle();
        if (Double.isNaN(angle)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getAngle(bus);
            } else {
                throw new PowsyblException("Voltage angle is undefined for bus '" + bus.getId() + "'");
            }
        }
        return angle;
    }
}
