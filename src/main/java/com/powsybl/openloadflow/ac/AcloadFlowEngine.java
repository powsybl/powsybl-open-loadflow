/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.AcOuterLoopGroupContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.outerloop.AbstractACOuterLoopGroup;
import com.powsybl.openloadflow.ac.outerloop.AcOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.MainAcOuterLoopGroup;
import com.powsybl.openloadflow.ac.solver.*;
import com.powsybl.openloadflow.lf.LoadFlowEngine;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class AcloadFlowEngine implements LoadFlowEngine<AcVariableType, AcEquationType, AcLoadFlowParameters, AcLoadFlowResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcloadFlowEngine.class);

    private final AcLoadFlowContext context;

    private final AcSolverFactory solverFactory;

    public AcloadFlowEngine(AcLoadFlowContext context) {
        this(context, new NewtonRaphsonFactory());
    }

    public AcloadFlowEngine(AcLoadFlowContext context, AcSolverFactory solverFactory) {
        this.context = Objects.requireNonNull(context);
        this.solverFactory = Objects.requireNonNull(solverFactory);
    }

    @Override
    public AcLoadFlowContext getContext() {
        return context;
    }

    public static class RunningContext {

        private final VoltageInitializer firstInitializer;

        private AcSolverResult lastSolverResult;

        private final Map<String, MutableInt> outerLoopIterationByType = new HashMap<>();

        private int outerLoopTotalIterations = 0;

        private final MutableInt nrTotalIterations = new MutableInt();

        private OuterLoopStatus lastOuterLoopStatus;

        private double distributedActivePower;

        public RunningContext(VoltageInitializer firstInitializer) {
            this.firstInitializer = firstInitializer;
        }

        public Integer getNrTotalIterationCount() {
            return nrTotalIterations.getValue();
        }

        public void incNrTotalIterationCount(int newIterationCount) {
            nrTotalIterations.add(newIterationCount);
        }

        public AcSolverResult getLastSolverResult() {
            return lastSolverResult;
        }

        public void setLastSolverResult(AcSolverResult lastSolverResult) {
            this.lastSolverResult = lastSolverResult;
        }

        public OuterLoopStatus getLastOuterLoopStatus() {
            return lastOuterLoopStatus;
        }

        public void setLastOuterLoopStatus(OuterLoopStatus lastOuterLoopStatus) {
            this.lastOuterLoopStatus = lastOuterLoopStatus;
        }

        public int getOuterLoopTotalIterationCount() {
            return outerLoopTotalIterations;
        }

        public void incrementOuterLoopTotalIterationCount() {
            outerLoopTotalIterations += 1;
        }

        public Map<String, MutableInt> getOuterLoopIterationByType() {
            return outerLoopIterationByType;
        }

        public double getDistributedActivePower() {
            return distributedActivePower;
        }

        public void addDistributedActivePower(double distributedActivePower) {
            this.distributedActivePower += distributedActivePower;
        }

        public VoltageInitializer getFirstInitializer() {
            return firstInitializer;
        }
    }

    @Override
    public AcLoadFlowResult run() {
        LOGGER.info("Start AC loadflow on network {}", context.getNetwork());

        VoltageInitializer voltageInitializer = context.getParameters().getVoltageInitializer();
        // in case of a DC voltage initializer, an DC equation system in created and equations are attached
        // to the network. It is important that DC init is done before AC equation system is created by
        // calling ACLoadContext.getEquationSystem to avoid DC equations overwrite AC ones in the network.
        voltageInitializer.prepare(context.getNetwork());

        RunningContext runningContext = new RunningContext(voltageInitializer);
        AcSolver solver = solverFactory.create(context.getNetwork(),
                                               context.getParameters(),
                                               context.getEquationSystem(),
                                               context.getJacobianMatrix(),
                                               context.getTargetVector(),
                                               context.getEquationVector());

        List<AcOuterLoop> outerLoops = context.getParameters().getOuterLoops();

        ReportNode nrReportNode = context.getNetwork().getReportNode();
        if (context.getParameters().isDetailedReport()) {
            nrReportNode = Reports.createDetailedSolverReporter(nrReportNode,
                    solver.getName(),
                    context.getNetwork().getNumCC(),
                    context.getNetwork().getNumSC());
        }

        AbstractACOuterLoopGroup group = new MainAcOuterLoopGroup(outerLoops);
        AcOuterLoopGroupContext outerLoopGroupContext = new AcOuterLoopGroupContext(context.getNetwork(), runningContext, solver);
        outerLoopGroupContext.setLoadFlowContext(context);
        OuterLoopStatus outerLoopFinalStatus = group.runOuterLoops(outerLoopGroupContext, nrReportNode);

        AcLoadFlowResult result = new AcLoadFlowResult(context.getNetwork(),
                                                       runningContext.outerLoopTotalIterations,
                                                       runningContext.nrTotalIterations.getValue(),
                                                       runningContext.lastSolverResult.getStatus(),
                                                       outerLoopFinalStatus,
                                                       runningContext.lastSolverResult.getSlackBusActivePowerMismatch(),
                                                       runningContext.getDistributedActivePower()
                                                       );

        LOGGER.info("Ac loadflow complete on network {} (result={})", context.getNetwork(), result);

        Reports.reportAcLfComplete(context.getNetwork().getReportNode(), result.isSuccess(), result.getSolverStatus().name(), result.getOuterLoopStatus().name());

        context.setResult(result);

        return result;
    }

    public static List<AcLoadFlowResult> run(List<LfNetwork> lfNetworks, AcLoadFlowParameters parameters) {
        return lfNetworks.stream()
                .map(n -> {
                    if (n.isValid()) {
                        try (AcLoadFlowContext context = new AcLoadFlowContext(n, parameters)) {
                            return new AcloadFlowEngine(context, parameters.getSolverFactory())
                                    .run();
                        }
                    }
                    return AcLoadFlowResult.createNoCalculationResult(n);
                })
                .toList();
    }
}
