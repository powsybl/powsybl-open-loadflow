/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.math.matrix.MatrixFactory;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PreviousValueVoltageInitializer implements VoltageInitializer {

    @Override
    public void prepare(LfNetwork network, MatrixFactory matrixFactory) {
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
