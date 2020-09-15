/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.common.base.Stopwatch;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.ReactiveLimitsOuterLoop;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

    private final Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider;

    public OpenSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                MatrixFactory matrixFactory, Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
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

    static class ContingencyContext {

        final Contingency contingency;

        final Set<String> branchIdsToOpen = new HashSet<>();

        ContingencyContext(Contingency contingency) {
            this.contingency = contingency;
        }
    }

    SecurityAnalysisResult runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all switches impacted by at least one contingency and for each contingency the branches impacted
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<ContingencyContext> contingencyContexts = getContingencyContexts(contingencies, allSwitchesToOpen);

        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters, lfParametersExt, true);

        // create networks including all necessary switches
        List<LfNetwork> lfNetworks = createNetworks(allSwitchesToOpen, acParameters);

        // run simulation on largest network
        LfNetwork largestNetwork = lfNetworks.get(0);
        SecurityAnalysisResult result = runSimulations(largestNetwork, contingencyContexts, acParameters, lfParametersExt);

        stopwatch.stop();
        LOGGER.info("Security analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return result;
    }

    List<ContingencyContext> getContingencyContexts(List<Contingency> contingencies, Set<Switch> allSwitchesToOpen) {
        List<ContingencyContext> contingencyContexts = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            ContingencyContext contingencyContext = new ContingencyContext(contingency);
            contingencyContexts.add(contingencyContext);

            Set<Switch> switchesToOpen = new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        contingencyContext.branchIdsToOpen.add(element.getId());
                        break;
                    default:
                        throw new UnsupportedOperationException("TODO");
                }
                AbstractTrippingTask task = element.toTask();
                task.traverse(network, null, switchesToOpen, new HashSet<>());
            }

            for (Switch sw : switchesToOpen) {
                contingencyContext.branchIdsToOpen.add(sw.getId());
                allSwitchesToOpen.add(sw);
            }
        }
        return contingencyContexts;
    }

    List<LfNetwork> createNetworks(Set<Switch> allSwitchesToOpen, AcLoadFlowParameters acParameters) {
        List<LfNetwork> lfNetworks;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            for (Switch sw : network.getSwitches()) {
                sw.setRetained(false);
            }
            for (Switch sw : allSwitchesToOpen) {
                sw.setRetained(true);
            }
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
    private void detectViolations(Stream<LfBranch> branches, Stream<LfBus> buses, List<LimitViolation> violations) {
        // Detect violation limits on branches
        branches.forEach(b -> detectBranchViolations(b, violations));

        // Detect violation limits on buses
        // TODO
    }

    /**
     * Detect violation limits on one branch and add them to the given list
     * @param branch branch of interest
     * @param violations list on which the violation limits encountered are added
     */
    private void detectBranchViolations(LfBranch branch, List<LimitViolation> violations) {
        // Detect violation on the left
        detectBranchViolationSide(branch.getId(), Branch.Side.ONE, branch.getBus1(), branch.getI1(), branch.getPermanentLimit1(), violations);

        // Detect violation on the right
        detectBranchViolationSide(branch.getId(), Branch.Side.TWO, branch.getBus2(), branch.getI2(), branch.getPermanentLimit2(), violations);
    }

    private void detectBranchViolationSide(String branchId, Branch.Side branchSide, LfBus busOfSide, double iOfSide,
                                           double permanentLimitOfSide, List<LimitViolation> violations) {
        if (busOfSide != null && iOfSide > permanentLimitOfSide) {
            double scale = PerUnit.SB / busOfSide.getNominalV();
            LimitViolation limitViolation1 = new LimitViolation(
                branchId, LimitViolationType.CURRENT, null, 2147483647,
                permanentLimitOfSide * scale, 0.f, iOfSide * scale, branchSide);
            violations.add(limitViolation1);
        }
    }

    private SecurityAnalysisResult runSimulations(LfNetwork network, List<ContingencyContext> contingencyContexts, AcLoadFlowParameters acParameters,
                                                  OpenLoadFlowParameters openLoadFlowParameters) {
        // create a contingency list that impact the network
        List<LfContingency> contingencies = createContingencies(contingencyContexts, network);

        // run pre-contingency simulation
        AcloadFlowEngine engine = new AcloadFlowEngine(network, acParameters);
        AcLoadFlowResult preContingencyLoadFlowResult = engine.run();
        boolean preContingencyComputationOk = preContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        List<LimitViolation> preContingencyLimitViolations = new ArrayList<>();
        LimitViolationsResult preContingencyResult = new LimitViolationsResult(preContingencyComputationOk, preContingencyLimitViolations);

        // only run post-contingency simulations if pre-contingency simulation is ok
        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        if (preContingencyComputationOk) {
            detectViolations(network.getBranches().stream(), network.getBuses().stream(), preContingencyLimitViolations);

            LOGGER.info("Save pre-contingency state");

            // save base state for later restoration after each contingency
            List<LfBus> buses = network.getBuses();
            double[] v = new double[buses.size()];
            double[] a = new double[buses.size()];
            double[] loadTargetP = new double[buses.size()];
            double[] generatorsTargetP = new double[buses.size()];
            int generatorsCount = 0;
            for (LfBus bus : buses) {
                v[bus.getNum()] = bus.getV();
                a[bus.getNum()] = bus.getAngle();
                loadTargetP[bus.getNum()] = bus.getLoadTargetP();
                for (LfGenerator generator : bus.getGenerators()) {
                    generatorsTargetP[generatorsCount] = generator.getTargetP();
                    generatorsCount++;
                }
                if (bus.hasVoltageControlCapability() && !bus.hasVoltageControl()) { // Bus PV -> PQ
                    ReactiveLimitsOuterLoop.switchPqPv(bus, engine.getEquationSystem(), engine.getVariableSet());
                    bus.setVoltageControlSwitchOffCount(0);
                }
            }

            // start a simulation for each of the contingency
            Iterator<LfContingency> contingencyIt = contingencies.iterator();
            while (contingencyIt.hasNext()) {
                LfContingency lfContingency = contingencyIt.next();

                PostContingencyResult postContingencyResult = runPostContingencySimulation(network, engine, lfContingency);
                postContingencyResults.add(postContingencyResult);

                if (contingencyIt.hasNext()) {
                    LOGGER.info("Restore pre-contingency state");

                    double mismatch = 0;
                    for (LfBus bus : lfContingency.getBuses()) {
                        mismatch += bus.getGenerationTargetP() - bus.getLoadTargetP();
                    } //TODO: if mismatch different than zero, start with a slack distribution.

                    // restore base state
                    generatorsCount = 0;
                    for (LfBus bus : buses) {
                        bus.setV(v[bus.getNum()]);
                        bus.setAngle(a[bus.getNum()]);
                        bus.setLoadTargetP(loadTargetP[bus.getNum()]);
                        for (LfGenerator generator : bus.getGenerators()) {
                            generator.setTargetP(generatorsTargetP[generatorsCount]);
                            generatorsCount++;
                        }
                        if (bus.hasVoltageControlCapability() && !bus.hasVoltageControl()) { // Bus PV -> PQ
                            ReactiveLimitsOuterLoop.switchPqPv(bus, engine.getEquationSystem(), engine.getVariableSet());
                            bus.setVoltageControlSwitchOffCount(0);
                        }
                    }
                }
            }
        }

        return new SecurityAnalysisResult(preContingencyResult, postContingencyResults);
    }

    private PostContingencyResult runPostContingencySimulation(LfNetwork network, AcloadFlowEngine engine, LfContingency lfContingency) {
        LOGGER.info("Start post contingency '{}' simulation", lfContingency.getContingency().getId());

        Stopwatch stopwatch = Stopwatch.createStarted();

        EquationSystem equationSystem = engine.getEquationSystem();

        List<Equation> deactivatedEquations = new ArrayList<>();
        List<EquationTerm> deactivatedEquationTerms = new ArrayList<>();

        for (LfBranch branch : lfContingency.getBranches()) {
            LOGGER.trace("Remove equations and equations terms related to branch '{}'", branch.getId());

            // deactivate all equations related to a branch
            for (Equation equation : equationSystem.getEquations(SubjectType.BRANCH, branch.getNum())) {
                if (equation.isActive()) {
                    equation.setActive(false);
                    deactivatedEquations.add(equation);
                }
            }

            // deactivate all equation terms related to a branch
            for (EquationTerm equationTerm : equationSystem.getEquationTerms(SubjectType.BRANCH, branch.getNum())) {
                if (equationTerm.isActive()) {
                    equationTerm.setActive(false);
                    deactivatedEquationTerms.add(equationTerm);
                }
            }
        }

        for (LfBus bus : lfContingency.getBuses()) {
            LOGGER.trace("Remove equations and equation terms related to bus '{}'", bus.getId());

            // deactivate all equations related to a bus
            for (Equation equation : equationSystem.getEquations(SubjectType.BUS, bus.getNum())) {
                if (equation.isActive()) {
                    equation.setActive(false);
                    deactivatedEquations.add(equation);
                }
            }

            // deactivate all equation terms related to a bus
            for (EquationTerm equationTerm : equationSystem.getEquationTerms(SubjectType.BUS, bus.getNum())) {
                if (equationTerm.isActive()) {
                    equationTerm.setActive(false);
                    deactivatedEquationTerms.add(equationTerm);
                }
            }
        }

        // restart LF on post contingency equation system
        engine.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        AcLoadFlowResult postContingencyLoadFlowResult = engine.run();
        boolean postContingencyComputationOk = postContingencyLoadFlowResult.getNewtonRaphsonStatus() == NewtonRaphsonStatus.CONVERGED;
        List<LimitViolation> postContingencyLimitViolations = new ArrayList<>();
        if (postContingencyComputationOk) {
            detectViolations(
                network.getBranches().stream().filter(b -> !lfContingency.getBranches().contains(b)),
                network.getBuses().stream().filter(b -> !lfContingency.getBuses().contains(b)),
                postContingencyLimitViolations);
        }

        // restore deactivated equations and equations terms from previous contingency
        if (!deactivatedEquations.isEmpty()) {
            for (Equation equation : deactivatedEquations) {
                equation.setActive(true);
            }
            deactivatedEquations.clear();
        }
        if (!deactivatedEquationTerms.isEmpty()) {
            for (EquationTerm equationTerm : deactivatedEquationTerms) {
                equationTerm.setActive(true);
            }
            deactivatedEquationTerms.clear();
        }

        stopwatch.stop();
        LOGGER.info("Post contingency '{}' simulation done in {} ms", lfContingency.getContingency().getId(),
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return new PostContingencyResult(lfContingency.getContingency(), postContingencyComputationOk, postContingencyLimitViolations);
    }

    List<LfContingency> createContingencies(List<ContingencyContext> contingencyContexts, LfNetwork network) {
        // create connectivity data structure
        GraphDecrementalConnectivity<LfBus> connectivity = createConnectivity(network);

        List<LfContingency> contingencies = new ArrayList<>();
        Iterator<ContingencyContext> contingencyContextIt = contingencyContexts.iterator();
        while (contingencyContextIt.hasNext()) {
            ContingencyContext contingencyContext = contingencyContextIt.next();

            // find contingency branches that are part of this network
            Set<LfBranch> branches = new HashSet<>(1);
            Iterator<String> branchIt = contingencyContext.branchIdsToOpen.iterator();
            while (branchIt.hasNext()) {
                String branchId = branchIt.next();
                LfBranch branch = network.getBranchById(branchId);
                if (branch != null) {
                    branches.add(branch);
                    branchIt.remove();
                }
            }

            // if no more branch in the contingency, remove contingency from the list because
            // it won't be part of another network
            if (contingencyContext.branchIdsToOpen.isEmpty()) {
                contingencyContextIt.remove();
            }

            // check if contingency split this network into multiple components
            if (branches.isEmpty()) {
                continue;
            }

            // update connectivity with triggered branches
            for (LfBranch branch : branches) {
                LfBus bus1 = branch.getBus1();
                LfBus bus2 = branch.getBus2();
                if (bus1 != null && bus2 != null) {
                    connectivity.cut(bus1, bus2);
                }
            }

            // add to contingency description buses and branches that won't be part of the main connected
            // component in post contingency state
            Set<LfBus> buses = connectivity.getSmallComponents().stream().flatMap(Set::stream).collect(Collectors.toSet());
            network.getBranches().stream()
                .filter(br -> buses.contains(br.getBus1()) && buses.contains(br.getBus2()))
                .forEach(branches::add);

            // reset connectivity to discard triggered branches
            connectivity.reset();

            contingencies.add(new LfContingency(contingencyContext.contingency, buses, branches));
        }

        return contingencies;
    }

    private GraphDecrementalConnectivity<LfBus> createConnectivity(LfNetwork network) {
        GraphDecrementalConnectivity<LfBus> connectivity = connectivityProvider.get();
        for (LfBus bus : network.getBuses()) {
            connectivity.addVertex(bus);
        }
        for (LfBranch branch : network.getBranches()) {
            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 != null && bus2 != null) {
                connectivity.addEdge(bus1, bus2);
            }
        }
        return connectivity;
    }
}
