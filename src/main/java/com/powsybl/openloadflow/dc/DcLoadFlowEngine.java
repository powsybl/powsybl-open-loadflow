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
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final DcLoadFlowParameters parameters;

    private double[] targetVector;

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

    private void distributeSlack(Collection<LfBus> buses) {
        double mismatch = getActivePowerMismatch(buses);
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), false);
        activePowerDistribution.run(buses, mismatch);
    }

    private static double getActivePowerMismatch(Collection<LfBus> buses) {
        double mismatch = 0;
        for (LfBus b : buses) {
            mismatch += b.getGenerationTargetP() - b.getLoadTargetP();
        }
        return -mismatch;
    }

    public DcLoadFlowResult run() {
        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        DcEquationSystemCreationParameters creationParameters = new DcEquationSystemCreationParameters(parameters.isUpdateFlows(), false, parameters.isForcePhaseControlOffAndAddAngle1Var(), parameters.isUseTransformerRatio());
        EquationSystem equationSystem = DcEquationSystem.create(network, new VariableSet(), creationParameters);

        LoadFlowResult.ComponentResult.Status status = LoadFlowResult.ComponentResult.Status.FAILED;
        try (JacobianMatrix j = new JacobianMatrix(equationSystem, parameters.getMatrixFactory())) {

            status = run(equationSystem, j, Collections.emptyList(), Collections.emptyList());
        } catch (Exception e) {
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }

        return new DcLoadFlowResult(network, getActivePowerMismatch(network.getBuses()), status);
    }

    public LoadFlowResult.ComponentResult.Status run(EquationSystem equationSystem, JacobianMatrix j,
                                                     Collection<LfBus> removedBuses, Collection<LfBranch> removedBranches) {

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());

        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        Collection<LfBus> remainingBuses = new HashSet<>(network.getBuses());
        remainingBuses.removeAll(removedBuses);

        if (parameters.isDistributedSlack()) {
            distributeSlack(remainingBuses);
        }

        equationSystem.updateEquations(x);

        this.targetVector = equationSystem.createTargetVector();

        if (!removedBuses.isEmpty()) {
            // set buses injections to 0
            removedBuses.stream()
                .map(lfBus -> equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Equation::getColumn)
                .forEach(column -> targetVector[column] = 0);
        }

        if (!removedBranches.isEmpty()) {
            // set transformer phase shift to 0
            removedBranches.stream()
                .map(lfBranch -> equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Equation::getColumn)
                .forEach(column -> targetVector[column] = 0);
        }

        LoadFlowResult.ComponentResult.Status status;
        try {
            j.solveTransposed(targetVector);
            status = LoadFlowResult.ComponentResult.Status.CONVERGED;
        } catch (Exception e) {
            status = LoadFlowResult.ComponentResult.Status.FAILED;
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }

        equationSystem.updateEquations(targetVector);
        equationSystem.updateNetwork(targetVector);

        // set all calculated voltages to NaN
        for (LfBus bus : network.getBuses()) {
            bus.setV(Double.NaN);
        }

        LOGGER.info("Dc loadflow complete (status={})", status);
        return status;
    }

    public double[] getTargetVector() {
        return targetVector;
    }
}
