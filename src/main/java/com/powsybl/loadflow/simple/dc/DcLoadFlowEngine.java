/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.dc;

import com.google.common.base.Stopwatch;
import com.powsybl.loadflow.simple.dc.equations.DcEquationSystem;
import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.loadflow.simple.equations.JacobianMatrix;
import com.powsybl.loadflow.simple.equations.UniformValueVoltageInitializer;
import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.powsybl.loadflow.simple.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final LfNetwork network;

    private final MatrixFactory matrixFactory;

    public DcLoadFlowEngine(LfNetwork network, MatrixFactory matrixFactory) {
        this.network = Objects.requireNonNull(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    public boolean run() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        EquationSystem equationSystem = DcEquationSystem.create(network);

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());

        double[] targets = equationSystem.createTargetVector();

        equationSystem.updateEquations(x);
        JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);
        try {
            double[] dx = Arrays.copyOf(targets, targets.length);

            boolean status;
            try {
                LUDecomposition lu = j.decomposeLU();
                lu.solve(dx);
                status = true;
            } catch (Exception e) {
                status = false;
                LOGGER.error("Failed to solve linear system for simple DC load flow.", e);
            }

            equationSystem.updateEquations(dx);
            equationSystem.updateNetwork(dx);

            stopwatch.stop();
            LOGGER.debug(PERFORMANCE_MARKER, "Dc loadflow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            LOGGER.info("Dc loadflow complete (status={})", status);

            return status;
        } finally {
            j.cleanLU();
        }
    }
}
