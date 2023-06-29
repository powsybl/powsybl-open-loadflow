/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.commons.reporter.Report;
import com.powsybl.commons.reporter.ReportBuilder;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.TypedValue;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;

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

    public static void reportNetworkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled(Reporter reporter) {
        reporter.report(Report.builder()
                .withKey("networkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled")
                .withDefaultMessage("Network must have at least one bus with generator voltage control enabled")
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

    public static Reporter createDetailedNewtonRaphsonReporter(Reporter reporter, int networkNumCc, int networkNumSc) {
        return reporter.createSubReporter("newtonRaphson", "Newton Raphson on Network CC${newtonRaphsonNetworkNumCc} SC${newtonRaphsonNetworkNumSc} || No outer loops calculations",
                Map.of("newtonRaphsonNetworkNumCc", new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        "newtonRaphsonNetworkNumSc", new TypedValue(networkNumSc, TypedValue.UNTYPED)));
    }

    public static Reporter createDetailedNewtonRaphsonReporterOuterLoop(Reporter reporter, int networkNumCc, int networkNumSc, int outerLoopIteration, String outerLoopType) {
        return reporter.createSubReporter("newtonRaphson", "Newton Raphson on Network CC${newtonRaphsonNetworkNumCc} SC${newtonRaphsonNetworkNumSc} || Outer loop iteration ${newtonRaphsonOuterLoopIteration} and type `${newtonRaphsonOuterLoopType}`",
                Map.of("newtonRaphsonNetworkNumCc", new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        "newtonRaphsonNetworkNumSc", new TypedValue(networkNumSc, TypedValue.UNTYPED),
                        "newtonRaphsonOuterLoopIteration", new TypedValue(outerLoopIteration, TypedValue.UNTYPED),
                        "newtonRaphsonOuterLoopType", new TypedValue(outerLoopType, TypedValue.UNTYPED)));
    }

    public static Reporter createNewtonRaphsonMismatchReporter(Reporter reporter, int iteration) {
        if (iteration == -1) {
            return reporter.createSubReporter("mismatchInitial", "Initial mismatch");
        } else {
            return reporter.createSubReporter("mismatchIteration", "Iteration ${iteration} mismatch", ITERATION, iteration);
        }
    }

    public static void reportNewtonRaphsonMismatch(Reporter reporter, String acEquationType, double mismatch, String busId, double busV, double busPhi, int iteration) {

        ReportBuilder reportBuilder = Report.builder();
        String mismatchDetails = " on ${equationType}: ${mismatch}, Bus Id: '${busId}', Bus V: ${busV}, Bus Phi: ${busPhi}";
        if (iteration == -1) {
            reportBuilder.withKey("NRInitialMismatch")
                    .withDefaultMessage("Initial mismatch" + mismatchDetails);
        } else {
            reportBuilder.withKey("NRIterationMismatch")
                    .withDefaultMessage("Iteration ${iteration} mismatch" + mismatchDetails)
                    .withValue(ITERATION, iteration);
        }

        reporter.report(reportBuilder.withValue("equationType", acEquationType)
                .withTypedValue("mismatch", mismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withValue("busId", busId)
                .withTypedValue("busV", busV, TypedValue.VOLTAGE)
                .withTypedValue("busPhi", busPhi, TypedValue.ANGLE)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonNorm(Reporter reporter, double norm, int iteration) {
        ReportBuilder reportBuilder = Report.builder();
        if (iteration == -1) {
            reportBuilder.withKey("NRInitialNorm")
                    .withDefaultMessage("Norm |f(x0)|=${norm}");
        } else {
            reportBuilder.withKey("NRIterationNorm")
                    .withDefaultMessage("Norm |f(x)|=${norm}");
        }
        reporter.report(reportBuilder.withValue("norm", norm)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
    }
}
