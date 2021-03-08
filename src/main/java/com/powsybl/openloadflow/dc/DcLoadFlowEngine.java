/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final DcLoadFlowParameters parameters;

    public DcLoadFlowEngine(LfNetwork network, MatrixFactory matrixFactory) {
        this.networks = Collections.singletonList(network);
        parameters = new DcLoadFlowParameters(new FirstSlackBusSelector(), matrixFactory);
    }

    public DcLoadFlowEngine(Object network, DcLoadFlowParameters parameters) {
        this.networks = LfNetwork.load(network, new LfNetworkParameters(parameters.getSlackBusSelector(), false, false, false, false, parameters.getPlausibleActivePowerLimit(), false));
        this.parameters = Objects.requireNonNull(parameters);
    }

    public DcLoadFlowEngine(List<LfNetwork> networks, DcLoadFlowParameters parameters) {
        this.networks = networks;
        this.parameters = Objects.requireNonNull(parameters);
    }

    private void distributeSlack(LfNetwork network) {
        double mismatch = network.getActivePowerMismatch();
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), false);
        activePowerDistribution.run(network, mismatch);
    }

    public DcLoadFlowResult run() {
        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        if (parameters.isDistributedSlack()) {
            distributeSlack(network);
        }

        DcEquationSystemCreationParameters creationParameters = new DcEquationSystemCreationParameters(parameters.isUpdateFlows(), false, parameters.isForcePhaseControlOffAndAddAngle1Var(), parameters.isUseTransformerRatio());
        EquationSystem equationSystem = DcEquationSystem.create(network, new VariableSet(), creationParameters);

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());

        equationSystem.updateEquations(x);

        double[] targets = equationSystem.createTargetVector();

        try (JacobianMatrix j = new JacobianMatrix(equationSystem, parameters.getMatrixFactory())) {
            double[] dx = Arrays.copyOf(targets, targets.length);

            LoadFlowResult.ComponentResult.Status status;
            try {
                j.solveTransposed(dx);
                status = LoadFlowResult.ComponentResult.Status.CONVERGED;
            } catch (Exception e) {
                status = LoadFlowResult.ComponentResult.Status.FAILED;
                LOGGER.error("Failed to solve linear system for DC load flow", e);
            }

            equationSystem.updateEquations(dx);
            equationSystem.updateNetwork(dx);

            // set all calculated voltages to NaN
            for (LfBus bus : network.getBuses()) {
                bus.setV(Double.NaN);
            }

            LOGGER.info("Dc loadflow complete (status={})", status);

            return new DcLoadFlowResult(network, network.getActivePowerMismatch(), status);
        }
    }
}
