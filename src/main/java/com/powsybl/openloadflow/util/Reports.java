/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Reports {

    public static final String NETWORK_NUM_CC = "networkNumCc";
    public static final String NETWORK_NUM_SC = "networkNumSc";
    private static final String ITERATION = "iteration";
    private static final String ITERATION_COUNT = "iterationCount";
    private static final String NETWORK_ID = "networkId";
    private static final String IMPACTED_GENERATOR_COUNT = "impactedGeneratorCount";

    private static final String IMPACTED_TRANSFORMER_COUNT = "impactedTransformerCount";

    private static final String IMPACTED_SHUNT_COUNT = "impactedShuntCount";
    private static final String BUS_ID = "busId";
    private static final String GENERATORS_ID = "generatorIds";
    private static final String CONTROLLER_BUS_ID = "controllerBusId";
    private static final String CONTROLLED_BUS_ID = "controlledBusId";
    private static final String ACTION_ID = "actionId";
    private static final String CONTINGENCY_ID = "contingencyId";
    public static final String MISMATCH = "mismatch";

    public static final String LF_NETWORK_KEY = "olf.lfNetwork";
    public static final String POST_CONTINGENCY_SIMULATION_KEY = "olf.postContingencySimulation";

    public record BusReport(String busId, double mismatch, double nominalV, double v, double phi, double p, double q) {
    }

    private Reports() {
    }

    public static void reportNetworkSize(ReportNode reportNode, int busCount, int branchCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.networkSize")
                .withUntypedValue("busCount", busCount)
                .withUntypedValue("branchCount", branchCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportNetworkBalance(ReportNode reportNode, double activeGeneration, double activeLoad, double reactiveGeneration, double reactiveLoad) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.networkBalance")
                .withUntypedValue("activeGeneration", activeGeneration)
                .withUntypedValue("activeLoad", activeLoad)
                .withUntypedValue("reactiveGeneration", reactiveGeneration)
                .withUntypedValue("reactiveLoad", reactiveLoad)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportNotUniqueControlledBusKeepingFirstControl(ReportNode reportNode, String generatorIds, String controllerBusId, String controlledBusId, String controlledBusGenId) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.notUniqueControlledBusKeepingFirstControl")
                .withUntypedValue(GENERATORS_ID, generatorIds)
                .withUntypedValue(CONTROLLER_BUS_ID, controllerBusId)
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withUntypedValue("controlledBusGenId", controlledBusGenId)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportNotUniqueControlledBusDisablingControl(ReportNode reportNode, String generatorIds, String controllerBusId, String controlledBusId, String controlledBusGenId) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.notUniqueControlledBusDisablingControl")
                .withUntypedValue(GENERATORS_ID, generatorIds)
                .withUntypedValue(CONTROLLER_BUS_ID, controllerBusId)
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withUntypedValue("controlledBusGenId", controlledBusGenId)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportNotUniqueTargetVControllerBusKeepingFirstControl(ReportNode reportNode, String generatorIds, String controllerBusId, Double keptTargetV, Double rejectedTargetV) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.notUniqueTargetVControllerBusKeepingFirstControl")
                .withUntypedValue(GENERATORS_ID, generatorIds)
                .withUntypedValue(CONTROLLER_BUS_ID, controllerBusId)
                .withUntypedValue("keptTargetV", keptTargetV)
                .withUntypedValue("rejectedTargetV", rejectedTargetV)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportNotUniqueTargetVControllerBusDisablingControl(ReportNode reportNode, String generatorIds, String controllerBusId, Double targetV1, Double targetV2) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.notUniqueTargetVControllerBusDisablingControl")
                .withUntypedValue(GENERATORS_ID, generatorIds)
                .withUntypedValue(CONTROLLER_BUS_ID, controllerBusId)
                .withUntypedValue("targetV1", targetV1)
                .withUntypedValue("targetV2", targetV2)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportControllerShuntAlreadyInVoltageControl(ReportNode reportNode, String controllerShuntId, String controlledBusId) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.controllerShuntAlreadyInVoltageControl")
                .withUntypedValue("controllerShuntId", controllerShuntId)
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportBusAlreadyControlledWithDifferentTargetV(ReportNode reportNode, String controllerBusId, String controlledBusId, String busesId, Double keptTargetV, Double ignoredTargetV) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.busAlreadyControlledWithDifferentTargetV")
                .withUntypedValue(CONTROLLER_BUS_ID, controllerBusId)
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withUntypedValue("busesId", busesId)
                .withUntypedValue("keptTargetV", keptTargetV)
                .withUntypedValue("ignoredTargetV", ignoredTargetV)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportBranchControlledAtBothSides(ReportNode reportNode, String controlledBranchId, String keptSide, String rejectedSide) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.branchControlledAtBothSides")
                .withUntypedValue("controlledBranchId", controlledBranchId)
                .withUntypedValue("keptSide", keptSide)
                .withUntypedValue("rejectedSide", rejectedSide)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportNetworkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled(ReportNode reportNode) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.networkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportComponentsWithoutGenerators(ReportNode reportNode, int deadComponentsCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.componentsWithoutGenerators")
                .withUntypedValue("deadComponentsCount", deadComponentsCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportMismatchDistributionFailure(ReportNode reportNode, double remainingMismatch) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.mismatchDistributionFailure")
                .withTypedValue(MISMATCH, remainingMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportResidualDistributionMismatch(ReportNode reportNode, double remainingMismatch) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.residualDistributionMismatch")
                .withTypedValue(MISMATCH, remainingMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.DEBUG_SEVERITY)
                .add();
    }

    public static void reportMismatchDistributionSuccess(ReportNode reportNode, double slackBusActivePowerMismatch, int iterationCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.mismatchDistributionSuccess")
                .withTypedValue("initialMismatch", slackBusActivePowerMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withUntypedValue(ITERATION_COUNT, iterationCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportAreaNoInterchangeControl(ReportNode reportNode, String area, String reason) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaNoInterchangeControl")
                .withUntypedValue("area", area)
                .withUntypedValue("reason", reason)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportAreaInterchangeControlDistributionFailure(ReportNode reportNode) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.areaInterchangeControlDistributionFailure")
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAreaInterchangeControlAreaMismatch(ReportNode reportNode, String area, double mismatch) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaInterchangeControlAreaMismatch")
                .withUntypedValue("area", area)
                .withTypedValue(MISMATCH, mismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAreaInterchangeControlAreaDistributionSuccess(ReportNode reportNode, String area, double mismatch, int iterationCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaInterchangeControlAreaDistributionSuccess")
                .withUntypedValue("area", area)
                .withTypedValue(MISMATCH, mismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
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
                .withUntypedValue("targetV", targetV * controlledBus.getNominalV())
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
                .withUntypedValue("targetV", targetV * controlledBus.getNominalV())
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

    public static void reportCurrentLimiterPstsChangedTaps(ReportNode reportNode, int numOfCurrentLimiterPstsThatChangedTap) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.currentLimiterPstsChangedTaps")
                .withUntypedValue("numOfCurrentLimiterPstsThatChangedTap", numOfCurrentLimiterPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportActivePowerControlPstsChangedTaps(ReportNode reportNode, int numOfActivePowerControlPstsThatChangedTap) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.activePowerControlPstsChangedTaps")
                .withUntypedValue("numOfActivePowerControlPstsThatChangedTap", numOfActivePowerControlPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlAlreadyExistsWithDifferentTargetV(ReportNode reportNode, String firstControllerId, String newControllerId, String controlledBusId, double vcTargetValue, double targetValue) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlAlreadyExistsWithDifferentTargetV")
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withUntypedValue("firstControllerId", firstControllerId)
                .withUntypedValue("newControllerId", newControllerId)
                .withUntypedValue("vcTargetValue", vcTargetValue)
                .withUntypedValue("targetValue", targetValue)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportTransformerControlAlreadyExistsUpdateDeadband(ReportNode reportNode, String firstControllerId, String newControllerId, String controlledBusId, double newTargetDeadband, Double oldTargetDeadband) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlAlreadyExistsUpdateDeadband")
                .withUntypedValue(CONTROLLED_BUS_ID, controlledBusId)
                .withUntypedValue("firstControllerId", firstControllerId)
                .withUntypedValue("newControllerId", newControllerId)
                .withUntypedValue("newTargetDeadband", newTargetDeadband)
                .withUntypedValue("oldTargetDeadband", oldTargetDeadband == null ? "---" : oldTargetDeadband.toString())
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

    public static void reportTransformerControlChangedTaps(ReportNode reportNode, int numTransformerControlAdjusted) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlChangedTaps")
                .withUntypedValue("numTransformerControlAdjusted", numTransformerControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportTransformerControlTapLimit(ReportNode reportNode, int numTransformerControlTapLimit) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformerControlTapLimit")
                .withUntypedValue("numTransformerControlTapLimit", numTransformerControlTapLimit)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportShuntVoltageControlChangedSection(ReportNode reportNode, int numShuntVoltageControlAdjusted) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.shuntVoltageControlChangedSection")
                .withUntypedValue("numShuntVoltageControlAdjusted", numShuntVoltageControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
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

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseNotStarted(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseNotStarted")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseTargetVIsImplausible(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseTargetVIsImplausible")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseInconsistentControlledBus(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseInconsistentControlledBus")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseInconsistentTargetVoltages(ReportNode reportNode, int impactedGeneratorCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseInconsistentTargetVoltages")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportTransformersDiscardedFromVoltageControlBecauseTargetVIsInconsistent(ReportNode reportNode, int impactedTransformerCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.transformersDiscardedFromVoltageControlBecauseTargetVIsInconsistent")
                .withUntypedValue(IMPACTED_TRANSFORMER_COUNT, impactedTransformerCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportShuntsDiscardedFromVoltageControlBecauseTargetVIsInconsistent(ReportNode reportNode, int impactedShuntCount) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.shuntsDiscardedFromVoltageControlBecauseTargetVIsInconsistent")
                .withUntypedValue(IMPACTED_SHUNT_COUNT, impactedShuntCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAcLfComplete(ReportNode reportNode, boolean success, String solverStatus, String outerloopStatus) {
        TypedValue severity = success ? TypedValue.INFO_SEVERITY : TypedValue.ERROR_SEVERITY;
        String successText = success ? "successfully" : "with error";
        reportNode.newReportNode()
                .withMessageTemplate("olf.acLfComplete")
                .withUntypedValue("successText", successText)
                .withUntypedValue("solverStatus", solverStatus)
                .withUntypedValue("outerloopStatus", outerloopStatus)
                .withSeverity(severity)
                .add();
    }

    public static ReportNode createLoadFlowReporter(ReportNode reportNode, String networkId) {
        return reportNode.newReportNode().withMessageTemplate("olf.loadFlow")
                .withUntypedValue(NETWORK_ID, networkId)
                .add();
    }

    public static ReportNode createRootLfNetworkReportNode(ReportNode firstRootReportNode, int networkNumCc, int networkNumSc) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate(LF_NETWORK_KEY)
                .withUntypedValue(NETWORK_NUM_CC, networkNumCc)
                .withUntypedValue(NETWORK_NUM_SC, networkNumSc)
                .build();
    }

    public static ReportNode includeLfNetworkReportNode(ReportNode reportNode, ReportNode lfNetworkReportNode) {
        reportNode.include(lfNetworkReportNode);
        return lfNetworkReportNode;
    }

    public static ReportNode createNetworkInfoReporter(ReportNode reportNode) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.networkInfo")
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
                .withUntypedValue(CONTINGENCY_ID, contingencyId)
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
        subReportNode.newReportNode()
                .withMessageTemplate("olf.solverNoOuterLoops")
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        return subReportNode;
    }

    public static ReportNode createDetailedSolverReporterOuterLoop(ReportNode reportNode, String solverName, int networkNumCc, int networkNumSc,
                                                                   int outerLoopIteration, String outerLoopType) {
        ReportNode subReportNode = createSolverReport(reportNode, solverName, networkNumCc, networkNumSc);
        subReportNode.newReportNode()
                .withMessageTemplate("olf.solverOuterLoopCurrentType")
                .withUntypedValue("outerLoopIteration", outerLoopIteration)
                .withUntypedValue("outerLoopType", outerLoopType)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        return subReportNode;
    }

    public static ReportNode createSolverReport(ReportNode reportNode, String solverName, int networkNumCc, int networkNumSc) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.solver")
                .withUntypedValue(NETWORK_NUM_CC, networkNumCc)
                .withUntypedValue(NETWORK_NUM_SC, networkNumSc)
                .withUntypedValue("solverName", solverName)
                .add();
    }

    public static ReportNode createNewtonRaphsonMismatchReporter(ReportNode reportNode, int iteration) {
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

    public static void reportNewtonRaphsonError(ReportNode reportNode, String error) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.NRError")
                .withUntypedValue("error", error)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .add();
    }

    public static void reportNewtonRaphsonNorm(ReportNode reportNode, double norm) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.NRNorm")
                .withUntypedValue("norm", norm)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .add();
    }

    public static void reportFastDecoupledNorm(ReportNode reportNode, double norm) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.FDNorm")
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
                .withTypedValue(MISMATCH, mismatchUnitConverter * busReport.mismatch(), OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
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

    public static void reportNewtonRaphsonBusesOutOfRealisticVoltageRange(ReportNode reportNode, Map<String, Double> busesOutOfRealisticVoltageRange, double minRealisticVoltage, double maxRealisticVoltage) {
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

    public static void reportAngleReferenceBusAndSlackBuses(ReportNode reportNode, String referenceBus, List<String> slackBuses) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.angleReferenceBusSelection")
                .withUntypedValue("referenceBus", referenceBus)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        slackBuses.forEach(slackBus -> reportNode.newReportNode()
                .withMessageTemplate("olf.slackBusSelection")
                .withUntypedValue("slackBus", slackBus)
                .withSeverity(TypedValue.INFO_SEVERITY)
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

    public static void reportActionApplicationFailure(String actionId, String contingencyId, ReportNode node) {
        node.newReportNode()
                .withMessageTemplate("olf.LfActionUtils")
                .withUntypedValue(ACTION_ID, actionId)
                .withUntypedValue(CONTINGENCY_ID, contingencyId)
                .add();
    }

    public static ReportNode createRootThreadReport(ReportNode firstRootReport) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReport.getTreeContext().getLocale())
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("olf.threadRoot")
                .build();
    }
}
