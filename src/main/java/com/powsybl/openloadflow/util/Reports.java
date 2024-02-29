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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Reports {

    private static final String NETWORK_NUM_CC = "networkNumCc";
    private static final String NETWORK_NUM_SC = "networkNumSc";
    private static final String ITERATION = "iteration";
    private static final String NETWORK_ID = "networkId";
    private static final String IMPACTED_GENERATOR_COUNT = "impactedGeneratorCount";
    private static final String BUS_ID = "busId";

    public record BusReport(String busId, double mismatch, double nominalV, double v, double phi, double p, double q) {
    }

    private Reports() {
    }

    public static void reportNetworkSize(Reporter reporter, int busCount, int branchCount) {
        reporter.report(Report.builder()
                .withKey("networkSize")
                .withDefaultMessage("Network has ${busCount} buses and ${branchCount} branches")
                .withValue("busCount", busCount)
                .withValue("branchCount", branchCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
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
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportNetworkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled(Reporter reporter) {
        reporter.report(Report.builder()
                .withKey("networkMustHaveAtLeastOneBusGeneratorVoltageControlEnabled")
                .withDefaultMessage("Network must have at least one bus with generator voltage control enabled")
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }

    public static void reportMismatchDistributionFailure(Reporter reporter, double remainingMismatch) {
        reporter.report(Report.builder()
                .withKey("mismatchDistributionFailure")
                .withDefaultMessage("Failed to distribute slack bus active power mismatch, ${mismatch} MW remains")
                .withTypedValue("mismatch", remainingMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }

    public static void reportMismatchDistributionSuccess(Reporter reporter, double slackBusActivePowerMismatch, int iterationCount) {
        reporter.report(Report.builder()
                .withKey("mismatchDistributionSuccess")
                .withDefaultMessage("Slack bus active power (${initialMismatch} MW) distributed in ${iterationCount} iteration(s)")
                .withTypedValue("initialMismatch", slackBusActivePowerMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withValue("iterationCount", iterationCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPvToPqBuses(Reporter reporter, int pvToPqBusCount, int remainingPvBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPvPq")
                .withDefaultMessage("${pvToPqBusCount} buses switched PV -> PQ (${remainingPvBusCount} buses remain PV)")
                .withValue("pvToPqBusCount", pvToPqBusCount)
                .withValue("remainingPvBusCount", remainingPvBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPqToPvBuses(Reporter reporter, int pqToPvBusCount, int blockedPqBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPqPv")
                .withDefaultMessage("${pqToPvBusCount} buses switched PQ -> PV (${blockedPqBusCount} buses blocked PQ due to the max number of switches)")
                .withValue("pqToPvBusCount", pqToPvBusCount)
                .withValue("blockedPqBusCount", blockedPqBusCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportBusForcedToBePv(Reporter reporter, String busId) {
        reporter.report(Report.builder()
                .withKey("busForcedToBePv")
                .withDefaultMessage("All PV buses should switch PQ, strongest one will stay PV: ${busId}")
                .withValue(BUS_ID, busId)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportBusesWithUpdatedQLimits(Reporter reporter, int numBusesWithUpdatedQLimits) {
        reporter.report(Report.builder()
                .withKey("busWithUpdatedQLimits")
                .withDefaultMessage("${numBusesWithUpdatedQLimits} buses blocked at a reactive limit have been adjusted because the reactive limit changed")
                .withValue("numBusesWithUpdatedQLimits", numBusesWithUpdatedQLimits)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportReactiveControllerBusesToPqBuses(Reporter reporter, int remoteReactivePowerControllerBusToPqCount) {
        reporter.report(Report.builder()
                .withKey("remoteReactiveControllerBusToPq")
                .withDefaultMessage("${remoteReactivePowerControllerBusToPqCount} bus(es) with remote reactive power controller switched PQ")
                .withValue("remoteReactivePowerControllerBusToPqCount", remoteReactivePowerControllerBusToPqCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportStandByAutomatonActivation(Reporter reporter, String busId, double newTargetV) {
        reporter.report(Report.builder()
                .withKey("standByAutomatonActivation")
                .withDefaultMessage("Activation of voltage control of static var compensator with stand by automaton: bus ${busId} switched PQ -> PV with targetV ${newTargetV}")
                .withValue(BUS_ID, busId)
                .withValue("newTargetV", newTargetV)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportCurrentLimiterPstsChangedTaps(Reporter reporter, int numOfCurrentLimiterPstsThatChangedTap) {
        reporter.report(Report.builder()
                .withKey("currentLimiterPstsChangedTaps")
                .withDefaultMessage("${numOfCurrentLimiterPstsThatChangedTap} current limiter PST(s) changed taps")
                .withValue("numOfCurrentLimiterPstsThatChangedTap", numOfCurrentLimiterPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportActivePowerControlPstsChangedTaps(Reporter reporter, int numOfActivePowerControlPstsThatChangedTap) {
        reporter.report(Report.builder()
                .withKey("activePowerControlPstsChangedTaps")
                .withDefaultMessage("${numOfActivePowerControlPstsThatChangedTap} active power control PST(s) changed taps")
                .withValue("numOfActivePowerControlPstsThatChangedTap", numOfActivePowerControlPstsThatChangedTap)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlBusesOutsideDeadband(Reporter reporter, int numTransformerControlBusesOutsideDeadband) {
        reporter.report(Report.builder()
                .withKey("transformerControlBusesOutsideDeadband")
                .withDefaultMessage("${numTransformerControlBusesOutsideDeadband} voltage-controlled buses are outside of their target deadbands")
                .withValue("numTransformerControlBusesOutsideDeadband", numTransformerControlBusesOutsideDeadband)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlBranchesOutsideDeadband(Reporter reporter, int numTransformerControlBranchesOutsideDeadband) {
        reporter.report(Report.builder()
                .withKey("transformerControlBranchesOutsideDeadband")
                .withDefaultMessage("${numTransformerControlBranchesOutsideDeadband} reactive power-controlled branches are outside of their target deadbands")
                .withValue("numTransformerControlBranchesOutsideDeadband", numTransformerControlBranchesOutsideDeadband)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlChangedTaps(Reporter reporter, int numTransformerControlAdjusted) {
        reporter.report(Report.builder()
                .withKey("transformerControlChangedTaps")
                .withDefaultMessage("${numTransformerControlAdjusted} transformers changed tap position")
                .withValue("numTransformerControlAdjusted", numTransformerControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlTapLimit(Reporter reporter, int numTransformerControlTapLimit) {
        reporter.report(Report.builder()
                .withKey("transformerControlTapLimit")
                .withDefaultMessage("${numTransformerControlTapLimit} transformers reached their tap maximum position")
                .withValue("numTransformerControlTapLimit", numTransformerControlTapLimit)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportShuntVoltageControlChangedSection(Reporter reporter, int numShuntVoltageControlAdjusted) {
        reporter.report(Report.builder()
                .withKey("shuntVoltageControlChangedSection")
                .withDefaultMessage("${numShuntVoltageControlAdjusted} shunts changed section")
                .withValue("numShuntVoltageControlAdjusted", numShuntVoltageControlAdjusted)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportUnsuccessfulOuterLoop(Reporter reporter, String outerLoopStatus) {
        reporter.report(Report.builder()
                .withKey("outerLoopStatus")
                .withDefaultMessage("Outer loop unsuccessful with status: ${outerLoopStatus}")
                .withValue("outerLoopStatus", outerLoopStatus)
                .withSeverity(TypedValue.ERROR_SEVERITY)
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
                .withDefaultMessage("${impactedGeneratorCount} generators were discarded from voltage control because not started")
                .withValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withDefaultMessage("${impactedGeneratorCount} generators have been discarded from voltage control because of a too small reactive range")
                .withValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits")
                .withDefaultMessage("${impactedGeneratorCount} generators have been discarded from voltage control because targetP is outside active power limits")
                .withValue(IMPACTED_GENERATOR_COUNT, impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportAcLfComplete(Reporter reporter, boolean success, String solverStatus, String outerloopStatus) {
        TypedValue severity = success ? TypedValue.INFO_SEVERITY : TypedValue.ERROR_SEVERITY;
        String successText = success ? "successfully" : "with error";
        reporter.report(Report.builder()
                .withKey("acLfComplete")
                .withDefaultMessage("AC load flow completed ${successText} (solverStatus=${solverStatus}, outerloopStatus=${outerloopStatus})")
                .withValue("successText", successText)
                .withValue("solverStatus", solverStatus)
                .withValue("outerloopStatus", outerloopStatus)
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

    public static Reporter createNetworkInfoReporter(Reporter reporter) {
        return reporter.createSubReporter("networkInfo", "Network info");
    }

    public static Reporter createOuterLoopReporter(Reporter reporter, String outerLoopType) {
        return reporter.createSubReporter("OuterLoop", "Outer loop ${outerLoopType}", "outerLoopType", outerLoopType);
    }

    public static Reporter createOuterLoopIterationReporter(Reporter reporter, int outerLoopIteration) {
        Map<String, TypedValue> subReporterMap = new HashMap<>();
        subReporterMap.put("outerLoopIteration", new TypedValue(outerLoopIteration, TypedValue.UNTYPED));
        return reporter.createSubReporter("OuterLoopIteration", "Outer loop iteration ${outerLoopIteration}", subReporterMap);
    }

    public static Reporter createSensitivityAnalysis(Reporter reporter, String networkId) {
        return reporter.createSubReporter("sensitivityAnalysis",
                "Sensitivity analysis on network '${networkId}'", NETWORK_ID, networkId);
    }

    public static Reporter createAcSecurityAnalysis(Reporter reporter, String networkId) {
        return reporter.createSubReporter("acSecurityAnalysis",
                "AC security analysis on network '${networkId}'", NETWORK_ID, networkId);
    }

    public static Reporter createDcSecurityAnalysis(Reporter reporter, String networkId) {
        return reporter.createSubReporter("dcSecurityAnalysis",
                "DC security analysis on network '${networkId}'", NETWORK_ID, networkId);
    }

    public static Reporter createPreContingencySimulation(Reporter reporter) {
        return reporter.createSubReporter("preContingencySimulation", "Pre-contingency simulation");
    }

    public static Reporter createPostContingencySimulation(Reporter reporter, String contingencyId) {
        return reporter.createSubReporter("postContingencySimulation", "Post-contingency simulation '${contingencyId}'",
                "contingencyId", contingencyId);
    }

    public static Reporter createDetailedSolverReporter(Reporter reporter, String solverName, int networkNumCc, int networkNumSc) {
        Reporter subReporter = reporter.createSubReporter("solver", solverName + " on Network CC${networkNumCc} SC${networkNumSc}",
                Map.of(NETWORK_NUM_CC, new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        NETWORK_NUM_SC, new TypedValue(networkNumSc, TypedValue.UNTYPED)));
        subReporter.report(Report.builder()
                .withKey("solverNoOuterLoops")
                .withDefaultMessage("No outer loops have been launched")
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
        return subReporter;
    }

    public static Reporter createDetailedSolverReporterOuterLoop(Reporter reporter, String solverName, int networkNumCc, int networkNumSc,
                                                                 int outerLoopIteration, String outerLoopType) {
        Reporter subReporter = reporter.createSubReporter("solver", solverName + " on Network CC${networkNumCc} SC${networkNumSc}",
                Map.of(NETWORK_NUM_CC, new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        NETWORK_NUM_SC, new TypedValue(networkNumSc, TypedValue.UNTYPED)));
        subReporter.report(Report.builder()
                .withKey("solverOuterLoopCurrentType")
                .withDefaultMessage("Newton-Raphson of outer loop iteration ${outerLoopIteration} of type ${outerLoopType}")
                .withValue("outerLoopIteration", outerLoopIteration)
                .withValue("outerLoopType", outerLoopType)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
        return subReporter;
    }

    public static Reporter createNewtonRaphsonMismatchReporter(Reporter reporter, int iteration) {
        if (iteration == 0) {
            return reporter.createSubReporter("mismatchInitial", "Initial mismatch");
        } else {
            return reporter.createSubReporter("mismatchIteration", "Iteration ${iteration} mismatch", ITERATION, iteration);
        }
    }

    public static void reportNewtonRaphsonError(Reporter reporter, String error) {
        reporter.report(Report.builder()
                .withKey("NRError")
                .withDefaultMessage("Newton Raphson crashed with error: ${error}")
                .withValue("error", error)
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonNorm(Reporter reporter, double norm) {
        reporter.report(Report.builder()
                .withKey("NRNorm")
                .withDefaultMessage("Newton-Raphson norm |f(x)|=${norm}")
                .withValue("norm", norm)
                .withSeverity(TypedValue.TRACE_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonLargestMismatches(Reporter reporter, String acEquationType, BusReport busReport) {
        Map<String, TypedValue> subReporterMap = new HashMap<>();
        subReporterMap.put("equationType", new TypedValue(acEquationType, TypedValue.UNTYPED));
        subReporterMap.put("mismatch", new TypedValue(busReport.mismatch(), OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE));

        ReportBuilder busIdReportBuilder = Report.builder();
        busIdReportBuilder.withKey("NRMismatchBusInfo")
                .withDefaultMessage("Bus Id: ${busId} (nominalVoltage=${busNominalV}kV)")
                .withValue(BUS_ID, busReport.busId())
                .withValue("busNominalV", busReport.nominalV())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busVReportBuilder = Report.builder();
        busVReportBuilder.withKey("NRMismatchBusV")
                .withDefaultMessage("Bus V: ${busV} pu, ${busPhi} rad")
                .withValue("busV", busReport.v())
                .withValue("busPhi", busReport.phi())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busInjectionReportBuilder = Report.builder();
        busInjectionReportBuilder.withKey("NRMismatchBusInjection")
                .withDefaultMessage("Bus injection: ${busSumP} MW, ${busSumQ} MVar")
                .withValue("busSumP", busReport.p())
                .withValue("busSumQ", busReport.q())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        Reporter subReporter = reporter.createSubReporter("NRMismatch", "Largest ${equationType} mismatch: ${mismatch}", subReporterMap);
        subReporter.report(busIdReportBuilder.build());
        subReporter.report(busVReportBuilder.build());
        subReporter.report(busInjectionReportBuilder.build());
    }

    public static void reportLineSearchStateVectorScaling(Reporter reporter, double stepSize) {
        reporter.report(Report.builder()
                .withKey("lineSearchStateVectorScaling")
                .withDefaultMessage("Step size: ${stepSize} (line search)")
                .withValue("stepSize", stepSize)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportMaxVoltageChangeStateVectorScaling(Reporter reporter, double stepSize, int vCutCount, int phiCutCount) {
        reporter.report(Report.builder()
                .withKey("maxVoltageChangeStateVectorScaling")
                .withDefaultMessage("Step size: ${stepSize} (max voltage change: ${vCutCount} Vmagnitude and ${phiCutCount} Vangle changes outside configured thresholds)")
                .withValue("stepSize", stepSize)
                .withValue("vCutCount", vCutCount)
                .withValue("phiCutCount", phiCutCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonBusesOutOfRealisticVoltageRange(Reporter reporter, Map<String, Double> busesOutOfRealisticVoltageRange, double minRealisticVoltage, double maxRealisticVoltage) {
        reporter.report(Report.builder()
                .withKey("newtonRaphsonBusesOutOfRealisticVoltageRange")
                .withDefaultMessage("${busCountOutOfRealisticVoltageRange} buses have a voltage magnitude out of the configured realistic range [${minRealisticVoltage}, ${maxRealisticVoltage}] p.u.: ${busesOutOfRealisticVoltageRange}")
                .withValue("busCountOutOfRealisticVoltageRange", busesOutOfRealisticVoltageRange.size())
                .withValue("minRealisticVoltage", minRealisticVoltage)
                .withValue("maxRealisticVoltage", maxRealisticVoltage)
                .withValue("busesOutOfRealisticVoltageRange", busesOutOfRealisticVoltageRange.toString())
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }
}
