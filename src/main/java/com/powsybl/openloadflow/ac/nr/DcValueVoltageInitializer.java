/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Profiler;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcValueVoltageInitializer implements VoltageInitializer {

    @Override
    public void prepare(LfNetwork network, MatrixFactory matrixFactory, Profiler profiler) {
        if (new DcLoadFlowEngine(network, matrixFactory, profiler).run().getStatus() != LoadFlowResult.ComponentResult.Status.CONVERGED) {
            throw new PowsyblException("DC loadflow failed, impossible to initialize voltage angle from DC values");
        }
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return 1;
    }

    @Override
    public double getAngle(LfBus bus) {
        return bus.getAngle();
    }
}
