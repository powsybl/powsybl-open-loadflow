/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcLoadFlowEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final List<LfNetwork> networks;

    private final DcLoadFlowParameters parameters;

    public <T> DcLoadFlowEngine(T network, LfNetworkLoader<T> networkLoader, DcLoadFlowParameters parameters, Reporter reporter) {
        this.networks = LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public DcLoadFlowEngine(List<LfNetwork> networks, DcLoadFlowParameters parameters) {
        this.networks = networks;
        this.parameters = Objects.requireNonNull(parameters);
    }

    private static void distributeSlack(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType) {
        double mismatch = getActivePowerMismatch(buses);
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(balanceType, false);
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

    private DcLoadFlowResult run(Reporter reporter, LfNetwork network) {
        EquationSystem<DcVariableType, DcEquationType> equationSystem = DcEquationSystem.create(network, parameters.getEquationSystemCreationParameters());

        LoadFlowResult.ComponentResult.Status status = LoadFlowResult.ComponentResult.Status.FAILED;
        try (JacobianMatrix<DcVariableType, DcEquationType> j = new JacobianMatrix<>(equationSystem, parameters.getMatrixFactory())) {

            status = run(network, parameters, equationSystem, j, Collections.emptyList(), Collections.emptyList(), reporter).getLeft();
        } catch (Exception e) {
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }
        return new DcLoadFlowResult(network, getActivePowerMismatch(network.getBuses()), status);
    }

    public static void initStateVector(LfNetwork network, EquationSystem<DcVariableType, DcEquationType> equationSystem, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getIndex().getSortedVariablesToFind().size()];
        for (Variable<DcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_PHI:
                    x[v.getRow()] = Math.toRadians(initializer.getAngle(network.getBus(v.getElementNum())));
                    break;

                case BRANCH_ALPHA1:
                    x[v.getRow()] = network.getBranch(v.getElementNum()).getPiModel().getA1();
                    break;

                case DUMMY_P:
                    x[v.getRow()] = 0;
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
        equationSystem.getStateVector().set(x);
    }

    public static void updateNetwork(LfNetwork network, EquationSystem<DcVariableType, DcEquationType> equationSystem, double[] x) {
        // update state variable
        for (Variable<DcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_PHI:
                    network.getBus(v.getElementNum()).setAngle(Math.toDegrees(x[v.getRow()]));
                    break;

                case BRANCH_ALPHA1:
                    network.getBranch(v.getElementNum()).getPiModel().setA1(x[v.getRow()]);
                    break;

                case DUMMY_P:
                    // nothing to do
                    break;

                default:
                    throw new IllegalStateException("Unknown variable type " + v.getType());
            }
        }
    }

    public static void initTarget(Equation<DcVariableType, DcEquationType> equation, LfNetwork network, double[] targets) {
        switch (equation.getType()) {
            case BUS_TARGET_P:
                targets[equation.getColumn()] = network.getBus(equation.getElementNum()).getTargetP();
                break;

            case BUS_TARGET_PHI:
                targets[equation.getColumn()] = 0;
                break;

            case BRANCH_TARGET_P:
                targets[equation.getColumn()] = LfBranch.getDiscretePhaseControlTarget(network.getBranch(equation.getElementNum()), DiscretePhaseControl.Unit.MW);
                break;

            case BRANCH_TARGET_ALPHA1:
                targets[equation.getColumn()] = network.getBranch(equation.getElementNum()).getPiModel().getA1();
                break;

            case ZERO_PHI:
                targets[equation.getColumn()] = LfBranch.getA(network.getBranch(equation.getElementNum()));
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: " + equation.getType());
        }

        for (EquationTerm<DcVariableType, DcEquationType> term : equation.getTerms()) {
            if (term.isActive() && term.hasRhs()) {
                targets[equation.getColumn()] -= term.rhs();
            }
        }
    }

    public static Pair<LoadFlowResult.ComponentResult.Status, double[]> run(LfNetwork network, DcLoadFlowParameters parameters,
                                                                            EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                                            JacobianMatrix<DcVariableType, DcEquationType> j,
                                                                            Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches, Reporter reporter) {
        initStateVector(network, equationSystem, new UniformValueVoltageInitializer());

        Collection<LfBus> remainingBuses = new LinkedHashSet<>(network.getBuses());
        remainingBuses.removeAll(disabledBuses);

        if (parameters.isDistributedSlack()) {
            distributeSlack(remainingBuses, parameters.getBalanceType());
        }

        var targetVector = TargetVector.createArray(network, equationSystem, DcLoadFlowEngine::initTarget);

        if (!disabledBuses.isEmpty()) {
            // set buses injections and transformers to 0
            disabledBuses.stream()
                .map(lfBus -> equationSystem.getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Equation::getColumn)
                .forEach(column -> targetVector[column] = 0);
        }

        if (!disabledBranches.isEmpty()) {
            // set transformer phase shift to 0
            disabledBranches.stream()
                .map(lfBranch -> equationSystem.getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Equation::getColumn)
                .forEach(column -> targetVector[column] = 0);
        }

        LoadFlowResult.ComponentResult.Status status;
        try {
            j.solveTransposed(targetVector);
            status = LoadFlowResult.ComponentResult.Status.CONVERGED;
        } catch (MatrixException e) {
            status = LoadFlowResult.ComponentResult.Status.FAILED;

            Reports.reportDcLfSolverFailure(reporter, e.getMessage());
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }

        equationSystem.getStateVector().set(targetVector);
        updateNetwork(network, equationSystem, targetVector);

        // set all calculated voltages to NaN
        if (parameters.isSetVToNan()) {
            for (LfBus bus : network.getBuses()) {
                bus.setV(Double.NaN);
            }
        }

        Reports.reportDcLfComplete(reporter, status.toString());
        LOGGER.info("DC load flow completed (status={})", status);

        return Pair.of(status, targetVector);
    }
}
