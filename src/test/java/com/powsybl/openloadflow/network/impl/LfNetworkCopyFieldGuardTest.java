/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards {@link LfNetworkCopier} against field drift: when a field is added to (or removed from) one
 * of the classes of the LfNetwork copy graph, this test fails so that the author updates the copy
 * constructors and the inventory below accordingly.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class LfNetworkCopyFieldGuardTest {

    public static Map<Class<?>, Set<String>> getLfNetworkExpectedFields() {
        Map<Class<?>, Set<String>> expected = new HashMap<>();
        expected.put(AbstractElement.class, Set.of("network", "num", "disabled"));
        expected.put(AbstractImpedantLfBranch.class, Set.of("connectedSide1", "connectedSide2", "disconnectionAllowedSide1", "disconnectionAllowedSide2", "p1", "q1", "i1",
                "p2", "q2", "i2", "openP1", "openQ1", "openI1", "openP2", "openQ2", "openI2", "closedP1", "closedQ1", "closedI1", "closedP2", "closedQ2", "closedI2",
                "additionalOpenP1", "additionalClosedP1", "additionalOpenQ1", "additionalClosedQ1", "additionalOpenP2", "additionalClosedP2", "additionalOpenQ2", "additionalClosedQ2"));
        expected.put(AbstractLfAcDcConverter.class, Set.of("calculatedPac", "calculatedQac", "calculatedIconv1", "calculatedIconv2", "targetP", "pAc", "qAc", "lossFactors",
                "targetVdc", "controlMode", "dcBus1", "dcBus2", "bus1"));
        expected.put(AbstractLfBranch.class, Set.of("bus1", "bus2", "currentLimits1", "activePowerLimits1", "apparentPowerLimits1", "currentLimits2", "activePowerLimits2",
                "apparentPowerLimits2", "piModel", "phaseControl", "phaseControlEnabled", "voltageControl", "voltageControlEnabled", "transformerReactivePowerControl",
                "zeroImpedanceContextByModel", "a1", "generatorReactivePowerControl", "asymLine"));
        expected.put(AbstractLfBranch.ZeroImpedanceContext.class, Set.of("spanningTreeEdge", "zeroImpedance"));
        expected.put(AbstractLfBus.class, Set.of("slack", "reference", "v", "calculatedV", "angle", "hasGeneratorsWithSlope", "generatorVoltageControlEnabled",
                "generatorReactivePowerControlEnabled", "generationTargetP", "generationTargetQ", "invalidatedGenerationTargetQ", "qLimitType", "generators",
                "converters", "shunt", "controllerShunt", "svcShunt", "distributedOnConformLoad", "loads", "loadTargetP", "loadTargetQ", "branches", "hvdcs",
                "generatorVoltageControl", "generatorReactivePowerControl", "transformerVoltageControl", "voltageSourceConverterVoltageControl", "shuntVoltageControl",
                "p", "q", "remoteControlReactivePercent", "zeroImpedanceNetwork", "asym", "area", "isGenerationTargetQFrozen", "forceTargetQInReactiveLimits", "numSC"));
        expected.put(AbstractLfDcBus.class, Set.of("v", "nominalV"));
        expected.put(AbstractLfDcLine.class, Set.of("dcBus1", "dcBus2", "p1", "i1", "p2", "i2", "r"));
        expected.put(AbstractLfGenerator.class, Set.of("network", "bus", "calculatedQ", "targetV", "switchedToLocalVoltageRegulation", "generatorControlType",
                "controlledBusId", "controlledBranchId", "controlledBranchSide", "remoteTargetQ", "disabled", "asym", "referencePriority", "reference", "extrapolateReactiveLimits"));
        expected.put(AbstractLfInjection.class, Set.of("initialTargetP", "targetP"));
        expected.put(AbstractLfShunt.class, Set.of("q", "p"));
        expected.put(AbstractPropertyBag.class, Set.of("properties"));
        expected.put(Control.class, Set.of("targetValue"));
        expected.put(DiscreteVoltageControl.class, Set.of("targetDeadband"));
        expected.put(GeneratorReactivePowerControl.class, Set.of("controllerBuses"));
        expected.put(GeneratorVoltageControl.class, Set.of());
        expected.put(LfAreaImpl.class, Set.of("areaRef", "interchangeTarget", "buses", "boundaries"));
        expected.put(LfAreaImpl.BoundaryImpl.class, Set.of("side", "branch"));
        expected.put(LfAsymBus.class, Set.of("bus", "totalDeltaPa", "totalDeltaQa", "totalDeltaPb", "totalDeltaQb", "totalDeltaPc", "totalDeltaQc", "vz", "angleZ", "vn",
                "angleN", "bzEquiv", "gzEquiv", "bnEquiv", "gnEquiv", "ixZ", "iyZ", "ixN", "iyN"));
        expected.put(LfAsymGenerator.class, Set.of("bz", "gz", "gn", "bn"));
        expected.put(LfAsymLine.class, Set.of("piZeroComponent", "piPositiveComponent", "piNegativeComponent", "phaseOpenA", "phaseOpenB", "phaseOpenC", "admittanceMatrix"));
        expected.put(LfBatteryImpl.class, Set.of("batteryRef", "initialParticipating", "participating", "droop", "participationFactor", "maxTargetP", "minTargetP"));
        expected.put(LfBoundaryLineBranch.class, Set.of("boundaryLineRef"));
        expected.put(LfBoundaryLineBus.class, Set.of("boundaryLineRef", "nominalV"));
        expected.put(LfBoundaryLineGenerator.class, Set.of("boundaryLineRef"));
        expected.put(LfBranchImpl.class, Set.of("branchRef"));
        expected.put(LfBranch.LfLimit.class, Set.of("name", "acceptableDuration", "value", "scaling"));
        expected.put(LfBranch.LfLimitsGroup.class, Set.of("sortedLimits", "operationalLimitsGroupId"));
        expected.put(LfBusImpl.class, Set.of("busRef", "nominalV", "lowVoltageLimit", "highVoltageLimit", "participating", "breakers", "country", "bbsIds", "fictitiousInjectionTargetP",
                "fictitiousInjectionTargetQ", "violationLocation"));
        expected.put(LfDcBusImpl.class, Set.of("dcBusRef", "isGrounded"));
        expected.put(LfDcLineImpl.class, Set.of("dcLineRef"));
        expected.put(LfVoltageSourceConverter.LfDroopReference.class, Set.of("k", "refVdc", "refP"));
        expected.put(LfGeneratorImpl.class, Set.of("generatorRef", "initialParticipating", "participating", "droop", "participationFactor", "qPercent",
                "isTargetQForcedInReactiveLimits", "forceVoltageControl", "maxTargetP", "minTargetP", "forceTargetQInReactiveLimits"));
        expected.put(LfHvdcImpl.AcEmulationControl.class, Set.of("hvdc", "droop", "p0", "pMaxFromCS1toCS2", "pMaxFromCS2toCS1", "acEmulationStatus"));
        expected.put(LfHvdcImpl.class, Set.of("id", "bus1", "bus2", "p1", "p2", "r", "nominalV", "converterStation1", "converterStation2", "acEmulation", "acEmulationControl"));
        expected.put(LfLegBranch.class, Set.of("twtRef", "legRef"));
        expected.put(LfLoadImpl.class, Set.of("bus", "loadModel", "loadsRefs", "lccCsRefs", "targetQ", "ensurePowerFactorConstantByLoad", "loadsAbsVariableTargetP",
                "absVariableTargetP", "distributedOnConformLoad", "loadsDisablingStatus", "p", "q"));
        expected.put(LfNetwork.class, Set.of("numCC", "synchronousNetworks", "slackBusSelector", "referenceBusSelector", "maxSlackBusCount", "busesById", "busesByIndex",
                "branches", "branchesById", "branchesByOriginalId", "shuntCount", "shuntsByIndex", "shuntsById", "generatorsById", "loadsById", "areasById", "areas", "hvdcs",
                "hvdcsById", "dcBusByIndex", "dcBusById", "dcLinesByIndex", "voltageSourceConvertersByIndex", "listeners", "validity", "connectivityFactory", "connectivity",
                "zeroImpedanceNetworksByModel", "reportNode", "secondaryVoltageControls", "voltageAngleLimits", "overloadManagementSystems", "connectivityRemovedBranches"));
        expected.put(LfNetwork.LfVoltageAngleLimit.class, Set.of("id", "from", "to", "highValue", "lowValue"));
        expected.put(LfOverloadManagementSystem.class, Set.of("monitoredBranch", "monitoredSide", "branchTrippingList"));
        expected.put(LfOverloadManagementSystem.LfBranchTripping.class, Set.of("branchOpen", "threshold", "branchToOperate"));
        expected.put(LfSecondaryVoltageControl.class, Set.of("controlUnits", "pilotBus", "targetValue", "zoneName"));
        expected.put(LfSecondaryVoltageControl.ControlUnit.class, Set.of("id", "participate", "generatorVoltageControl"));
        expected.put(LfShunt.Controller.class, Set.of("id", "sectionsB", "sectionsG", "position", "minPosition", "bMagnitude"));
        expected.put(LfShuntImpl.class, Set.of("shuntCompensatorsRefs", "bus", "voltageControl", "voltageControlCapability", "voltageControlEnabled", "controllers", "b", "zb", "g"));
        expected.put(LfShuntImpl.ControllerImpl.class, Set.of("shuntCompensatorRef"));
        expected.put(LfStandbyAutomatonShunt.class, Set.of("svc", "b"));
        expected.put(LfStarBus.class, Set.of("t3wtRef", "nominalV"));
        expected.put(LfStaticVarCompensatorImpl.class, Set.of("svcRef", "reactiveLimits", "nominalV", "slope", "targetQ", "standByAutomaton", "b0", "standByAutomatonShunt"));
        expected.put(LfStaticVarCompensatorImpl.SvcReactiveLimits.class, Set.of());
        expected.put(LfSwitch.class, Set.of("switchRef"));
        expected.put(LfSynchronousNetworkImpl.class, Set.of("lfNetwork", "numSC", "slackBusSelector", "referenceBusSelector", "maxSlackBusCount", "referenceBus", "slackBuses",
                "excludedSlackBuses", "referenceGenerator"));
        expected.put(LfTieLineBranch.class, Set.of("boundaryLine1Ref", "boundaryLine2Ref", "id"));
        expected.put(LfVscConverterStationImpl.class, Set.of("stationRef", "lossFactor", "hvdc", "hvdcDanglingInIidm"));
        expected.put(LfVoltageSourceConverterImpl.class, Set.of("converterRef", "isVoltageRegulatorOn", "targetQ", "targetVac", "droopBands"));
        expected.put(PiModelArray.class, Set.of("models", "lowTapPosition", "tapPositionIndex", "a1", "r1", "continuousR1", "branch", "minR1", "maxR1"));
        expected.put(ReactivePowerControl.class, Set.of("controlledSide", "controlledBranch"));
        expected.put(ShuntVoltageControl.class, Set.of());
        expected.put(SimplePiModel.class, Set.of("r", "x", "g1", "b1", "g2", "b2", "r1", "a1"));
        expected.put(TransformerPhaseControl.class, Set.of("controllerBranch", "controlledBranch", "targetDeadband", "controlledSide", "mode", "unit"));
        expected.put(TransformerReactivePowerControl.class, Set.of("controllerBranch", "targetDeadband"));
        expected.put(TransformerVoltageControl.class, Set.of());
        expected.put(VoltageControl.class, Set.of("type", "priority", "targetPriority", "controlledBus", "controllerElements", "mergeStatus", "mergedDependentVoltageControls",
                "mainMergedVoltageControl", "disabled"));
        expected.put(VoltageSourceConverterVoltageControl.class, Set.of());
        return expected;
    }

    @Test
    void testCopyGraphFieldInventory() {
        for (Map.Entry<Class<?>, Set<String>> entry : getLfNetworkExpectedFields().entrySet()) {
            Set<String> actual = Arrays.stream(entry.getKey().getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                    .map(Field::getName)
                    .collect(Collectors.toSet());
            assertEquals(entry.getValue(), actual,
                    "Field inventory of " + entry.getKey().getName() + " changed: handle the new/removed fields"
                            + " in the copy constructors used by LfNetworkCopier, then update this inventory");
        }
    }
}
