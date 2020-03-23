/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.math.matrix.MatrixFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PreviousValueVoltageInitializer implements VoltageInitializer {

    @Override
    public void prepare(LfNetwork network, MatrixFactory matrixFactory, double lowImpedanceThreshold) {
        // nothing to do
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return bus.getV();
    }

    @Override
    public double getAngle(LfBus bus) {
        return bus.getAngle();
    }
}
