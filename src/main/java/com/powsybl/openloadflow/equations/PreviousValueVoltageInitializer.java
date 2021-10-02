/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PreviousValueVoltageInitializer implements VoltageInitializer {

    @Override
    public void prepare(LfNetwork network, LfNetworkParameters networkParameters, MatrixFactory matrixFactory, Reporter reporter) {
        // nothing to do
    }

    @Override
    public double getMagnitude(LfBus bus) {
        double v = bus.getV().eval();
        if (Double.isNaN(v)) {
            throw new PowsyblException("Voltage magnitude is undefined for bus '" + bus.getId() + "'");
        }
        return v;
    }

    @Override
    public double getAngle(LfBus bus) {
        double angle = bus.getAngle();
        if (Double.isNaN(angle)) {
            throw new PowsyblException("Voltage angle is undefined for bus '" + bus.getId() + "'");
        }
        return angle;
    }
}
