/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.lf.LoadFlowEngine;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.DisabledNetwork;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowEngine implements LoadFlowEngine<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowEngine.class);

    private final DcLoadFlowContext context;

    public DcLoadFlowEngine(DcLoadFlowContext context) {
        this.context = Objects.requireNonNull(context);
    }

    private static class RunningContext {

        private boolean lastSolverSuccess;

        private int outerLoopTotalIterations = 0;

        private OuterLoopResult lastOuterLoopResult = OuterLoopResult.stable();
    }

    @Override
    public DcLoadFlowContext getContext() {
        return context;
    }

    public static void distributeSlack(LfNetwork network, Collection<LfBus> buses, LoadFlowParameters.BalanceType balanceType, boolean useActiveLimits) {
        double mismatch = getActivePowerMismatch(buses);
        ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(balanceType, false, useActiveLimits);
        activePowerDistribution.run(network.getReferenceGenerator(), buses, mismatch);
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

    private boolean runOuterLoop(DcOuterLoop outerLoop, DcOuterLoopContext outerLoopContext, RunningContext runningContext) {
        ReportNode olReportNode = Reports.createOuterLoopReporter(outerLoopContext.getNetwork().getReportNode(), outerLoop.getName());
        OuterLoopStatus outerLoopStatus;
        int outerLoopIteration = 0;
        boolean success = true;

        // re-run linear system solving until stabilization
        do {
            // check outer loop status
            outerLoopContext.setIteration(outerLoopIteration);
            outerLoopContext.setLoadFlowContext(context);
            outerLoopContext.setOuterLoopTotalIterations(runningContext.outerLoopTotalIterations);
            outerLoopStatus = outerLoop.check(outerLoopContext, olReportNode).status();

            if (outerLoopStatus == OuterLoopStatus.UNSTABLE) {
                LOGGER.debug("Start outer loop '{}' iteration {}", outerLoop.getName(), outerLoopStatus);

                // if not yet stable, restart linear system solving
                double[] targetVectorArray = context.getTargetVector().getArray().clone();
                runningContext.lastSolverSuccess = solve(targetVectorArray, context.getJacobianMatrix(), olReportNode);

                if (runningContext.lastSolverSuccess) {
                    context.getEquationSystem().getStateVector().set(targetVectorArray);
                    updateNetwork(outerLoopContext.getNetwork(), context.getEquationSystem(), targetVectorArray);
                }

                outerLoopIteration++;
                runningContext.outerLoopTotalIterations++;
            }
        } while (outerLoopStatus == OuterLoopStatus.UNSTABLE
                && runningContext.lastSolverSuccess
                && runningContext.outerLoopTotalIterations < context.getParameters().getMaxOuterLoopIterations());

        return success;
    }

    public static boolean solve(double[] targetVectorArray,
                                JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix,
                                ReportNode reportNode) {
        try {
            jacobianMatrix.solveTransposed(targetVectorArray);
            return true;
        } catch (MatrixException e) {
            Reports.reportDcLfSolverFailure(reportNode, e.getMessage());
            LOGGER.error("Failed to solve linear system for DC load flow", e);
            return false;
        }
    }

    public DcLoadFlowResult run() {
        LfNetwork network = context.getNetwork();
        ReportNode reportNode = network.getReportNode();
        EquationSystem<DcVariableType, DcEquationType> equationSystem = context.getEquationSystem();
        DcLoadFlowParameters parameters = context.getParameters();
        TargetVector<DcVariableType, DcEquationType> targetVector = context.getTargetVector();
        RunningContext runningContext = new RunningContext();

        // outer loop initialization
        List<Pair<DcOuterLoop, DcOuterLoopContext>> outerLoopsAndContexts = new ArrayList<>();

        if (parameters.getNetworkParameters().isPhaseControl()) {
            DcIncrementalPhaseControlOuterLoop phaseShifterControlOuterLoop = new DcIncrementalPhaseControlOuterLoop();
            DcOuterLoopContext phaseShifterControlOuterLoopContext = new DcOuterLoopContext(network);
            outerLoopsAndContexts.add(Pair.of(phaseShifterControlOuterLoop, phaseShifterControlOuterLoopContext));
            phaseShifterControlOuterLoop.initialize(phaseShifterControlOuterLoopContext);
        }

        if (parameters.isAreaInterchangeControl() && network.hasArea()) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(parameters.getBalanceType(), false, parameters.getNetworkParameters().isUseActiveLimits());
            DcAreaInterchangeControlControlOuterLoop areaInterchangeControlOuterLoop = new DcAreaInterchangeControlControlOuterLoop(activePowerDistribution, parameters.getSlackBusPMaxMismatch(), parameters.getAreaInterchangePMaxMismatch());
            DcOuterLoopContext areaInterchangeControlOuterLoopContext = new DcOuterLoopContext(network);
            outerLoopsAndContexts.add(Pair.of(areaInterchangeControlOuterLoop, areaInterchangeControlOuterLoopContext));
            areaInterchangeControlOuterLoop.initialize(areaInterchangeControlOuterLoopContext);
        }

        initStateVector(network, equationSystem, new UniformValueVoltageInitializer());

        if (parameters.isDistributedSlack() || parameters.isAreaInterchangeControl() && !network.hasArea()) {
            distributeSlack(network, network.getBuses(), parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because JacobianMatrix.solveTransposed take as an input the second member
        // and reuse the array to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = targetVector.getArray().clone();

        // First linear system solution
        runningContext.lastSolverSuccess = solve(targetVectorArray, context.getJacobianMatrix(), reportNode);

        equationSystem.getStateVector().set(targetVectorArray);
        updateNetwork(network, equationSystem, targetVectorArray);

        // continue with outer loops only if solver succeed
        if (runningContext.lastSolverSuccess) {
            do {
                // outer loops are nested: innermost loop first in the list, outermost loop last
                for (var outerLoopAndContext : outerLoopsAndContexts) {
                    runOuterLoop(outerLoopAndContext.getLeft(), outerLoopAndContext.getRight(), runningContext);

                    // continue with next outer loop only if:
                    // - last solver run succeed,
                    // - last OuterLoopStatus is not FAILED
                    // - we have not reached max number of outer loop iteration
                    if (!runningContext.lastSolverSuccess
                            || runningContext.lastOuterLoopResult.status() == OuterLoopStatus.FAILED
                            || runningContext.outerLoopTotalIterations >= context.getParameters().getMaxOuterLoopIterations()) {
                        break;
                    }
                }
            } while (!runningContext.lastSolverSuccess
                    && runningContext.lastOuterLoopResult.status() != OuterLoopStatus.FAILED
                    && runningContext.outerLoopTotalIterations < context.getParameters().getMaxOuterLoopIterations());
        }

        // set all calculated voltages to NaN
        if (parameters.isSetVToNan()) {
            for (LfBus bus : network.getBuses()) {
                bus.setV(Double.NaN);
            }
        }

        Reports.reportDcLfComplete(reportNode, runningContext.lastSolverSuccess);
        LOGGER.info("DC load flow completed (success={})", runningContext.lastSolverSuccess);

        return new DcLoadFlowResult(context.getNetwork(), getActivePowerMismatch(context.getNetwork().getBuses()), runningContext.lastSolverSuccess);
    }

    public static <T> List<DcLoadFlowResult> run(T network, LfNetworkLoader<T> networkLoader, DcLoadFlowParameters parameters, ReportNode reportNode) {
        return LfNetwork.load(network, networkLoader, parameters.getNetworkParameters(), reportNode)
                .stream()
                .map(n -> {
                    if (n.getValidity() == LfNetwork.Validity.VALID) {
                        try (DcLoadFlowContext context = new DcLoadFlowContext(n, parameters)) {
                            return new DcLoadFlowEngine(context)
                                    .run();
                        }
                    }

                    return new DcLoadFlowResult(n, getActivePowerMismatch(n.getBuses()), false);
                })
                .toList();
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling and that do not
     * update the state vector and the network at the end (because we don't need it to just evaluate a few equations).
     */
    public static double[] run(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, ReportNode reportNode) {
        // FIXME : add AIC here ?

        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            distributeSlack(loadFlowContext.getNetwork(), remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwritten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();

        if (!disabledNetwork.getBuses().isEmpty()) {
            // set buses injections and transformers to 0
            disabledNetwork.getBuses().stream()
                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        boolean succeeded = solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reportNode);
        if (!succeeded) {
            throw new PowsyblException("DC solver failed");
        }

        return targetVectorArray; // now contains dx
    }
}
