/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;
import com.powsybl.openloadflow.ac.equations.AcEquationType;

import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class Reports {

    private static final String NETWORK_NUM_CC = "networkNumCc";
    private static final String NETWORK_NUM_SC = "networkNumSc";
    private static final String ITERATION = "iteration";
    private static final String NETWORK_ID = "networkId";

    private Reports() {
    }

    public static void reportNetworkSize(Reporter reporter, int busCount, int branchCount) {
        reporter.report(Report.builder()
                .withKey("networkSize")
                .withDefaultMessage("Network has ${busCount} buses and ${branchCount} branches")
                .withValue("busCount", busCount)
                .withValue("branchCount", branchCount)
                .build());
    }

    public static void reportNetworkBalance(Reporter reporter, double activeGeneration, double activeLoad, double reactiveGeneration, double reactiveLoad) {
        reporter.report(Report.builder()
                .withKey("networkBalance")
                .withDefaultMessage("Network balance: active generation=${activeGeneration} MW, active load=${activeLoad} MW, reactive generation=${reactiveGeneration} MVar, reactive load=${reactiveLoad} MVar")
                .withValue("activeGeneration", activeGeneration)
                .withValue("activeLoad", activeLoad)
                .withValue("reactiveGeneration", reactiveGeneration)
                .withValue("reactiveLoad", reactiveLoad)
                .build());
    }

    public static void reportNetworkMustHaveAtLeastOneBusVoltageControlled(Reporter reporter) {
        reporter.report(Report.builder()
                .withKey("networkMustHaveAtLeastOneBusVoltageControlled")
                .withDefaultMessage("Network must have at least one bus voltage controlled")
                .build());
    }

    public static void reportMismatchDistributionFailure(Reporter reporter, int iteration, double remainingMismatch) {
        reporter.report(Report.builder()
                .withKey("mismatchDistributionFailure")
                .withDefaultMessage("Iteration ${iteration}: failed to distribute slack bus active power mismatch, ${mismatch} MW remains")
                .withValue(ITERATION, iteration)
                .withTypedValue("mismatch", remainingMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }

    public static void reportMismatchDistributionSuccess(Reporter reporter, int iteration, double slackBusActivePowerMismatch, int iterationCount) {
        reporter.report(Report.builder()
                .withKey("mismatchDistributionSuccess")
                .withDefaultMessage("Iteration ${iteration}: slack bus active power (${initialMismatch} MW) distributed in ${iterationCount} iterations")
                .withValue(ITERATION, iteration)
                .withTypedValue("initialMismatch", slackBusActivePowerMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withValue("iterationCount", iterationCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportNoMismatchDistribution(Reporter reporter, int iteration) {
        reporter.report(Report.builder()
                .withKey("NoMismatchDistribution")
                .withDefaultMessage("Iteration ${iteration}: already balanced")
                .withValue(ITERATION, iteration)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPvToPqBuses(Reporter reporter, int pvToPqBusCount, int remainingPvBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPvPq")
                .withDefaultMessage("${pvToPqBusCount} buses switched PV -> PQ ({remainingPvBusCount} bus remains PV}")
                .withValue("pvToPqBusCount", pvToPqBusCount)
                .withValue("remainingPvBusCount", remainingPvBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPqToPvBuses(Reporter reporter, int pqToPvBusCount, int blockedPqBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPqPv")
                .withDefaultMessage("${pqToPvBusCount} buses switched PQ -> PV ({blockedPqBusCount} buses blocked PQ because have reach max number of switch)")
                .withValue("pqToPvBusCount", pqToPvBusCount)
                .withValue("blockedPqBusCount", blockedPqBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportStandByAutomatonActivation(Reporter reporter, String busId, double newTargetV) {
        reporter.report(Report.builder()
                .withKey("standByAutomatonActivation")
                .withDefaultMessage("Activation of voltage control of static var compensator with stand by automaton: bus {busId} switched PQ -> PV with targetV {newTargetV}")
                .withValue("busId", busId)
                .withValue("newTargetV", newTargetV)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportDcLfSolverFailure(Reporter reporter, String errorMessage) {
        reporter.report(Report.builder()
                .withKey("dcLfFailure")
                .withDefaultMessage("Failed to solve linear system for DC load flow: ${errorMessage}")
                .withValue("errorMessage", errorMessage)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }

    public static void reportDcLfComplete(Reporter reporter, boolean succeeded) {
        reporter.report(Report.builder()
                .withKey("dcLfComplete")
                .withDefaultMessage("DC load flow completed (status=${succeeded})")
                .withValue("succeeded", succeeded)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseNotStarted(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseNotStarted")
                .withDefaultMessage("${impactedGeneratorCount} generators have been discarded from voltage control because not started")
                .withValue("impactedGeneratorCount", impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withDefaultMessage("${impactedGeneratorCount} generators have been discarded from voltage control because of a too small reactive range")
                .withValue("impactedGeneratorCount", impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportAcLfComplete(Reporter reporter, String nrStatus, TypedValue severity) {
        reporter.report(Report.builder()
                .withKey("acLfComplete")
                .withDefaultMessage("AC load flow complete with NR status '${nrStatus}'")
                .withValue("nrStatus", nrStatus)
                .withSeverity(severity)
                .build());
    }

    public static Reporter createLoadFlowReporter(Reporter reporter, String networkId) {
        return reporter.createSubReporter("loadFlow", "Load flow on network '${networkId}'",
                NETWORK_ID, networkId);
    }

    public static Reporter createLfNetworkReporter(Reporter reporter, int networkNumCc, int networkNumSc) {
        return reporter.createSubReporter("lfNetwork", "Network CC${networkNumCc} SC${networkNumSc}",
                Map.of(NETWORK_NUM_CC, new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        NETWORK_NUM_SC, new TypedValue(networkNumSc, TypedValue.UNTYPED)));
    }

    public static Reporter createPostLoadingProcessingReporter(Reporter reporter) {
        return reporter.createSubReporter("postLoadingProcessing", "Post loading processing");
    }

    public static Reporter createOuterLoopReporter(Reporter reporter, String outerLoopType) {
        return reporter.createSubReporter("OuterLoop", "Outer loop ${outerLoopType}", "outerLoopType", outerLoopType);
    }

    public static Reporter createSensitivityAnalysis(Reporter reporter, String networkId) {
        return reporter.createSubReporter("sensitivityAnalysis",
                "Sensitivity analysis on network '${networkId}'", NETWORK_ID, networkId);
    }

    public static Reporter createAcSecurityAnalysis(Reporter reporter, String networkId) {
        return reporter.createSubReporter("acSecurityAnalysis",
                "AC security analysis on network '${networkId}'", NETWORK_ID, networkId);
    }

    public static Reporter createPreContingencySimulation(Reporter reporter) {
        return reporter.createSubReporter("preContingencySimulation", "Pre-contingency simulation");
    }

    public static Reporter createPostContingencySimulation(Reporter reporter, String contingencyId) {
        return reporter.createSubReporter("postContingencySimulation", "Post-contingency simulation '${contingencyId}'",
                "contingencyId", contingencyId);
    }

    public static Reporter createNewtonRaphsonReporter(Reporter reporter, int networkNumCc, int networkNumSc) {
        return reporter.createSubReporter("newtonRaphson", "Network CC${networkNumCc} SC${networkNumSc}",
                Map.of(NETWORK_NUM_CC, new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        NETWORK_NUM_SC, new TypedValue(networkNumSc, TypedValue.UNTYPED)));
    }

    public static Reporter createNewtonRaphsonMismatchReporter(Reporter reporter, int iteration) {
        String var1 = iteration == -1 ? "mismatchInitial" : String.format("mismatchIteration%s", iteration);
        String var2 = iteration == -1 ? "Initial mismatch" : String.format("Iteration %s mismatch", iteration);
        return reporter.createSubReporter(var1, var2);
    }

    public static void reportNewtonRaphsonMismatch(Reporter reporter, AcEquationType acEquationType, double mismatch, String busId, double busV, double busPhi, int iteration) {
        String prefixIteration = iteration == -1 ? "Initial" : String.format("Iteration%s", iteration);
        String messageIteration = iteration == -1 ? "Initial mismatch" : String.format("Iteration %s mismatch", iteration);

        String suffixAcEquationType;
        switch (acEquationType) {
            case BUS_TARGET_P:
                suffixAcEquationType = "TargetP";
                break;
            case BUS_TARGET_Q:
                suffixAcEquationType = "TargetQ";
                break;
            case BUS_TARGET_V:
                suffixAcEquationType = "TargetV";
                break;
            default:
                // not implemented for other types
                return;
        }

        Reporter subReporter = reporter.createSubReporter(String.format("%s%s", suffixAcEquationType, prefixIteration), String.format("%s on %s", messageIteration, suffixAcEquationType));
        subReporter.report(Report.builder()
                .withKey(String.format("%sMismatch%s", suffixAcEquationType, prefixIteration))
                .withDefaultMessage(String.format("Mismatch on %s : '${mismatch}'", suffixAcEquationType))
                .withValue("mismatch", mismatch)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
        subReporter.report(Report.builder()
                .withKey(String.format("%sBusId%s", suffixAcEquationType, prefixIteration))
                .withDefaultMessage(String.format("Bus Id : '${busId}'", suffixAcEquationType))
                .withValue("busId", busId)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
        subReporter.report(Report.builder()
                .withKey(String.format("%sBusV%s", suffixAcEquationType, prefixIteration))
                .withDefaultMessage(String.format("Bus V : '${busV}'", suffixAcEquationType))
                .withValue("busV", busV)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
        subReporter.report(Report.builder()
                .withKey(String.format("%sBusPhi%s", suffixAcEquationType, prefixIteration))
                .withDefaultMessage(String.format("Bus Phi : '${busPhi}'", suffixAcEquationType))
                .withValue("busPhi", busPhi)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonNorm(Reporter reporter, double norm, int iteration) {
        String keyIteration = iteration == -1 ? "initialNorm" : String.format("iteration%sNorm", iteration);
        String xVariable = iteration == -1 ? "x0" : "x";
        reporter.report(Report.builder()
                .withKey(keyIteration)
                .withDefaultMessage(String.format("Norm |f(%s)|='${norm}'", xVariable))
                .withValue("norm", norm)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
    }
}
