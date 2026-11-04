/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import com.powsybl.openloadflow.network.LfGenerator;
import com.powsybl.openloadflow.network.NetworkReportResourceBundle;
import com.powsybl.openloadflow.util.PerUnit;
import org.slf4j.Logger;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class NetworkReports {

    public static final String NETWORK_NUM_CC = "networkNumCc";
    public static final String NETWORK_NUM_SC = "networkNumSc";
    private static final String IMPACTED_GENERATOR_COUNT = "impactedGeneratorCount";

    private static final String IMPACTED_TRANSFORMER_COUNT = "impactedTransformerCount";

    private static final String IMPACTED_SHUNT_COUNT = "impactedShuntCount";
    private static final String GENERATOR_ID = "generatorId";
    private static final String GENERATORS_ID = "generatorIds";
    private static final String CONTROLLER_BUS_ID = "controllerBusId";
    private static final String CONTROLLED_BUS_ID = "controlledBusId";
    private static final String ACTION_ID = "actionId";
    private static final String CONTINGENCY_ID = "contingencyId";
    private static final String TARGET_V = "targetV";
    private static final String TARGET_P = "targetP";

    public static final String LF_NETWORK_KEY = "olf.lfNetwork";
    public static final String MISMATCH_TYPED_VALUE = "Mismatch";

    private NetworkReports() {
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

    public static void reportBusAlreadyControlledWithDifferentTargetV(ReportNode reportNode, String controllerBusId,
                                                                      String controlledBusId, String busesId, Double keptTargetV, Double ignoredTargetV) {
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
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.componentsWithoutGenerators")
                .withUntypedValue("deadComponentsCount", deadComponentsCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportAreaNoInterchangeControlNoBoundary(ReportNode reportNode, String area) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaNoInterchangeControlNoBoundary")
                .withUntypedValue("area", area)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAreaNoInterchangeControlNoInterchangeTarget(ReportNode reportNode, String area) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaNoInterchangeControlNoInterchangeTarget")
                .withUntypedValue("area", area)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static void reportAreaNoInterchangeControlMissingBuses(ReportNode reportNode, String area) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.areaNoInterchangeControlMissingBuses")
                .withUntypedValue("area", area)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromVoltageControlBecauseNotStarted(ReportNode firstRootReportNode, LfGenerator generator) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromVoltageControlBecauseNotStarted")
                .withUntypedValue(GENERATOR_ID, generator.getId())
                .withUntypedValue(TARGET_P, generator.getTargetP() * PerUnit.SB)
                .withUntypedValue("minP", generator.getMinP() * PerUnit.SB)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits(ReportNode firstRootReportNode, LfGenerator generator) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits")
                .withUntypedValue(GENERATOR_ID, generator.getId())
                .withUntypedValue(TARGET_P, generator.getTargetP() * PerUnit.SB)
                .withUntypedValue("minP", generator.getMinP() * PerUnit.SB)
                .withUntypedValue("maxP", generator.getMaxP() * PerUnit.SB)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(ReportNode firstRootReportNode, LfGenerator generator) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withUntypedValue(GENERATOR_ID, generator.getId())
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromVoltageControlBecauseImplausibleTargetVoltage(ReportNode firstRootReportNode, String generatorId, double targetV) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromVoltageControlBecauseImplausibleTargetVoltage")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue(TARGET_V, targetV)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero(ReportNode firstRootReportNode, String generatorId, double targetP) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromActivePowerControlBecauseTargetEqualsToZero")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue(TARGET_P, targetP)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP(ReportNode firstRootReportNode,
                                                                                                                    String generatorId,
                                                                                                                    double targetP,
                                                                                                                    double maxTargetP) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue(TARGET_P, targetP)
                .withUntypedValue("maxTargetP", maxTargetP)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP(ReportNode firstRootReportNode,
                                                                                                                  String generatorId,
                                                                                                                  double targetP,
                                                                                                                  double minTargetP) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue(TARGET_P, targetP)
                .withUntypedValue("minTargetP", minTargetP)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible(ReportNode firstRootReportNode, String generatorId, double maxP, double plausibleLimit) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromActivePowerControlBecauseMaxPNotPlausible")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue("maxP", maxP)
                .withUntypedValue("plausibleLimit", plausibleLimit)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static ReportNode createRootReportGeneratorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP(ReportNode firstRootReportNode, String generatorId, double maxP, double minP) {
        return ReportNode.newRootReportNode()
                .withLocale(firstRootReportNode.getTreeContext().getLocale())
                .withResourceBundles(NetworkReportResourceBundle.BASE_NAME)
                .withMessageTemplate("olf.oneGeneratorDiscardedFromActivePowerControlBecauseMaxPEqualsMinP")
                .withUntypedValue(GENERATOR_ID, generatorId)
                .withUntypedValue("maxP", maxP)
                .withUntypedValue("minP", minP)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build();
    }

    public static String reportGeneratorWithUpdatedTargetQ(ReportNode reportNode, LfGenerator generator, double oldTargetQ, double newTargetQ) {
        ReportNode result = reportNode.newReportNode()
                .withMessageTemplate("olf.generatorWithUpdatedTargetQ")
                .withUntypedValue(GENERATOR_ID, generator.getId())
                .withUntypedValue("oldTargetQ", oldTargetQ)
                .withUntypedValue("newTargetQ", newTargetQ)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
        return result.getMessage();
    }

    public static void reportTransformerControlAlreadyExistsWithDifferentTargetV(ReportNode reportNode, String firstControllerId,
                                                                                 String newControllerId, String controlledBusId,
                                                                                 double vcTargetValue, double targetValue) {
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

    public static void reportTransformerControlAlreadyExistsUpdateDeadband(ReportNode reportNode, String firstControllerId,
                                                                           String newControllerId, String controlledBusId,
                                                                           double newTargetDeadband, Double oldTargetDeadband) {
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

    public static ReportNode reportGeneratorsDiscardedFromVoltageControlBecauseNotStarted(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseNotStarted")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromVoltageControlBecauseImplausibleTargetVoltage(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
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

    public static ReportNode reportGeneratorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromActivePowerControlBecauseTargetEqualsToZero")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromActivePowerControlBecauseTargetPGreaterThanMaxP")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromActivePowerControlBecauseTargetPLowerThanMinP")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromActivePowerControlBecauseMaxPNotPlausible")
                .withUntypedValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .add();
    }

    public static ReportNode reportGeneratorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP(ReportNode reportNode, int impactedGeneratorCount) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.generatorsDiscardedFromActivePowerControlBecauseMaxPEqualsMinP")
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

    public static void reportRescaledRemoteVoltageControls(ReportNode reportNode, int rescaledRemoteVoltageControls) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.rescaledRemoteVoltageControls")
                .withUntypedValue("count", rescaledRemoteVoltageControls)
                .withSeverity(TypedValue.WARN_SEVERITY)
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

    /**
     * Create a Root report node for an AC-DC network. It is identified only by its connected component number.
     * @param firstRootReportNode original root report node. Used to get locale parameter.
     * @param networkNumCc number of the connected component represented by the LfNetwork associated to this report node.
     * @return a new report node to be attached to a LfNetwork.
     */
    public static ReportNode createRootAcDcLfNetworkReportNode(ReportNode firstRootReportNode, int networkNumCc) {
        return ReportNode.newRootReportNode()
            .withLocale(firstRootReportNode.getTreeContext().getLocale())
            .withAllResourceBundlesFromClasspath()
            .withMessageTemplate("olf.lfCcNetwork")
            .withUntypedValue(NETWORK_NUM_CC, networkNumCc)
            .build();
    }

    /**
     * Create a report node for a synchronous network. It is identified only by its synchronous component number.
     * @param networkNumSc number of the synchronous component represented by the LfSynchronousNetwork associated to this report node.
     * @return a new report node to be used by a LfSynchronousNetwork.
     */
    public static ReportNode createLfSynchronousNetworkReportNode(ReportNode reportNode, int networkNumSc) {
        return reportNode.newReportNode()
            .withMessageTemplate("olf.lfScNetwork")
            .withUntypedValue(NETWORK_NUM_SC, networkNumSc)
            .add();
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

    public static void reportAutomaticVdcReferenceConverter(ReportNode reportNode, int dcComponentNum, String converterId, double targetVdc) {
        reportNode.newReportNode()
                .withMessageTemplate("olf.automaticVdcReferenceConverter")
                .withUntypedValue("dcComponentNum", dcComponentNum)
                .withUntypedValue("converterId", converterId)
                .withUntypedValue("targetVdc", targetVdc)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .add();
    }

    public static void reportFictitiousInjectionTotal(ReportNode reportNode, double totalP, double totalQ, long busCount, Logger logger) {
        ReportNode node = reportNode.newReportNode()
                .withMessageTemplate("olf.fictitiousInjectionTotal")
                .withUntypedValue("totalP", totalP)
                .withUntypedValue("totalQ", totalQ)
                .withUntypedValue("busCount", busCount)
                .add();
        logger.info(node.getMessage());
    }

    public static void reportActionApplicationFailure(String actionId, String contingencyId, ReportNode node) {
        node.newReportNode()
                .withMessageTemplate("olf.lfActionUtils")
                .withUntypedValue(ACTION_ID, actionId)
                .withUntypedValue(CONTINGENCY_ID, contingencyId)
                .add();
    }

    public static ReportNode reportVoltageInitializer(ReportNode reportNode, String initializerName) {
        return reportNode.newReportNode()
                .withMessageTemplate("olf.voltageInitializer")
                .withUntypedValue("initializerName", initializerName)
                .add();
    }
}
