/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import com.powsybl.security.*;
import com.powsybl.security.action.Action;
import com.powsybl.security.detectors.DefaultLimitViolationDetector;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.*;
import com.powsybl.security.strategy.OperatorStrategy;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractOpenSecurityAnalysisTest {

    protected ComputationManager computationManager;

    protected MatrixFactory matrixFactory;

    protected OpenSecurityAnalysisProvider securityAnalysisProvider;

    protected OpenLoadFlowProvider loadFlowProvider;

    protected static Network createNodeBreakerNetwork() {
        Network network = NodeBreakerNetworkFactory.create();

        network.getLine("L1").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(1000)
                .endTemporaryLimit()
                .add();
        network.getLine("L1").newCurrentLimits2()
                .setPermanentLimit(940.0)
                .add();
        network.getLine("L2").newCurrentLimits1()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(950)
                .endTemporaryLimit()
                .add();
        network.getLine("L2").newCurrentLimits2()
                .setPermanentLimit(940.0)
                .beginTemporaryLimit()
                .setName("600")
                .setAcceptableDuration(600)
                .setValue(945)
                .endTemporaryLimit()
                .beginTemporaryLimit()
                .setName("60")
                .setAcceptableDuration(60)
                .setValue(970)
                .endTemporaryLimit()
                .add();

        return network;
    }

    protected LoadFlowResult runLoadFlow(Network network, LoadFlowParameters parameters) {
        return loadFlowProvider.run(network, computationManager, network.getVariantManager().getWorkingVariantId(), parameters)
                .join();
    }

    /**
     * Runs a security analysis with default parameters + most meshed slack bus selection
     */
    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                       LoadFlowParameters lfParameters) {
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(lfParameters);
        return runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters);
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                       SecurityAnalysisParameters saParameters) {
        return runSecurityAnalysis(network, contingencies, monitors, saParameters, Reporter.NO_OP);
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                       SecurityAnalysisParameters saParameters, Reporter reporter) {
        ContingenciesProvider provider = n -> contingencies;
        SecurityAnalysisReport report = securityAnalysisProvider.run(network,
                network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(),
                new LimitViolationFilter(),
                computationManager,
                saParameters,
                provider,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                monitors,
                reporter)
                .join();
        return report.getResult();
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors,
                                                       SecurityAnalysisParameters saParameters, List<OperatorStrategy> operatorStrategies,
                                                       List<Action> actions, Reporter reporter) {
        ContingenciesProvider provider = n -> contingencies;
        SecurityAnalysisReport report = securityAnalysisProvider.run(network,
                network.getVariantManager().getWorkingVariantId(),
                new DefaultLimitViolationDetector(),
                new LimitViolationFilter(),
                computationManager,
                saParameters,
                provider,
                Collections.emptyList(),
                operatorStrategies,
                actions,
                monitors,
                reporter)
                .join();
        return report.getResult();
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, List<StateMonitor> monitors) {
        return runSecurityAnalysis(network, contingencies, monitors, new LoadFlowParameters());
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies, LoadFlowParameters loadFlowParameters) {
        return runSecurityAnalysis(network, contingencies, Collections.emptyList(), loadFlowParameters);
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network, List<Contingency> contingencies) {
        return runSecurityAnalysis(network, contingencies, Collections.emptyList());
    }

    protected SecurityAnalysisResult runSecurityAnalysis(Network network) {
        return runSecurityAnalysis(network, Collections.emptyList(), Collections.emptyList());
    }

    protected static List<StateMonitor> createAllBranchesMonitors(Network network) {
        Set<String> allBranchIds = network.getBranchStream().map(Identifiable::getId).collect(Collectors.toSet());
        return List.of(new StateMonitor(ContingencyContext.all(), allBranchIds, Collections.emptySet(), Collections.emptySet()));
    }

    protected static List<StateMonitor> createNetworkMonitors(Network network) {
        Set<String> allBranchIds = network.getBranchStream().map(Identifiable::getId).collect(Collectors.toSet());
        Set<String> allVoltageLevelIds = network.getVoltageLevelStream().map(Identifiable::getId).collect(Collectors.toSet());
        return List.of(new StateMonitor(ContingencyContext.all(), allBranchIds, allVoltageLevelIds, Collections.emptySet()));
    }

    protected static List<Contingency> createAllBranchesContingencies(Network network) {
        return network.getBranchStream()
                .map(b -> new Contingency(b.getId(), new BranchContingency(b.getId())))
                .collect(Collectors.toList());
    }

    protected static void setSlackBusId(LoadFlowParameters lfParameters, String slackBusId) {
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId(slackBusId);
    }

    protected static Optional<PostContingencyResult> getOptionalPostContingencyResult(SecurityAnalysisResult result, String contingencyId) {
        return result.getPostContingencyResults().stream()
                .filter(r -> r.getContingency().getId().equals(contingencyId))
                .findFirst();
    }

    protected static PostContingencyResult getPostContingencyResult(SecurityAnalysisResult result, String contingencyId) {
        return getOptionalPostContingencyResult(result, contingencyId)
                .orElseThrow();
    }

    protected static Optional<OperatorStrategyResult> getOptionalOperatorStrategyResult(SecurityAnalysisResult result, String operatorStrategyId) {
        return result.getOperatorStrategyResults().stream()
                .filter(r -> r.getOperatorStrategy().getId().equals(operatorStrategyId))
                .findFirst();
    }

    protected static OperatorStrategyResult getOperatorStrategyResult(SecurityAnalysisResult result, String operatorStrategyId) {
        return getOptionalOperatorStrategyResult(result, operatorStrategyId)
                .orElseThrow();
    }

    protected static void assertAlmostEquals(BusResult expected, BusResult actual, double epsilon) {
        assertEquals(expected.getVoltageLevelId(), actual.getVoltageLevelId());
        assertEquals(expected.getBusId(), actual.getBusId());
        assertEquals(expected.getV(), actual.getV(), epsilon);
        assertEquals(expected.getAngle(), actual.getAngle(), epsilon);
    }

    protected static void assertAlmostEquals(BranchResult expected, BranchResult actual, double epsilon) {
        assertEquals(expected.getBranchId(), actual.getBranchId());
        assertEquals(expected.getP1(), actual.getP1(), epsilon);
        assertEquals(expected.getQ1(), actual.getQ1(), epsilon);
        assertEquals(expected.getI1(), actual.getI1(), epsilon);
        assertEquals(expected.getP2(), actual.getP2(), epsilon);
        assertEquals(expected.getQ2(), actual.getQ2(), epsilon);
        assertEquals(expected.getI2(), actual.getI2(), epsilon);
    }

    protected static void assertAlmostEquals(ThreeWindingsTransformerResult expected, ThreeWindingsTransformerResult actual, double epsilon) {
        assertEquals(expected.getThreeWindingsTransformerId(), actual.getThreeWindingsTransformerId());
        assertEquals(expected.getP1(), actual.getP1(), epsilon);
        assertEquals(expected.getQ1(), actual.getQ1(), epsilon);
        assertEquals(expected.getI1(), actual.getI1(), epsilon);
        assertEquals(expected.getP2(), actual.getP2(), epsilon);
        assertEquals(expected.getQ2(), actual.getQ2(), epsilon);
        assertEquals(expected.getI2(), actual.getI2(), epsilon);
        assertEquals(expected.getP3(), actual.getP3(), epsilon);
        assertEquals(expected.getQ3(), actual.getQ3(), epsilon);
        assertEquals(expected.getI3(), actual.getI3(), epsilon);
    }
}
