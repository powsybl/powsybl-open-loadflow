/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.lf.LoadFlowEngine;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkLoader;
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
public class DcLoadFlowEngine implements LoadFlowEngine<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final DcLoadFlowContext context;

    public DcLoadFlowEngine(DcLoadFlowContext context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public DcLoadFlowContext getContext() {
        return context;
    }

    private static void distributeSlack(Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType) {
        double mismatch = getActivePowerMismatch(buses);
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(balanceType, false);
        activePowerDistribution.run(buses, mismatch);
    }

    public static double getActivePowerMismatch(Collection<LfBus> buses) {
        double mismatch = 0;
        for (LfBus b : buses) {
            mismatch += b.getGenerationTargetP() - b.getLoadTargetP();
        }
        return -mismatch;
    }

    public static void initStateVector(LfNetwork network, EquationSystem<DcVariableType, DcEquationType> equationSystem, VoltageInitializer initializer) {
        double[] x = new double[equationSystem.getIndex().getSortedVariablesToFind().size()];
        for (Variable<DcVariableType> v : equationSystem.getIndex().getSortedVariablesToFind()) {
            switch (v.getType()) {
                case BUS_PHI:
                    x[v.getRow()] = initializer.getAngle(network.getBus(v.getElementNum()));
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
                    network.getBus(v.getElementNum()).setAngle(x[v.getRow()]);
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

    @Override
    public DcLoadFlowResult run() {
        boolean succeeded = run(Collections.emptyList(), Collections.emptyList(), context.getNetwork().getReporter()).getLeft();
        return new DcLoadFlowResult(context.getNetwork(), getActivePowerMismatch(context.getNetwork().getBuses()), succeeded);
    }

    public Pair<Boolean, double[]> run(Collection<LfBus> disabledBuses, Collection<LfBranch> disabledBranches,
                                        Reporter reporter) {
        LfNetwork network = context.getNetwork();
        EquationSystem<DcVariableType, DcEquationType> equationSystem = context.getEquationSystem();
        DcLoadFlowParameters parameters = context.getParameters();
        TargetVector<DcVariableType, DcEquationType> targetVector = context.getTargetVector();

        initStateVector(network, equationSystem, new UniformValueVoltageInitializer());

        Collection<LfBus> remainingBuses = new LinkedHashSet<>(network.getBuses());
        remainingBuses.removeAll(disabledBuses);

        if (parameters.isDistributedSlack()) {
            distributeSlack(remainingBuses, parameters.getBalanceType());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwriten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = targetVector.getArray().clone();

        if (!disabledBuses.isEmpty()) {
            // set buses injections and transformers to 0
            disabledBuses.stream()
                .flatMap(lfBus -> equationSystem.getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                .map(Equation::getColumn)
                .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledBranches.isEmpty()) {
            // set transformer phase shift to 0
            disabledBranches.stream()
                .flatMap(lfBranch -> equationSystem.getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                .map(Equation::getColumn)
                .forEach(column -> targetVectorArray[column] = 0);
        }

        boolean succeeded;
        try {
            context.getJacobianMatrix().solveTransposed(targetVectorArray);
            succeeded = true;
        } catch (MatrixException e) {
            succeeded = false;

            Reports.reportDcLfSolverFailure(reporter, e.getMessage());
            LOGGER.error("Failed to solve linear system for DC load flow", e);
        }

        equationSystem.getStateVector().set(targetVectorArray);
        updateNetwork(network, equationSystem, targetVectorArray);

        // set all calculated voltages to NaN
        if (parameters.isSetVToNan()) {
            for (LfBus bus : network.getBuses()) {
                bus.setV(Double.NaN);
            }
        }

        Reports.reportDcLfComplete(reporter, succeeded);
        LOGGER.info("DC load flow completed (succeed={})", succeeded);

        return Pair.of(succeeded, targetVectorArray);
    }

    public static <T> List<DcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, DcLoadFlowParameters parameters, Reporter reporter) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reporter)
                .stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (DcLoadFlowContext context = new DcLoadFlowContext(n, parameters)) {
                            return new DcLoadFlowEngine(context)
                                    .run();
                        }
                    }

                    return new DcLoadFlowResult(n, getActivePowerMismatch(n.getBuses()), false);
                })
                .collect(Collectors.toList());
    }
}
