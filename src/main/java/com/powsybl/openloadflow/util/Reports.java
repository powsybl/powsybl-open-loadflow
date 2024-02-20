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
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.OpenLoadFlowReportConstants;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.NewtonRaphson;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;

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
                .withDefaultMessage("Slack bus active power (${initialMismatch} MW) distributed in ${iterationCount} inner iteration(s)")
                .withTypedValue("initialMismatch", slackBusActivePowerMismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE)
                .withValue("iterationCount", iterationCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPvToPqBuses(Reporter reporter, int pvToPqBusCount, int remainingPvBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPvPq")
                .withDefaultMessage("${pvToPqBusCount} bus(es) switched PV -> PQ (${remainingPvBusCount} bus(es) remain(s) PV)")
                .withValue("pvToPqBusCount", String.format("%6s", pvToPqBusCount))
                .withValue("remainingPvBusCount", String.format("%6s", remainingPvBusCount))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportPqToPvBuses(Reporter reporter, int pqToPvBusCount, int blockedPqBusCount) {
        reporter.report(Report.builder()
                .withKey("switchPqPv")
                .withDefaultMessage("${pqToPvBusCount} bus(es) switched PQ -> PV (${blockedPqBusCount} bus(es) blocked PQ due to the max number of switches)")
                .withValue("pqToPvBusCount", String.format("%6s", pqToPvBusCount))
                .withValue("blockedPqBusCount", String.format("%6s", blockedPqBusCount))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportBusForcedToBePv(Reporter reporter, String busId) {
        reporter.report(Report.builder()
                .withKey("busForcedToBePv")
                .withDefaultMessage("All PV buses should switch PQ, strongest one will stay PV: ${busId}")
                .withValue("busId", busId)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportBusesWithUpdatedQLimits(Reporter reporter, int numBusesWithUpdatedQLimits) {
        reporter.report(Report.builder()
                .withKey("busWithUpdatedQLimits")
                .withDefaultMessage("${numBusesWithUpdatedQLimits} bus(es) PQ blocked at its (their) min/max reactive power limit had its (their) min/max limit(s) updated")
                .withValue("numBusesWithUpdatedQLimits", String.format("%6s", numBusesWithUpdatedQLimits))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportReactiveControllerBusesToPqBuses(Reporter reporter, int remoteReactivePowerControllerBusToPqCount) {
        reporter.report(Report.builder()
                .withKey("remoteReactiveControllerBusToPq")
                .withDefaultMessage("${remoteReactivePowerControllerBusToPqCount} bus(es) with remote reactive power controller have switched PQ")
                .withValue("remoteReactivePowerControllerBusToPqCount", remoteReactivePowerControllerBusToPqCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportStandByAutomatonActivation(Reporter reporter, String busId, double newTargetV) {
        reporter.report(Report.builder()
                .withKey("standByAutomatonActivation")
                .withDefaultMessage("Activation of voltage control of SVC with stand by automaton: bus ${busId} switched PQ -> PV with targetV ${newTargetV}")
                .withValue("busId", busId)
                .withValue("newTargetV", newTargetV)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportCurrentLimiterPstsChangedTaps(Reporter reporter, int numOfCurrentLimiterPstsThatChangedTap) {
        reporter.report(Report.builder()
                .withKey("currentLimiterPstsChangedTaps")
                .withDefaultMessage("${numOfCurrentLimiterPstsThatChangedTap} current limiter PST(s) changed taps")
                .withValue("numOfCurrentLimiterPstsThatChangedTap", String.format("%6s", numOfCurrentLimiterPstsThatChangedTap))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportActivePowerControlPstsChangedTaps(Reporter reporter, int numOfActivePowerControlPstsThatChangedTap) {
        reporter.report(Report.builder()
                .withKey("activePowerControlPstsChangedTaps")
                .withDefaultMessage("${numOfActivePowerControlPstsThatChangedTap} active power control PST(s) changed taps")
                .withValue("numOfActivePowerControlPstsThatChangedTap", String.format("%6s", numOfActivePowerControlPstsThatChangedTap))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlBusesOutsideDeadband(Reporter reporter, int numTransformerControlBusesOutsideDeadband) {
        reporter.report(Report.builder()
                .withKey("transformerControlBusesOutsideDeadband")
                .withDefaultMessage("${numTransformerControlBusesOutsideDeadband} voltage-controlled bus(es) is (are) outside of its (their) target deadband(s)")
                .withValue("numTransformerControlBusesOutsideDeadband", String.format("%6s", numTransformerControlBusesOutsideDeadband))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlBranchesOutsideDeadband(Reporter reporter, int numTransformerControlBranchesOutsideDeadband) {
        reporter.report(Report.builder()
                .withKey("transformerControlBranchesOutsideDeadband")
                .withDefaultMessage("${numTransformerControlBranchesOutsideDeadband} reactive power-controlled branch(es) is (are) outside of its (their) target deadband(s)")
                .withValue("numTransformerControlBranchesOutsideDeadband", String.format("%6s", numTransformerControlBranchesOutsideDeadband))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlChangedTaps(Reporter reporter, int numTransformerControlAdjusted) {
        reporter.report(Report.builder()
                .withKey("transformerControlChangedTaps")
                .withDefaultMessage("${numTransformerControlAdjusted} transformer(s) changed at least one tap")
                .withValue("numTransformerControlAdjusted", String.format("%6s", numTransformerControlAdjusted))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportTransformerControlTapLimit(Reporter reporter, int numTransformerControlTapLimit) {
        reporter.report(Report.builder()
                .withKey("transformerControlTapLimit")
                .withDefaultMessage("${numTransformerControlTapLimit} transformer(s) reached its (their) tap limit(s)")
                .withValue("numTransformerControlTapLimit", String.format("%6s", numTransformerControlTapLimit))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportShuntVoltageControlChangedSusceptance(Reporter reporter, int numShuntVoltageControlAdjusted) {
        reporter.report(Report.builder()
                .withKey("shuntVoltageControlChangedSusceptance")
                .withDefaultMessage("${numShuntVoltageControlAdjusted} shunt(s) changed its (their) susceptance(s)")
                .withValue("numShuntVoltageControlAdjusted", String.format("%6s", numShuntVoltageControlAdjusted))
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportOuterLoopStatus(Reporter reporter, OuterLoopStatus outerLoopStatus) {
        TypedValue severity = outerLoopStatus == OuterLoopStatus.STABLE ? TypedValue.INFO_SEVERITY : TypedValue.ERROR_SEVERITY;
        reporter.report(Report.builder()
                .withKey("outerLoopStatus")
                .withDefaultMessage("Status: ${outerLoopStatus}")
                .withValue("outerLoopStatus", outerLoopStatus.name())
                .withSeverity(severity)
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
                .withDefaultMessage("${impactedGeneratorCount} generator(s) was (were) discarded from voltage control because it (they) didn't start")
                .withValue("impactedGeneratorCount", impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseReactiveRangeIsTooSmall")
                .withDefaultMessage("${impactedGeneratorCount} generator(s) was (were) discarded from voltage control because its (their) reactive range(s) is (are) too small")
                .withValue("impactedGeneratorCount", impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportGeneratorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits(Reporter reporter, int impactedGeneratorCount) {
        reporter.report(Report.builder()
                .withKey("generatorsDiscardedFromVoltageControlBecauseTargetPIsOutsideActiveLimits")
                .withDefaultMessage("${impactedGeneratorCount} generator(s) was (were) discarded from voltage control because its (their) targetP(s) is (are) outside its (their) active power limits")
                .withValue("impactedGeneratorCount", impactedGeneratorCount)
                .withSeverity(TypedValue.WARN_SEVERITY)
                .build());
    }

    public static void reportAcLfComplete(Reporter reporter, LoadFlowResult.ComponentResult.Status lfStatus, AcSolverStatus solverStatus) {
        TypedValue severity = lfStatus == LoadFlowResult.ComponentResult.Status.CONVERGED ? TypedValue.INFO_SEVERITY : TypedValue.ERROR_SEVERITY;
        reporter.report(Report.builder()
                .withKey("acLfCompletionStatus")
                .withDefaultMessage("AC load flow completion status: ${lfStatus}")
                .withValue("lfStatus", lfStatus.name())
                .withSeverity(severity)
                .build());
        reporter.report(Report.builder()
                .withKey("solverStatus")
                .withDefaultMessage("Solver status: ${solverStatus}")
                .withValue("solverStatus", solverStatus.name())
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

    public static Reporter createOuterLoopIterationReporter(Reporter reporter, int currentRunIteration, int totalIterations) {
        Map<String, TypedValue> subReporterMap = new HashMap<>();
        subReporterMap.put("currentRunIteration", new TypedValue(currentRunIteration, TypedValue.UNTYPED));
        subReporterMap.put("totalIterations", new TypedValue(totalIterations, TypedValue.UNTYPED));
        return reporter.createSubReporter("OuterLoopIteration", "Iteration ${currentRunIteration} (total=${totalIterations})", subReporterMap);
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
                                                                 int outerLoopTotalIterations, String outerLoopType, int currentRunIterations) {
        Reporter subReporter = reporter.createSubReporter("solver", solverName + " on Network CC${networkNumCc} SC${networkNumSc}",
                Map.of(NETWORK_NUM_CC, new TypedValue(networkNumCc, TypedValue.UNTYPED),
                        NETWORK_NUM_SC, new TypedValue(networkNumSc, TypedValue.UNTYPED)));
        subReporter.report(Report.builder()
                .withKey("solverOuterLoopCurrentType")
                .withDefaultMessage("Launched after iteration ${currentRunIterations} (total=${outerLoopTotalIterations}) of outer loop type ${outerLoopType}")
                .withValue("currentRunIterations", currentRunIterations)
                .withValue("outerLoopTotalIterations", outerLoopTotalIterations)
                .withValue("outerLoopType", outerLoopType)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
        return subReporter;
    }

    public static Reporter createNewtonRaphsonMismatchReporter(Reporter reporter, int iteration) {
        if (iteration == 0) {
            return reporter.createSubReporter("mismatchInitial", "Initial mismatch");
        } else {
            return reporter.createSubReporter("mismatchIteration", "Iteration ${iteration} ", ITERATION, iteration);
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

    public static void reportNewtonRaphsonNorm(Reporter reporter, double norm, int iteration) {
        ReportBuilder reportBuilder = Report.builder();
        if (iteration == 0) {
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

    public static void reportNewtonRaphsonMismatch(Reporter reporter, String acEquationType, double mismatch, NewtonRaphson.NewtonRaphsonMismatchBusInfo nRmismatchBusInfo) {
        Map<String, TypedValue> subReporterMap = new HashMap<>();
        subReporterMap.put("equationType", new TypedValue(acEquationType, TypedValue.UNTYPED));
        subReporterMap.put("mismatch", new TypedValue(mismatch, OpenLoadFlowReportConstants.MISMATCH_TYPED_VALUE));

        ReportBuilder busIdReportBuilder = Report.builder();
        busIdReportBuilder.withKey("NRMismatchBusId")
                .withDefaultMessage("Bus Id: ${busId}")
                .withValue("busId", nRmismatchBusInfo.busId())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busNominalVReportBuilder = Report.builder();
        busNominalVReportBuilder.withKey("NRMismatchBusNominalV")
                .withDefaultMessage("Bus nominal V [kV]: ${busNominalV}")
                .withValue("busNominalV", nRmismatchBusInfo.busNominalV())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busVReportBuilder = Report.builder();
        busVReportBuilder.withKey("NRMismatchBusV")
                .withDefaultMessage("Bus V [p.u.]: ${busV}")
                .withValue("busV", nRmismatchBusInfo.busV())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busPhiReportBuilder = Report.builder();
        busPhiReportBuilder.withKey("NRMismatchBusPhi")
                .withDefaultMessage("Bus Phi [rad]: ${busPhi}")
                .withValue("busPhi", nRmismatchBusInfo.busPhi())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busPReportBuilder = Report.builder();
        busPReportBuilder.withKey("NRMismatchBusSumP")
                .withDefaultMessage("Bus sum P [MW]: ${busSumP}")
                .withValue("busSumP", nRmismatchBusInfo.busSumP())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        ReportBuilder busQReportBuilder = Report.builder();
        busQReportBuilder.withKey("NRMismatchBusSumQ")
                .withDefaultMessage("Bus sum Q [MVar]: ${busSumQ}")
                .withValue("busSumQ", nRmismatchBusInfo.busSumQ())
                .withSeverity(TypedValue.TRACE_SEVERITY);

        Reporter subReporter = reporter.createSubReporter("NRMismatch", "Mismatch on ${equationType} : ${mismatch}", subReporterMap);
        subReporter.report(busIdReportBuilder.build());
        subReporter.report(busNominalVReportBuilder.build());
        subReporter.report(busVReportBuilder.build());
        subReporter.report(busPhiReportBuilder.build());
        subReporter.report(busPReportBuilder.build());
        subReporter.report(busQReportBuilder.build());
    }

    public static void reportLineSearchStateVectorScaling(Reporter reporter, double stepSize) {
        reporter.report(Report.builder()
                .withKey("lineSearchStateVectorScaling")
                .withDefaultMessage("Step size (line search): ${stepSize}")
                .withValue("stepSize", stepSize)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportMaxVoltageChangeStateVectorScaling(Reporter reporter, double stepSize, int vCutCount, int phiCutCount) {
        reporter.report(Report.builder()
                .withKey("maxVoltageChangeStateVectorScaling")
                .withDefaultMessage("Step size (max voltage change): ${stepSize} (${vCutCount} Vmagnitude and ${phiCutCount} Vangle changes outside thresholds)")
                .withValue("stepSize", stepSize)
                .withValue("vCutCount", vCutCount)
                .withValue("phiCutCount", phiCutCount)
                .withSeverity(TypedValue.INFO_SEVERITY)
                .build());
    }

    public static void reportNewtonRaphsonBusesOutOfNormalVoltageRange(Reporter reporter, Map<String, Double> busesOutOfNormalVoltageRange, double minRealisticVoltage, double maxRealisticVoltage) {
        reporter.report(Report.builder()
                .withKey("newtonRaphsonBusesOutOfNormalVoltageRange")
                .withDefaultMessage("${busCountOutOfNormalVoltageRange} bus(es) have its (their) voltage magnitude out of the range [${minRealisticVoltage}, ${maxRealisticVoltage}] p.u.: ${busesOutOfNormalVoltageRange}")
                .withValue("busCountOutOfNormalVoltageRange", busesOutOfNormalVoltageRange.size())
                .withValue("minRealisticVoltage", minRealisticVoltage)
                .withValue("maxRealisticVoltage", maxRealisticVoltage)
                .withValue("busesOutOfNormalVoltageRange", busesOutOfNormalVoltageRange.toString())
                .withSeverity(TypedValue.ERROR_SEVERITY)
                .build());
    }
}
