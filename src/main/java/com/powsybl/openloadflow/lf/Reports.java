/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeAdder;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.openloadflow.lf.outerloop.DiscreteControllerChange;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.NetworkReports;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Reports {

    private static final String ITERATION = "iteration";
    private static final String ITERATION_COUNT = "iterationCount";
    private static final String NETWORK_ID = "networkId";
    private static final String SOLVER_NAME = "solverName";
    private static final String BUS_ID = "busId";
    private static final String CONTINGENCY_ID = "contingencyId";
    private static final String HVDC_ID = "hvdcId";
    private static final String CONTROLLER_STATION_ID = "controllerStationId";
    private static final String NON_CONTROLLER_STATION_ID = "nonControllerStationId";
    private static final String MISMATCH = "mismatch";
    private static final String TARGET_V = "targetV";

    public static final String POST_CONTINGENCY_SIMULATION_KEY = "olf.postContingencySimulation";

    public record BusReport(String busId, double mismatch, double nominalV, double v, double phi, double p, double q) {
    }

    private Reports() {
    }

    public static void reportMismatchDistributionFailure(ReportNode reportNode, double remainingMismatch) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.mismatchDistributionFailure")
                .withTypedValue(MISMATCH, remainingMismatch, NetworkReports.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportResidualDistributionMismatch(ReportNode reportNode, double remainingMismatch) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.residualDistributionMismatch")
                .withTypedValue(MISMATCH, remainingMismatch, NetworkReports.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.DEBUG_SEVERITY)
                .add();
    }

    public static ReportNode reportAreaInterchangeControlDistributionFailure(ReportNode reportNode) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.areaInterchangeControlDistributionFailure")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAicAreaDistributionMismatch(ReportNode reportNode, String area, double mismatch, boolean isInterchangeDistribution) {
        String messageTemplate = isInterchangeDistribution ? "olf.areaInterchangeControlAreaMismatch" : "olf.areaInterchangeControlAreaSlackMismatch";
        reportNode.newReportNode()
                .withMessageTemplate(messageTemplate)
                .withUntypedValue("area", area)
                .withTypedValue(MISMATCH, mismatch, NetworkReports.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAicAreaDistributionSuccess(ReportNode reportNode, String area, double mismatch, int iterationCount, boolean isInterchangeDistribution) {
        String messageTemplate = isInterchangeDistribution ? "olf.areaInterchangeControlAreaDistributionSuccess" : "olf.areaInterchangeControlSlackDistributionSuccess";
        reportNode.newReportNode()
                .withMessageTemplate(messageTemplate)
                .withUntypedValue("area", area)
                .withTypedValue(MISMATCH, mismatch, NetworkReports.MISMATCH_TYPED_VALUE)
                .withUntypedValue(ITERATION_COUNT, iterationCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode reportPvToPqBuses(ReportNode reportNode, int pvToPqBusCount, int remainingPvBusCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.pvToPqBuses")
                .withUntypedValue("pvToPqBusCount", pvToPqBusCount)
                .withUntypedValue("remainingPvBusCount", remainingPvBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportPvToPqMaxQ(ReportNode reportNode,
                                        LfBus controllerBus,
                                        double busQ,
                                        double maxQ,
                                        boolean log,
                                        Logger logger) {
        ReportNode newNode = reportNode.newReportNode()
                .withMessageTemplate("olf.pvToPqMaxQ")
                .withUntypedValue(BUS_ID, controllerBus.getId())
                .withTypedValue("busQ", busQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("maxQ", maxQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
        if (log) {
            logger.trace(newNode.getMessage());
        }
    }

    public static void reportPvToPqMinQ(ReportNode reportNode,
                                        LfBus controllerBus,
                                        double busQ,
                                        double minQ,
                                        boolean log,
                                        Logger logger) {
        ReportNode newNode = reportNode.newReportNode()
                .withMessageTemplate("olf.PvToPqMinQ")
                .withUntypedValue(BUS_ID, controllerBus.getId())
                .withTypedValue("busQ", busQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("minQ", minQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
        if (log) {
            logger.trace(newNode.getMessage());
        }
    }

    public static void reportPvToPqMinRealisticV(ReportNode reportNode,
                                                 LfBus controllerBus,
                                                 double targetQ,
                                                 double minRealisticV,
                                                 boolean log,
                                                 Logger logger) {
        ReportNode newNode = reportNode.newReportNode()
                .withMessageTemplate("olf.PvToPqMinRealisticV")
                .withUntypedValue(BUS_ID, controllerBus.getId())
                .withTypedValue("targetQ", targetQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("minRealisticV", minRealisticV * controllerBus.getNominalV(), TypedValue.VOLTAGE)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
        if (log) {
            logger.trace(newNode.getMessage());
        }
    }

    public static void reportPvToPqMaxRealisticV(ReportNode reportNode,
                                                 LfBus controllerBus,
                                                 double targetQ,
                                                 double maxRealisticV,
                                                 boolean log,
                                                 Logger logger) {
        ReportNode newNode = reportNode.newReportNode()
                .withMessageTemplate("olf.PvToPqMaxRealisticV")
                .withUntypedValue("busId", controllerBus.getId())
                .withTypedValue("targetQ", targetQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("maxRealisticV", maxRealisticV * controllerBus.getNominalV(), TypedValue.VOLTAGE)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
        if (log) {
            logger.trace(newNode.getMessage());
        }
    }

    public static ReportNode reportPqToPvBuses(ReportNode reportNode, int pqToPvBusCount, int blockedPqBusCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.PqToPvBuses")
                .withUntypedValue("pqToPvBusCount", pqToPvBusCount)
                .withUntypedValue("blockedPqBusCount", blockedPqBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode createRootReportPvPqSwitchLimit(ReportNode firstRootReportNode, LfBus controllerBus, int limit, boolean log, Logger logger) {
        ReportNode result = ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.pvPqSwitchLimit")
                .withUntypedValue("busId", controllerBus.getId())
                .withUntypedValue("limit", limit)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
        if (log) {
            logger.trace(result.getMessage());
        }
        return result;
    }

    public static ReportNode createRootReportPqToPvBusMaxLimit(ReportNode firstRootReportNode, LfBus controllerBus, LfBus controlledBus, double targetV, boolean log, Logger logger) {
        ReportNode result = ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.pqToPvBusMaxLimit")
                .withUntypedValue("busId", controllerBus.getId())
                // busV and targetV need a higher precision than usual Voltage rounding to understand
                // the difference. Their unit is not given to avoid a too high formatting based on Unit
                .withUntypedValue("busV", controlledBus.getV() * controlledBus.getNominalV())
                .withUntypedValue(TARGET_V, targetV * controlledBus.getNominalV())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
        if (log) {
            logger.trace(result.getMessage());
        }
        return result;
    }

    public static ReportNode createRootReportPqToPvBusMinLimit(ReportNode firstRootReportNode, LfBus controllerBus, LfBus controlledBus, double targetV, boolean log, Logger logger) {
        ReportNode result = ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.pqToPvBusMinLimit")
                .withUntypedValue("busId", controllerBus.getId())
                // busV and targetV need a higher precision than usual Voltage rounding to understand
                // the difference. Their unit is not given to avoid a too high formatting based on Unit
                .withUntypedValue("busV", controlledBus.getV() * controlledBus.getNominalV())
                .withUntypedValue(TARGET_V, targetV * controlledBus.getNominalV())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
        if (log) {
            logger.trace(result.getMessage());
        }
        return result;
    }

    public static void reportBusForcedToBePv(ReportNode reportNode, String busId) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.busForcedToBePv")
                .withUntypedValue(BUS_ID, busId)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportBusesWithUpdatedQLimits(ReportNode reportNode, int numBusesWithUpdatedQLimits) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.busWithUpdatedQLimits")
                .withUntypedValue("numBusesWithUpdatedQLimits", numBusesWithUpdatedQLimits)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode reportReactiveControllerBusesToPqBuses(ReportNode reportNode, int remoteReactivePowerControllerBusToPqCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.reactiveControllerBusesToPqBuses")
                .withUntypedValue("remoteReactivePowerControllerBusToPqCount", remoteReactivePowerControllerBusToPqCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode createRootReportReactiveControllerBusesToPqMaxQ(ReportNode firstRootReportNode,
                                                                             LfBus controllerBus,
                                                                             double busQ,
                                                                             double maxQ,
                                                                             boolean log,
                                                                             Logger logger) {
        ReportNode result = ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.reactiveControllerBusesToPqMaxQ")
                .withUntypedValue("busId", controllerBus.getId())
                .withTypedValue("busQ", busQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("maxQ", maxQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
        if (log) {
            logger.trace(result.getMessage());
        }
        return result;
    }

    public static ReportNode createRootReportReactiveControllerBusesToPqMinQ(ReportNode firstRootReportNode,
                                                                             LfBus controllerBus,
                                                                             double busQ,
                                                                             double minQ,
                                                                             boolean log,
                                                                             Logger logger) {
        ReportNode result = ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.reactiveControllerBusesToPqMinQ")
                .withUntypedValue("busId", controllerBus.getId())
                .withTypedValue("busQ", busQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withTypedValue("minQ", minQ * PerUnit.SB, TypedValue.REACTIVE_POWER)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
        if (log) {
            logger.trace(result.getMessage());
        }
        return result;
    }

    public static void reportStandByAutomatonActivation(ReportNode reportNode, String busId, double newTargetV) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.standByAutomatonActivation")
                .withUntypedValue(BUS_ID, busId)
                .withUntypedValue("newTargetV", newTargetV)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode reportCurrentLimiterPstsChangedTaps(ReportNode reportNode, int numOfCurrentLimiterPstsThatChangedTap) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.currentLimiterPstsChangedTaps")
                .withUntypedValue("numOfCurrentLimiterPstsThatChangedTap", numOfCurrentLimiterPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode reportActivePowerControlPstsChangedTaps(ReportNode reportNode, int numOfActivePowerControlPstsThatChangedTap) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.activePowerControlPstsChangedTaps")
                .withUntypedValue("numOfActivePowerControlPstsThatChangedTap", numOfActivePowerControlPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlBusesOutsideDeadband(ReportNode reportNode, int numTransformerControlBusesOutsideDeadband) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlBusesOutsideDeadband")
                .withUntypedValue("numTransformerControlBusesOutsideDeadband", numTransformerControlBusesOutsideDeadband)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlBranchesOutsideDeadband(ReportNode reportNode, int numTransformerControlBranchesOutsideDeadband) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlBranchesOutsideDeadband")
                .withUntypedValue("numTransformerControlBranchesOutsideDeadband", numTransformerControlBranchesOutsideDeadband)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static ReportNode reportTransformerControlChangedTaps(ReportNode reportNode, int numTransformerControlAdjusted) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlChangedTaps")
                .withUntypedValue("numTransformerControlAdjusted", numTransformerControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlChangedTapsDetail(ReportNode reportNode, DiscreteControllerChange change) {
        ReportNodeAdder adder = reportNode.newReportNode();
        change.getSide().ifPresentOrElse(
            side -> adder.withMessageTemplate("olf.transformerControlChangedTapsDetailWithSide")
                    .withUntypedValue("side", side.getNum()),
            () -> adder.withMessageTemplate("olf.transformerControlChangedTapsDetail"));
        adder.withUntypedValue("transformerId", change.getElementId())
                .withUntypedValue("oldTapPosition", change.getOldPosition())
                .withUntypedValue("newTapPosition", change.getNewPosition())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static ReportNode reportTransformerControlTapLimit(ReportNode reportNode, int numTransformerControlTapLimit) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlTapLimit")
                .withUntypedValue("numTransformerControlTapLimit", numTransformerControlTapLimit)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlTapLimitDetail(ReportNode reportNode, String tranformerId) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlTapLimitDetail")
                .withUntypedValue("transformerId", tranformerId)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static ReportNode reportShuntVoltageControlChangedSection(ReportNode reportNode, int numShuntVoltageControlAdjusted) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.shuntVoltageControlChangedSection")
                .withUntypedValue("numShuntVoltageControlAdjusted", numShuntVoltageControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportShuntVoltageControlChangedSectionDetail(ReportNode reportNode, DiscreteControllerChange change) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.shuntVoltageControlChangedSectionDetail")
                .withUntypedValue("shuntId", change.getElementId())
                .withUntypedValue("oldPosition", change.getOldPosition())
                .withUntypedValue("newPosition", change.getNewPosition())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static void reportUnsuccessfulOuterLoop(ReportNode reportNode, String outerLoopStatus) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.outerLoopStatus")
                .withUntypedValue("outerLoopStatus", outerLoopStatus)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportMaxOuterLoopIterations(ReportNode reportNode, int iterationCount, boolean withLog, Logger logger) {
        ReportNode added = reportNode.newReportNode()
                .withMessageTemplate("olf.maxOuterLoopIterations")
                .withUntypedValue("outerLoopIterationCount", iterationCount)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
        if (withLog) {
            logger.error(added.getMessage());
        }
    }

    public static void reportDcLfSolverFailure(ReportNode reportNode, String errorMessage) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.dcLfFailure")
                .withUntypedValue("errorMessage", errorMessage)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportDcLfComplete(ReportNode reportNode, boolean succeeded, String outerloopStatus) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.dcLfComplete")
                .withUntypedValue("succeeded", succeeded)
                .withUntypedValue("outerloopStatus", outerloopStatus)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportAcLfCompleteWithSuccess(ReportNode reportNode, String solverStatus, String outerloopStatus) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.acLfCompleteWithSuccess")
                .withUntypedValue("solverStatus", solverStatus)
                .withUntypedValue("outerloopStatus", outerloopStatus)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportAcLfCompleteWithError(ReportNode reportNode, String solverStatus, String outerloopStatus) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.acLfCompleteWithError")
                .withUntypedValue("solverStatus", solverStatus)
                .withUntypedValue("outerloopStatus", outerloopStatus)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static ReportNode createLoadFlowReporter(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode().withMessageTemplate("olf.loadFlow")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createOuterLoopReporter(ReportNode reportNode, String outerLoopType) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.OuterLoop")
                .withUntypedValue("outerLoopType", outerLoopType)
                .add();
    }

    public static ReportNode createOuterLoopIterationReporter(ReportNode reportNode, int outerLoopIteration) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.OuterLoopIteration")
                .withUntypedValue("outerLoopIteration", outerLoopIteration)
                .add();
    }

    public static ReportNode createRootOuterLoopIterationReporter(ReportNode firstRootReportNode, int outerLoopIteration) {
        return ReportNode.newRootReportNode()
            .withLocale(firstRootReportNode.getTreeContext().getLocale())
            .withAllResourceBundlesFromClasspath()
            .withMessageTemplate("olf.OuterLoopIteration")
            .withUntypedValue("outerLoopIteration", outerLoopIteration)
            .build();
    }

    public static ReportNode createSensitivityAnalysis(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.sensitivityAnalysis")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createAcSecurityAnalysis(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.acSecurityAnalysis")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createDcSecurityAnalysis(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.dcSecurityAnalysis")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createWoodburyDcSecurityAnalysis(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.woodburyDcSecurityAnalysis")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createPreContingencySimulation(ReportNode reportNode) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.preContingencySimulation")
                .add();
    }

    public static ReportNode createPostContingencySimulation(ReportNode reportNode, String contingencyId) {
        return reportNode.newReportNode()
                .withMessageTemplate(POST_CONTINGENCY_SIMULATION_KEY)
                .withUntypedValue(CONTINGENCY_ID, Objects.toString(contingencyId))
                .add();
    }

    public static ReportNode createOperatorStrategySimulation(ReportNode reportNode, String operatorStrategyId) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.operatorStrategySimulation")
                .withUntypedValue("operatorStrategyId", operatorStrategyId)
                .add();
    }

    public static ReportNode createDetailedSolverReporter(ReportNode reportNode, String solverName, int networkNumCc, int networkNumSc) {
        ReportNode subReportNode = createSolverReport(reportNode, solverName, networkNumCc, networkNumSc);
        return createDetailedSolverReporter(subReportNode);
    }

    public static ReportNode createDetailedSolverReporterConnectedComponent(ReportNode reportNode, String solverName, int networkNumCc) {
        ReportNode subReportNode = createSolverReportConnectedComponent(reportNode, solverName, networkNumCc);
        return createDetailedSolverReporter(subReportNode);
    }

    public static ReportNode createDetailedSolverReporter(ReportNode subReportNode) {
        subReportNode.newReportNode()
                .withMessageTemplate("olf.solverNoOuterLoops")
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        return subReportNode;
    }

    public static ReportNode createDetailedSolverReporterOuterLoop(ReportNode reportNode, String solverName, int networkNumCc, int networkNumSc,
                                                                   int outerLoopIteration, String outerLoopType) {
        ReportNode subReportNode = createSolverReport(reportNode, solverName, networkNumCc, networkNumSc);
        return createDetailedSolverReporterOuterLoopConnectedComponent(subReportNode, solverName, outerLoopIteration, outerLoopType);
    }

    public static ReportNode createDetailedSolverReporterOuterLoopConnectedComponent(ReportNode reportNode, String solverName, int networkNumCc,
                                                                                     int outerLoopIteration, String outerLoopType) {
        ReportNode subReportNode = createSolverReportConnectedComponent(reportNode, solverName, networkNumCc);
        return createDetailedSolverReporterOuterLoopConnectedComponent(subReportNode, solverName, outerLoopIteration, outerLoopType);
    }

    public static ReportNode createDetailedSolverReporterOuterLoopConnectedComponent(ReportNode subReportNode, String solverName, int outerLoopIteration, String outerLoopType) {
        subReportNode.newReportNode()
                .withMessageTemplate("olf.solverOuterLoopCurrentType")
                .withUntypedValue(SOLVER_NAME, solverName)
                .withUntypedValue("outerLoopIteration", outerLoopIteration)
                .withUntypedValue("outerLoopType", outerLoopType)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        return subReportNode;
    }

    public static ReportNode createSolverReport(ReportNode reportNode, String solverName, int networkNumCc, int networkNumSc) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.solver")
                .withUntypedValue(NetworkReports.NETWORK_NUM_CC, networkNumCc)
                .withUntypedValue(NetworkReports.NETWORK_NUM_SC, networkNumSc)
                .withUntypedValue(SOLVER_NAME, solverName)
                .add();
    }

    public static ReportNode createSolverReportConnectedComponent(ReportNode reportNode, String solverName, int networkNumCc) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.solverCc")
                .withUntypedValue(NetworkReports.NETWORK_NUM_CC, networkNumCc)
                .withUntypedValue(SOLVER_NAME, solverName)
                .add();
    }

    public static ReportNode createAcMismatchReporter(ReportNode reportNode, int iteration) {
        if (iteration == 0) {
            return reportNode.newReportNode()
                    .withMessageTemplate("olf.mismatchInitial").
                    add();
        } else {
            return reportNode.newReportNode()
                    .withMessageTemplate("olf.mismatchIteration")
                    .withUntypedValue(ITERATION, iteration)
                    .add();
        }
    }

    public static void reportAcSolverError(ReportNode reportNode, String solverName, String error) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.solverError")
                .withUntypedValue(SOLVER_NAME, solverName)
                .withUntypedValue("error", error)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportSolverNorm(ReportNode reportNode, double norm) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.solverNorm")
                .withUntypedValue("norm", norm)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static void reportNewtonRaphsonLargestMismatches(ReportNode reportNode, String acEquationType, BusReport busReport) {
        String mismatchUnit;
        double mismatchUnitConverter;
        switch (acEquationType) {
            case "P" -> {
                mismatchUnit = "MW";
                mismatchUnitConverter = PerUnit.SB;
            }
            case "Q" -> {
                mismatchUnit = "MVar";
                mismatchUnitConverter = PerUnit.SB;
            }
            default -> {
                mismatchUnit = "p.u.";
                mismatchUnitConverter = 1.0;
            }
        }

        ReportNode subReportNode = reportNode.newReportNode()
                .withMessageTemplate("olf.NRMismatch")
                .withUntypedValue("equationType", acEquationType)
                .withTypedValue(MISMATCH, mismatchUnitConverter * busReport.mismatch(), NetworkReports.MISMATCH_TYPED_VALUE)
                .withUntypedValue("mismatchUnit", mismatchUnit)
                .add();

        subReportNode.newReportNode()
                .withMessageTemplate("olf.NRMismatchBusInfo")
                .withUntypedValue(BUS_ID, busReport.busId())
                .withUntypedValue("busNominalV", busReport.nominalV())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();

        subReportNode.newReportNode()
                .withMessageTemplate("olf.NRMismatchBusV")
                .withUntypedValue("busV", busReport.v())
                .withUntypedValue("busPhi", busReport.phi())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();

        subReportNode.newReportNode()
                .withMessageTemplate("olf.NRMismatchBusInjection")
                .withUntypedValue("busP", busReport.p())
                .withUntypedValue("busQ", busReport.q())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static void reportLineSearchStateVectorScaling(ReportNode reportNode, double stepSize) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.lineSearchStateVectorScaling")
                .withUntypedValue("stepSize", stepSize)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportMaxVoltageChangeStateVectorScaling(ReportNode reportNode, double stepSize, int vCutCount, int phiCutCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.maxVoltageChangeStateVectorScaling")
                .withUntypedValue("stepSize", stepSize)
                .withUntypedValue("vCutCount", vCutCount)
                .withUntypedValue("phiCutCount", phiCutCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportNewtonRaphsonBusesOutOfRealisticVoltageRange(ReportNode reportNode, Map<String, Double> busesOutOfRealisticVoltageRange,
                                                                          double minRealisticVoltage, double maxRealisticVoltage) {
        ReportNode voltageOutOfRangeReport = reportNode.newReportNode()
                .withMessageTemplate("olf.newtonRaphsonBusesOutOfRealisticVoltageRange")
                .withUntypedValue("busCountOutOfRealisticVoltageRange", busesOutOfRealisticVoltageRange.size())
                .withUntypedValue("minRealisticVoltage", minRealisticVoltage)
                .withUntypedValue("maxRealisticVoltage", maxRealisticVoltage)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();

        busesOutOfRealisticVoltageRange.forEach((id, voltage) -> voltageOutOfRangeReport.newReportNode()
            .withMessageTemplate("olf.newtonRaphsonBusesOutOfRealisticVoltageRangeDetails")
            .withUntypedValue(BUS_ID, id)
            .withUntypedValue("voltage", voltage)
            .withSeverity(TypedValue.TRACE_SEVERITY)
            .add());
    }

    public static void reportAcEmulationDisabledInWoodburyDcSecurityAnalysis(ReportNode reportNode) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.acEmulationDisabledInWoodburyDcSecurityAnalysis")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportContingencyActivePowerLossDistribution(ReportNode reportNode, double mismatch, double remaining) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.contingencyActivePowerLossDistribution")
                .withUntypedValue(MISMATCH, mismatch)
                .withUntypedValue("distributed", mismatch - remaining)
                .withUntypedValue("remaining", remaining)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportAcEmulationFromLinearToSaturated(ReportNode reportNode, String hvdcId, String controllerStationId, String nonControllerStationId, double pMax, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.acEmulationFromLinearToSaturated")
                .withUntypedValue(HVDC_ID, hvdcId)
                .withUntypedValue(CONTROLLER_STATION_ID, controllerStationId)
                .withUntypedValue(NON_CONTROLLER_STATION_ID, nonControllerStationId)
                .withUntypedValue("pMax", pMax)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportAcEmulationSaturationSideSwitch(ReportNode reportNode, String hvdcId, String newControllerStationId, String newNonControllerStationId, double pMax, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.acEmulationSaturationSideSwitch")
                .withUntypedValue(HVDC_ID, hvdcId)
                .withUntypedValue(CONTROLLER_STATION_ID, newControllerStationId)
                .withUntypedValue(NON_CONTROLLER_STATION_ID, newNonControllerStationId)
                .withUntypedValue("pMax", pMax)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportAcEmulationBackToLinear(ReportNode reportNode, String hvdcId, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.acEmulationBackToLinear")
                .withUntypedValue(HVDC_ID, hvdcId)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportFreezeHvdc(ReportNode reportNode, String hvdcId, String controllerStationId, String nonControllerStationId, double setPoint, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.freezeHvdc")
                .withUntypedValue(HVDC_ID, hvdcId)
                .withUntypedValue(CONTROLLER_STATION_ID, controllerStationId)
                .withUntypedValue(NON_CONTROLLER_STATION_ID, nonControllerStationId)
                .withUntypedValue("setPoint", setPoint)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportNoFreezeBecauseHvdcAction(ReportNode reportNode, String hvdcId, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.noFreezeBecauseHvdcAction")
                .withUntypedValue(HVDC_ID, hvdcId)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportUnfreezeHvdc(ReportNode reportNode, String hvdcID, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.unfreezeHvdc")
                .withUntypedValue("ID", hvdcID)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        logger.info(node.getMessage());
    }

    public static ReportNode createRootThreadReport(ReportNode firstRootReport) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReport.getTreeContext().getLocale())
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("olf.threadRoot")
                .build();
    }

    public static ReportNode reportFixTargetVoltage(ReportNode reportNode) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.fixVoltageTargets")
                .add();
    }

    public static ReportNode reportIncompatibleVoltageTarget(ReportNode reportNode, String idBus1, String idBus2, double indicator, String deactivatedElements) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.incompatibleVoltageTarget")
                .withUntypedValue("bus1", idBus1)
                .withUntypedValue("bus2", idBus2)
                .withUntypedValue("indicator", indicator)
                .withUntypedValue("elements", deactivatedElements)
                .add();
    }

}
