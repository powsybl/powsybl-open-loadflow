/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.ac.newfiles.LfAcDcConverter;
import com.powsybl.openloadflow.ac.newfiles.LfDcNode;
import com.powsybl.openloadflow.ac.newfiles.LfVoltageSourceConverter;
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

    @Override
    public double getReactivePower(LfVoltageSourceConverter converter) {
        double q = converter.getQac();
        if (Double.isNaN(q)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getReactivePower(converter);
            } else {
                throw new PowsyblException("Reactive Power is undefined for converter '" + converter.getId() + "'");
            }
        }
        return q;
    }

    @Override
    public double getActivePower(LfAcDcConverter converter) {
        double p = converter.getPac();
        if (Double.isNaN(p)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getActivePower(converter);
            } else {
                throw new PowsyblException("Power is undefined for converter '" + converter.getId() + "'");
            }
        }
        return p;
    }

    @Override
    public double getMagnitude(LfDcNode dcNode) {
        double v = dcNode.getV();
        if (Double.isNaN(v)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getMagnitude(dcNode);
            } else {
                throw new PowsyblException("Power is undefined for dcNode '" + dcNode.getId() + "'");
            }
        }
        return v;
    }

    @Override
    public double getCurrent(LfAcDcConverter converter) {
        double i = converter.getIConv();
        if (Double.isNaN(i)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getCurrent(converter);
            } else {
                throw new PowsyblException("Power is undefined for converter '" + converter.getId() + "'");
            }
        }
        return i;
    }
}
