/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.google.common.base.Stopwatch;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.LfContingency;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysis implements SecurityAnalysis {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSecurityAnalysis.class);

    private final Network network;

    private final LimitViolationDetector detector;

    private final LimitViolationFilter filter;

    private final List<SecurityAnalysisInterceptor> interceptors = new ArrayList<>();

    private final MatrixFactory matrixFactory;

    private final Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider;

    private static final double POST_CONTINGENCY_INCREASING_FACTOR = 1.1;

    public OpenSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        this.network = Objects.requireNonNull(network);
        this.detector = Objects.requireNonNull(detector);
        this.filter = Objects.requireNonNull(filter);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.connectivityProvider = Objects.requireNonNull(connectivityProvider);
    }

    @Override
    public void addInterceptor(SecurityAnalysisInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
    }

    @Override
    public boolean removeInterceptor(SecurityAnalysisInterceptor interceptor) {
        return interceptors.remove(Objects.requireNonNull(interceptor));
    }

    @Override
    public CompletableFuture<SecurityAnalysisResult> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFuture.supplyAsync(() -> {
            String oldWorkingVariantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().setWorkingVariant(workingVariantId);
            SecurityAnalysisResult result = runSync(securityAnalysisParameters, contingenciesProvider);
            network.getVariantManager().setWorkingVariant(oldWorkingVariantId);
            return result;
        });
    }

    SecurityAnalysisResult runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());
        // in some post-contingency computation, it does not remain elements to participate to slack distribution.
        // in that case, the remaining mismatch is put on the slack bus and no exception is thrown.
        lfParametersExt.setThrowsExceptionInCaseOfSlackDistributionFailure(false);

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.create(network, contingencies, allSwitchesToOpen);

        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters, lfParametersExt, true);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, acParameters);

        // run simulation on largest network
        LfNetwork largestNetwork = lfNetworks.get(0);
        SecurityAnalysisResult result = runSimulations(largestNetwork, propagatedContingencies, acParameters, lfParameters, lfParametersExt);

        stopwatch.stop();
        LOGGER.info("Security analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return result;
    }

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, AcLoadFlowParameters acParameters) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            network.getSwitchStream().filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER)
                    .forEach(sw -> sw.setRetained(false));
            allSwitchesToOpen.forEach(sw -> sw.setRetained(true));
            lfNetworks = AcloadFlowEngine.createNetworks(network, acParameters);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }
        return lfNetworks;
    }

    /**
     * Detect violations on branches and on buses
     * @param branches branches on which the violation limits are checked
     * @param buses buses on which the violation limits are checked
     * @param violations list on which the violation limits encountered are added
     */
    private void detectViolations(Stream<LfBranch> branches, Stream<LfBus> buses, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // Detect violation limits on branches
        branches.forEach(branch -> detectBranchViolations(branch, violations));

        // Detect violation limits on buses
        buses.forEach(bus -> detectBusViolations(bus, violations));
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     * @param violations list on which the violation limits encountered are added
     */
    private void detectBranchViolations(LfBranch branch, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // detect violation limits on a branch
        if (branch.getBus1() != null) {
            branch.getViolationRanges1().stream()
                .filter(violationRange -> branch.getI1() > violationRange.getMinValue()) // only check min value as ranges are sorted and taking first
                .findFirst()
                .map(violationRange -> createLimitViolation1(branch, violationRange))
                .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));
        }
        if (branch.getBus2() != null) {
            branch.getViolationRanges2().stream()
                .filter(violationRange -> branch.getI2() > violationRange.getMinValue()) // only check min value as ranges are sorted
                .findFirst()
                .map(temporaryLimit2 -> createLimitViolation2(branch, temporaryLimit2))
                .ifPresent(limitViolation -> violations.put(getSubjectSideId(limitViolation), limitViolation));
        }
    }

    private static LimitViolation createLimitViolation1(LfBranch branch, AbstractLfBranch.LfViolationRange violationRange) {
        double scale1 = PerUnit.SB / branch.getBus1().getNominalV();
        return new LimitViolation(branch.getId(), LimitViolationType.CURRENT, null,
            violationRange.getAcceptableDuration(), violationRange.getMinValue() * scale1,
            (float) 1., branch.getI1() * scale1, Branch.Side.ONE);
    }

    private static LimitViolation createLimitViolation2(LfBranch branch, AbstractLfBranch.LfViolationRange violationRange) {
        double scale2 = PerUnit.SB / branch.getBus2().getNominalV();
        return new LimitViolation(branch.getId(), LimitViolationType.CURRENT, null,
            violationRange.getAcceptableDuration(), violationRange.getMinValue() * scale2,
            (float) 1., branch.getI2() * scale2, Branch.Side.TWO);
    }

    private static Pair<String, Branch.Side> getSubjectSideId(LimitViolation limitViolation) {
        return Pair.of(limitViolation.getSubjectId(), limitViolation.getSide());
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param bus branch of interest
     * @param violations list on which the violation limits encountered are added
     */
    private void detectBusViolations(LfBus bus, Map<Pair<String, Branch.Side>, LimitViolation> violations) {
        // detect violation limits on a bus
        double scale = bus.getNominalV();
        if (!Double.isNaN(bus.getHighVoltageLimit()) && bus.getV() > bus.getHighVoltageLimit()) {
            LimitViolation limitViolation1 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.HIGH_VOLTAGE, bus.getHighVoltageLimit() * scale,
                    (float) 1., bus.getV() * scale);
            violations.put(getSubjectSideId(limitViolation1), limitViolation1);
        }
        if (!Double.isNaN(bus.getLowVoltageLimit()) && bus.getV() < bus.getLowVoltageLimit()) {
            LimitViolation limitViolation2 = new LimitViolation(bus.getVoltageLevelId(), LimitViolationType.LOW_VOLTAGE, bus.getHighVoltageLimit() * scale,
                    (float) 1., bus.getV() * scale);
            violations.put(getSubjectSideId(limitViolation2), limitViolation2);
        }
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<PropagatedContingency> propagatedContingencies, AcLoadFlowParameters acParameters,
                                                  LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters openLoadFlowParameters) {
        // create a contingency list that impact the network
        List<LfContingency> contingencies = createContingencies(propagatedContingencies, network);

        // run pre-contingency simulation
        try (AcloadFlowEngine engine = new AcloadFlowEngine(network, acParameters)) {
            AcLoadFlowResult preContingencyLoadFlowResult = engine.run();
            boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
            Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations = new HashMap<>();

            // only run post-contingency simulations if pre-contingency simulation is ok
            List<PostContingencyResult> postContingencyResults = new ArrayList<>();
            if (preContingencyComputationOk) {
                detectViolations(network.getBranches().stream(), network.getBuses().stream(), preContingencyLimitViolations);

                LOGGER.info("Save pre-contingency state");

                // save base state for later restoration after each contingency
                Map<LfBus, BusState> busStates = BusState.createBusStates(network.getBuses());
                for (LfBus bus : network.getBuses()) {
                    bus.setVoltageControlSwitchOffCount(0);
                }

                // start a simulation for each of the contingency
                Iterator<LfContingency> contingencyIt = contingencies.iterator();
                while (contingencyIt.hasNext()) {
                    LfContingency lfContingency = contingencyIt.next();

                    for (LfBus bus : lfContingency.getBuses()) {
                        bus.setDisabled(true);
                    }

                    distributedMismatch(network, lfContingency.getActivePowerLoss(), loadFlowParameters, openLoadFlowParameters);

                    PostContingencyResult postContingencyResult = runPostContingencySimulation(network, engine, lfContingency, preContingencyLimitViolations);
                    postContingencyResults.add(postContingencyResult);

                    if (contingencyIt.hasNext()) {
                        LOGGER.info("Restore pre-contingency state");

                        // restore base state
                        BusState.restoreBusStates(busStates);
                    }
                }
            }

            LimitViolationsResult preContingencyResult = new LimitViolationsResult(preContingencyComputationOk, new ArrayList<>(preContingencyLimitViolations.values()));
            return new SecurityAnalysisResult(preContingencyResult, postContingencyResults);
        }
    }

    public static void distributedMismatch(LfNetwork network, double mismatch, LoadFlowParameters loadFlowParameters,
                                           OpenLoadFlowParameters openLoadFlowParameters) {
        if (loadFlowParameters.isDistributedSlack() && Math.abs(mismatch) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(loadFlowParameters.getBalanceType(), openLoadFlowParameters.isLoadPowerFactorConstant());
            activePowerDistribution.run(network, mismatch);
        }
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcloadFlowEngine engine, LfContingency lfContingency,
                                                               Map<Pair<String, Branch.Side>, LimitViolation> preContingencyLimitViolations) {
        LOGGER.info("Start post contingency '{}' simulation", lfContingency.getContingency().getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation> deactivatedEquations = new ArrayList<>();
        List<EquationTerm> deactivatedEquationTerms = new ArrayList<>();

        LfContingency.deactivateEquations(lfContingency, engine.getEquationSystem(), deactivatedEquations, deactivatedEquationTerms);

        // restart LF on post contingency equation system
        engine.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = engine.run();
        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        Map<Pair<String, Branch.Side>, LimitViolation> postContingencyLimitViolations = new HashMap<>();
        if (postContingencyComputationOk) {
            detectViolations(
                network.getBranches().stream().filter(b -> !lfContingency.getBranches().contains(b)),
                network.getBuses().stream().filter(b -> !lfContingency.getBuses().contains(b)),
                postContingencyLimitViolations);
        }

        preContingencyLimitViolations.forEach((subjectSideId, preContingencyViolation) -> {
            LimitViolation postContingencyViolation = postContingencyLimitViolations.get(subjectSideId);
            if (violationWeakenedOrEquivalent(preContingencyViolation, postContingencyViolation)) {
                postContingencyLimitViolations.remove(subjectSideId);
            }
        });

        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done in {} ms", lfContingency.getContingency().getId(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(lfContingency.getContingency(), postContingencyComputationOk, new ArrayList<>(postContingencyLimitViolations.values()));
    }

    /**
     * Compares two limit violations
     * @param violation1 first limit violation
     * @param violation2 second limit violation
     * @return true if violation2 is weaker than or equivalent to violation1, otherwise false
     */
    private static boolean violationWeakenedOrEquivalent(LimitViolation violation1, LimitViolation violation2) {
        if (violation2 != null) {
            if (violation2.getLimit() < violation1.getLimit()) {
                return true; // the limit violated is smaller hence the violation is weaker
            }
            if (violation2.getLimit() == violation1.getLimit()) {
                // the limit violated is the same: we consider the violations equivalent if the new value is close to previous one
                return violation2.getValue() <= violation1.getValue() * POST_CONTINGENCY_INCREASING_FACTOR;
            }
        }
        return false;
    }

    List<LfContingency> createContingencies(List<PropagatedContingency> propagatedContingencies, LfNetwork network) {
        return LfContingency.createContingencies(propagatedContingencies, network, network.createDecrementalConnectivity(connectivityProvider), true);
    }
}
