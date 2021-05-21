/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.EvaluableConstants.NAN;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final DcLoadFlowParameters parameters;

    private double[] targetVector;

    public DcLoadFlowEngine(LfNetwork network, MatrixFactory matrixFactory, boolean setVToNan) {
        this.networks = Collections.singletonList(network);
        parameters = new DcLoadFlowParameters(new FirstSlackBusSelector(), matrixFactory, setVToNan, EnumSet.noneOf(Country.class));
    }

    public DcLoadFlowEngine(Object network, DcLoadFlowParameters parameters, Reporter reporter) {
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters(parameters.getSlackBusSelector(), false, false, false, false,
                parameters.getPlausibleActivePowerLimit(), false, parameters.isComputeMainConnectedComponentOnly(), parameters.getCountriesToBalance());
        this.networks = LfNetwork.load(network, lfNetworkParameters, reporter);
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

    public List<DcLoadFlowResult> run(Reporter reporter) {
        return networks.stream().map(n -> run(reporter, n)).collect(Collectors.toList());
    }

    public DcLoadFlowResult run(Reporter reporter, LfNetwork pNetwork) {
        DcEquationSystemCreationParameters creationParameters = new DcEquationSystemCreationParameters(parameters.isUpdateFlows(), false, parameters.isForcePhaseControlOffAndAddAngle1Var(), parameters.isUseTransformerRatio());
        EquationSystem equationSystem = DcEquationSystem.create(pNetwork, new VariableSet(), creationParameters);

        LoadFlowResult.ComponentResult.Status status = LoadFlowResult.ComponentResult.Status.FAILED;
        try (JacobianMatrix j = new JacobianMatrix(equationSystem, parameters.getMatrixFactory())) {

            status = run(equationSystem, j, Collections.emptyList(), Collections.emptyList(), reporter);
        } catch (Exception e) {
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }
        return new DcLoadFlowResult(pNetwork, getActivePowerMismatch(pNetwork.getBuses()), status);
    }

    public LoadFlowResult.ComponentResult.Status run(EquationSystem equationSystem, JacobianMatrix j,
                                                     Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches,
                                                     Reporter reporter) {

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());

        // only process main (largest) connected component
        LfNetwork network = networks.get(0);

        Collection<LfBus> remainingBuses = new LinkedHashSet<>(network.getBuses());
        remainingBuses.removeAll(disabledBuses);

        if (parameters.isDistributedSlack()) {
            distributeSlack(remainingBuses);
        }

        equationSystem.updateEquations(x);

        this.targetVector = TargetVector.createArray(network, equationSystem);

        if (!disabledBuses.isEmpty()) {
            // set buses injections and transformers to 0
            disabledBuses.stream()
                .map(lfBus -> equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Equation::getColumn)
                .forEach(column -> targetVector[column] = 0);
        }

        if (!disabledBranches.isEmpty()) {
            // set transformer phase shift to 0
            disabledBranches.stream()
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
            reporter.report(Report.builder()
                .withKey("loadFlowFailure")
                .withDefaultMessage("Failed to solve linear system for DC load flow: ${errorMessage}")
                .withValue("errorMessage", e.getMessage())
                .withSeverity(OpenLoadFlowReportConstants.ERROR_SEVERITY)
                .build());
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }

        equationSystem.updateEquations(targetVector);
        equationSystem.updateEquations(targetVector, EquationSystem.EquationUpdateType.AFTER_NR);
        equationSystem.updateNetwork(targetVector);

        // set all calculated voltages to NaN
        if (parameters.isSetVToNan()) {
            for (LfBus bus : network.getBuses()) {
                bus.setV(NAN);
            }
        }

        reporter.report(Report.builder()
            .withKey("loadFlowCompleted")
            .withDefaultMessage("DC load flow completed (status=${lfStatus})")
            .withValue("lfStatus", status.toString())
            .withSeverity(OpenLoadFlowReportConstants.INFO_SEVERITY)
            .build());
        LOGGER.info("DC load flow completed (status={})", status);
        return status;
    }

    public double[] getTargetVector() {
        return targetVector;
    }
}
