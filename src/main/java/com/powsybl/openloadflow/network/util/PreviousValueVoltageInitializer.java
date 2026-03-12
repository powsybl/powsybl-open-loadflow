/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Reports;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PreviousValueVoltageInitializer implements VoltageInitializer {

    public static final String NAME = "Previous Value";

    private final UniformValueVoltageInitializer defaultVoltageInitializer = new UniformValueVoltageInitializer();

    private final boolean defaultToUniformValue;

    public PreviousValueVoltageInitializer() {
        this(false);
    }

    public PreviousValueVoltageInitializer(boolean defaultToUniformValue) {
        this.defaultToUniformValue = defaultToUniformValue;
    }

    @Override
    public void prepare(LfNetwork network, ReportNode reportNode) {
        Reports.reportVoltageInitializer(reportNode, NAME);
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
    public double getMagnitude(LfDcBus dcBus) {
        double v = dcBus.getV();
        if (Double.isNaN(v)) {
            if (defaultToUniformValue) {
                return defaultVoltageInitializer.getMagnitude(dcBus);
            } else {
                throw new PowsyblException("Voltage is undefined for dcBus '" + dcBus.getId() + "'");
            }
        }
        return v;
    }
}
