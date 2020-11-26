/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.google.common.base.Stopwatch;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.util.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final MatrixFactory matrixFactory;

    private final boolean updateFlows;

    public DcLoadFlowEngine(LfNetwork network, MatrixFactory matrixFactory) {
        this.networks = Collections.singletonList(network);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.updateFlows = false;
    }

    public DcLoadFlowEngine(Object network, DcLoadFlowParameters parameters) {
        LfNetworkParameters lfParameters = new LfNetworkParameters(parameters.getSlackBusSelector(),
            false, false, false,
            parameters.isTwtSplitShuntAdmittance(), false);
        this.networks = LfNetwork.load(network, lfParameters);
        matrixFactory = parameters.getMatrixFactory();
        updateFlows = parameters.isUpdateFlows();
    }

    public DcLoadFlowResult run() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        EquationSystem equationSystem = DcEquationSystem.create(network, new VariableSet(), updateFlows);

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());

        double[] targets = equationSystem.createTargetVector();

        equationSystem.updateEquations(x);
        JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);
        try {
            double[] dx = Arrays.copyOf(targets, targets.length);

            boolean ok;
            try {
                LUDecomposition lu = j.decomposeLU();
                lu.solveTransposed(dx);
                ok = true;
            } catch (Exception e) {
                ok = false;
                LOGGER.error("Failed to solve linear system for DC load flow", e);
            }

            equationSystem.updateEquations(dx);
            equationSystem.updateNetwork(dx);

            // set all calculated voltages to NaN
            for (LfBus bus : network.getBuses()) {
                bus.setV(Double.NaN);
            }

            stopwatch.stop();
            LOGGER.debug(Markers.PERFORMANCE_MARKER, "Dc loadflow ran in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            LOGGER.info("Dc loadflow complete (ok={})", ok);

            return new DcLoadFlowResult(network, ok);
        } finally {
            j.cleanLU();
        }
    }
}
