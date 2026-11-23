/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.BusbarSectionContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.GeneratorContingency;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.contingency.violations.LimitViolationFilter;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.action.Actions;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OpenSecurityAnalysisParameters#isKeepOnlyNecessarySwitches()}: a contingency keeps only the switches that
 * separate parts of a voltage level that stay connected to the network, and loses the equipment the others cut off.
 *
 * <p>The network: a voltage level VL1 with three busbar sections in a row, BBS1 - BBS2 - BBS3, joined by section
 * breakers, and optionally a coupler BBS1 - BBS3. BBS1 holds a generator with its auxiliary load behind the same
 * breaker, and a line to VL2; BBS3 a second generator and a second line to VL2; BBS2 nothing. VL2 holds the two line
 * ends and a load. Optionally, a spare bay joins the bay of G1 to BBS3 through an open breaker.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class KeepOnlyNecessarySwitchesTest {

    private static void disconnector(VoltageLevel vl, String id, int node1, int node2) {
        vl.getNodeBreakerView().newDisconnector().setId(id).setNode1(node1).setNode2(node2).add();
    }

    private static void breaker(VoltageLevel vl, String id, int node1, int node2) {
        vl.getNodeBreakerView().newBreaker().setId(id).setNode1(node1).setNode2(node2).add();
    }

    static Network createNetwork(boolean coupler13) {
        return createNetwork(coupler13, false);
    }

    static Network createNetwork(boolean coupler13, boolean spareBayG1) {
        Network network = Network.create("keep-only-necessary-switches", "test");
        Substation s1 = network.newSubstation().setId("S1").add();
        VoltageLevel vl1 = s1.newVoltageLevel().setId("VL1").setNominalV(400).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        vl1.getNodeBreakerView().newBusbarSection().setId("BBS1").setNode(0).add();
        vl1.getNodeBreakerView().newBusbarSection().setId("BBS2").setNode(1).add();
        vl1.getNodeBreakerView().newBusbarSection().setId("BBS3").setNode(2).add();
        // section breakers BBS1 - BBS2 - BBS3
        disconnector(vl1, "D12A", 0, 10);
        breaker(vl1, "B12", 10, 11);
        disconnector(vl1, "D12B", 11, 1);
        disconnector(vl1, "D23A", 1, 12);
        breaker(vl1, "B23", 12, 13);
        disconnector(vl1, "D23B", 13, 2);
        if (coupler13) {
            disconnector(vl1, "D13A", 0, 14);
            breaker(vl1, "B13", 14, 15);
            disconnector(vl1, "D13B", 15, 2);
        }
        // generator G1 and its auxiliary load LA behind the same breaker, on BBS1
        disconnector(vl1, "DG1", 0, 20);
        breaker(vl1, "BG1", 20, 21);
        vl1.getNodeBreakerView().newInternalConnection().setNode1(21).setNode2(22).add();
        vl1.newGenerator().setId("G1").setNode(21).setMinP(0).setMaxP(500).setTargetP(200).setTargetV(400)
                .setVoltageRegulatorOn(true).add();
        vl1.newLoad().setId("LA").setNode(22).setP0(10).setQ0(2).add();
        if (spareBayG1) {
            disconnector(vl1, "DG1S", 2, 23);
            vl1.getNodeBreakerView().newBreaker().setId("BG1S").setNode1(23).setNode2(21).setOpen(true).add();
        }
        // generator G2 on BBS3
        disconnector(vl1, "DG2", 2, 30);
        breaker(vl1, "BG2", 30, 31);
        vl1.newGenerator().setId("G2").setNode(31).setMinP(0).setMaxP(500).setTargetP(100).setTargetV(400)
                .setVoltageRegulatorOn(true).add();
        // line bays: L1 on BBS1, L2 on BBS3
        disconnector(vl1, "DL1", 0, 40);
        breaker(vl1, "BL1", 40, 41);
        disconnector(vl1, "DL2", 2, 42);
        breaker(vl1, "BL2", 42, 43);

        Substation s2 = network.newSubstation().setId("S2").add();
        VoltageLevel vl2 = s2.newVoltageLevel().setId("VL2").setNominalV(400).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        vl2.getNodeBreakerView().newBusbarSection().setId("BBS4").setNode(0).add();
        disconnector(vl2, "DL1_2", 0, 1);
        breaker(vl2, "BL1_2", 1, 2);
        disconnector(vl2, "DL2_2", 0, 3);
        breaker(vl2, "BL2_2", 3, 4);
        disconnector(vl2, "DLD", 0, 5);
        breaker(vl2, "BLD", 5, 6);
        vl2.newLoad().setId("LD").setNode(6).setP0(280).setQ0(40).add();

        network.newLine().setId("L1").setVoltageLevel1("VL1").setNode1(41).setVoltageLevel2("VL2").setNode2(2)
                .setR(1).setX(10).add();
        network.newLine().setId("L2").setVoltageLevel1("VL1").setNode1(43).setVoltageLevel2("VL2").setNode2(4)
                .setR(1).setX(12).add();
        return network;
    }

    /** The switches the network keeps for a contingency. */
    private static Set<String> keptSwitches(Network network, Contingency contingency, boolean keepOnlyNecessarySwitches) {
        return keptSwitches(network, contingency, keepOnlyNecessarySwitches, List.of());
    }

    /** The switches the network keeps for a contingency, with those the actions operate. */
    private static Set<String> keptSwitches(Network network, Contingency contingency, boolean keepOnlyNecessarySwitches,
                                            List<Action> actions) {
        LfTopoConfig topoConfig = new LfTopoConfig();
        Actions.addAllSwitchesToOperate(topoConfig, network, actions);
        PropagatedContingency.createList(network, List.of(contingency), topoConfig, new PropagatedContingencyCreationParameters()
                .setContingencyPropagation(true)
                .setKeepOnlyNecessarySwitches(keepOnlyNecessarySwitches));
        return topoConfig.getSwitchesToOpen().stream().map(Switch::getId).collect(Collectors.toSet());
    }

    private static SecurityAnalysisResult run(Network network, List<Contingency> contingencies, boolean keepOnlyNecessarySwitches) {
        return run(network, contingencies, keepOnlyNecessarySwitches, List.of(), List.of());
    }

    private static SecurityAnalysisResult run(Network network, List<Contingency> contingencies, boolean keepOnlyNecessarySwitches,
                                              List<Action> actions, List<OperatorStrategy> operatorStrategies) {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters().setLoadFlowParameters(new LoadFlowParameters());
        parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters()
                .setKeepOnlyNecessarySwitches(keepOnlyNecessarySwitches));
        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .setFilter(new LimitViolationFilter())
                .setComputationManager(LocalComputationManager.getDefault())
                .setSecurityAnalysisParameters(parameters)
                .setMonitors(List.of(new StateMonitor(com.powsybl.contingency.ContingencyContext.all(), Set.of("L1", "L2"), Set.of(), Set.of())))
                .setActions(actions)
                .setOperatorStrategies(operatorStrategies)
                .setReportNode(ReportNode.NO_OP);
        return new OpenSecurityAnalysisProvider(new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>())
                .run(network, network.getVariantManager().getWorkingVariantId(), n -> contingencies, runParameters)
                .join().getResult();
    }

    private static Optional<PostContingencyResult> result(SecurityAnalysisResult result, String contingencyId) {
        return result.getPostContingencyResults().stream().filter(r -> r.getContingency().getId().equals(contingencyId)).findFirst();
    }

    /** The flows of the two lines after a contingency, with and without the parameter, are the same. */
    private static void assertSameFlows(SecurityAnalysisResult expected, SecurityAnalysisResult actual, String contingencyId) {
        PostContingencyResult e = result(expected, contingencyId).orElseThrow();
        PostContingencyResult a = result(actual, contingencyId).orElseThrow();
        assertEquals(e.getStatus(), a.getStatus(), contingencyId);
        assertSameFlows(e.getNetworkResult(), a.getNetworkResult(), contingencyId);
        assertEquals(e.getConnectivityResult().getDisconnectedLoadActivePower(), a.getConnectivityResult().getDisconnectedLoadActivePower(), 1e-6, contingencyId);
        assertEquals(e.getConnectivityResult().getDisconnectedGenerationActivePower(), a.getConnectivityResult().getDisconnectedGenerationActivePower(), 1e-6, contingencyId);
    }

    /** The flows of the two lines after an operator strategy, with and without the parameter, are the same. */
    private static void assertSameStrategyFlows(SecurityAnalysisResult expected, SecurityAnalysisResult actual, String operatorStrategyId) {
        OperatorStrategyResult e = expected.getOperatorStrategyResults().stream().filter(r -> r.getOperatorStrategy().getId().equals(operatorStrategyId)).findFirst().orElseThrow();
        OperatorStrategyResult a = actual.getOperatorStrategyResults().stream().filter(r -> r.getOperatorStrategy().getId().equals(operatorStrategyId)).findFirst().orElseThrow();
        assertEquals(e.getStatus(), a.getStatus(), operatorStrategyId);
        assertSameFlows(e.getNetworkResult(), a.getNetworkResult(), operatorStrategyId);
    }

    private static void assertSameFlows(NetworkResult e, NetworkResult a, String id) {
        for (String line : List.of("L1", "L2")) {
            BranchResult be = e.getBranchResult(line);
            BranchResult ba = a.getBranchResult(line);
            if (be == null) {
                assertNull(ba, id + " " + line);
                continue;
            }
            assertEquals(be.getP1(), ba.getP1(), 1e-6, id + " " + line);
            assertEquals(be.getQ1(), ba.getQ1(), 1e-6, id + " " + line);
            assertEquals(be.getI1(), ba.getI1(), 1e-6, id + " " + line);
        }
    }

    @Test
    void generatorBayWithItsAuxiliaryLoad() {
        Network network = createNetwork(false);
        Contingency contingency = new Contingency("G1", new GeneratorContingency("G1"));
        assertEquals(Set.of("BG1"), keptSwitches(network, contingency, false));
        assertEquals(Set.of(), keptSwitches(network, contingency, true));
        SecurityAnalysisResult expected = run(network, List.of(contingency), false);
        SecurityAnalysisResult actual = run(network, List.of(contingency), true);
        assertSameFlows(expected, actual, "G1");
        // the auxiliary load is lost with the generator, as when its bay is cut off
        assertEquals(10, actual.getPostContingencyResults().getFirst().getConnectivityResult().getDisconnectedLoadActivePower(), 1e-6);
    }

    @Test
    void lineBay() {
        Network network = createNetwork(false);
        Contingency contingency = new Contingency("L1", new BranchContingency("L1"));
        assertEquals(Set.of(), keptSwitches(network, contingency, true));
        assertSameFlows(run(network, List.of(contingency), false), run(network, List.of(contingency), true), "L1");
    }

    @Test
    void busbarSectionBetweenTwoOthers() {
        // losing BBS2 separates BBS1 from BBS3: its section breakers are kept
        Network network = createNetwork(false);
        Contingency contingency = new Contingency("BBS2", new BusbarSectionContingency("BBS2"));
        Set<String> kept = keptSwitches(network, contingency, true);
        assertEquals(keptSwitches(network, contingency, false), kept);
        assertFalse(kept.isEmpty());
        SecurityAnalysisResult expected = run(network, List.of(contingency), false);
        SecurityAnalysisResult actual = run(network, List.of(contingency), true);
        assertSameFlows(expected, actual, "BBS2");
        assertEquals(PostContingencyComputationStatus.CONVERGED, result(actual, "BBS2").orElseThrow().getStatus());
    }

    @Test
    void redundantBusbarSection() {
        // with the coupler BBS1 - BBS3, losing BBS2 leaves the voltage level connected; BBS2 holds nothing to
        // disconnect, so its switches are kept: the contingency loses the bus of BBS2 and has the base case flows, as
        // without the parameter, instead of having no result
        Network network = createNetwork(true);
        Contingency contingency = new Contingency("BBS2", new BusbarSectionContingency("BBS2"));
        assertFalse(keptSwitches(network, contingency, false).isEmpty());
        assertEquals(keptSwitches(network, contingency, false), keptSwitches(network, contingency, true));
        SecurityAnalysisResult expected = run(network, List.of(contingency), false);
        SecurityAnalysisResult actual = run(network, List.of(contingency), true);
        assertSameFlows(expected, actual, "BBS2");
        BranchResult base = expected.getPreContingencyResult().getNetworkResult().getBranchResult("L1");
        assertEquals(base.getP1(), result(actual, "BBS2").orElseThrow().getNetworkResult().getBranchResult("L1").getP1(), 1e-6);
    }

    @Test
    void couplerAStrategyOpens() {
        // with the coupler BBS1 - BBS3, losing BBS2 leaves the voltage level connected, but a strategy may open the
        // coupler: BBS2 then separates BBS1 from BBS3, and its section breakers are kept
        Network network = createNetwork(true);
        Contingency contingency = new Contingency("BBS2", new BusbarSectionContingency("BBS2"));
        List<Action> actions = List.of(new SwitchAction("openB13", "B13", true));
        List<OperatorStrategy> strategies = List.of(new OperatorStrategy("strategy", ContingencyContext.specificContingency("BBS2"),
                new TrueCondition(), List.of("openB13")));
        assertEquals(keptSwitches(network, contingency, false, actions), keptSwitches(network, contingency, true, actions));
        SecurityAnalysisResult expected = run(network, List.of(contingency), false, actions, strategies);
        SecurityAnalysisResult actual = run(network, List.of(contingency), true, actions, strategies);
        assertSameFlows(expected, actual, "BBS2");
        assertSameStrategyFlows(expected, actual, "strategy");
    }

    @Test
    void bayAStrategyBringsBack() {
        // losing G1 cuts off its bay with the auxiliary load LA; a strategy closing the spare breaker BG1S brings the bay
        // back on BBS3 with LA: the breaker of the bay is kept, instead of disconnecting LA
        Network network = createNetwork(false, true);
        Contingency contingency = new Contingency("G1", new GeneratorContingency("G1"));
        List<Action> actions = List.of(new SwitchAction("closeBG1S", "BG1S", false));
        List<OperatorStrategy> strategies = List.of(new OperatorStrategy("strategy", ContingencyContext.specificContingency("G1"),
                new TrueCondition(), List.of("closeBG1S")));
        assertEquals(keptSwitches(network, contingency, false, actions), keptSwitches(network, contingency, true, actions));
        SecurityAnalysisResult expected = run(network, List.of(contingency), false, actions, strategies);
        SecurityAnalysisResult actual = run(network, List.of(contingency), true, actions, strategies);
        assertSameFlows(expected, actual, "G1");
        assertSameStrategyFlows(expected, actual, "strategy");
    }

    @Test
    void sameResultsForEveryElement() {
        // every element and busbar section of the network, with and without the parameter
        Network network = createNetwork(false);
        List<Contingency> contingencies = List.of(
                new Contingency("G1", new GeneratorContingency("G1")),
                new Contingency("G2", new GeneratorContingency("G2")),
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")),
                new Contingency("BBS1", new BusbarSectionContingency("BBS1")),
                new Contingency("BBS2", new BusbarSectionContingency("BBS2")),
                new Contingency("BBS3", new BusbarSectionContingency("BBS3")));
        SecurityAnalysisResult expected = run(network, contingencies, false);
        SecurityAnalysisResult actual = run(network, contingencies, true);
        for (Contingency contingency : contingencies) {
            assertSameFlows(expected, actual, contingency.getId());
        }
    }
}
