/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ServiceParameterResolver;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.stream.Stream;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class NonImpedantBranchDisablingTest {

    private final CommonTestConfig commonTestConfig;

    NonImpedantBranchDisablingTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
    }

    @Test
    void test() {
        Network network = NodeBreakerNetworkFactory.create();
        network.getSwitch("B3").setRetained(false);
        network.getSwitch("C").setRetained(true);
        AcLoadFlowParameters parameters = OpenLoadFlowParameters.createAcParameters(new LoadFlowParameters(),
                                                                                    new OpenLoadFlowParameters(),
                                                                                    commonTestConfig.matrixFactory(),
                                                                                    new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                                                                                    true,
                                                                                    false);
        parameters.getNetworkParameters().setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters.getNetworkParameters()).get(0);

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, parameters)) {
            var engine = new AcloadFlowEngine(context);
            engine.run();
            assertEquals(8, context.getEquationSystem().getIndex().getSortedVariablesToFind().size());
            var l1 = lfNetwork.getBranchById("L1");
            var l2 = lfNetwork.getBranchById("L2");
            assertEquals(3.01884, l1.getP1().eval(), 10e-5);
            assertEquals(3.01884, l2.getP1().eval(), 10e-5);

            lfNetwork.getBranchById("C").setDisabled(true);

            engine.run();
            assertEquals(8, context.getEquationSystem().getIndex().getSortedVariablesToFind().size()); // we have kept same variables!!!
            assertEquals(0, l1.getP1().eval(), 10e-5);
            assertEquals(6.07782, l2.getP1().eval(), 10e-5);
        }
    }

    @Test
    void testOpenBranch() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getLine("L2").setR(0.0).setX(0.0);
        network.getLine("L1").getTerminal1().disconnect();
        loadFlowRunner.run(network);
        assertEquals(600.018, network.getLine("L2").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-600.018, network.getLine("L2").getTerminal2().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, network.getLine("L1").getTerminal1().getP(), 0);

        network.getLine("L1").getTerminal1().connect();
        network.getLine("L1").getTerminal2().disconnect();
        loadFlowRunner.run(network);
        assertEquals(600.0, network.getLine("L2").getTerminal1().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-600.0, network.getLine("L2").getTerminal2().getP(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, network.getLine("L1").getTerminal2().getP(), 0);
    }

    @Test
    void testDisabledNonImpedantBranch() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        Switch c1 = network.getSwitch("C1");
        c1.setOpen(true);

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters olfParameters = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusesIds(List.of("VL2_0"));

        loadFlowRunner.run(network, parameters);

        assertActivePowerEquals(401.757, network.getLine("L1").getTerminal1());
        assertActivePowerEquals(100.878, network.getLine("L2").getTerminal1());
        assertActivePowerEquals(100.878, network.getLine("L3").getTerminal1());

        AcLoadFlowParameters acLoadFlowParameters
                = OpenLoadFlowParameters.createAcParameters(network,
                                                            parameters, olfParameters,
                                                            commonTestConfig.matrixFactory(),
                                                            new NaiveGraphConnectivityFactory<>(LfElement::getNum),
                                                            true,
                                                            false);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(c1);
        try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig, acLoadFlowParameters.getNetworkParameters(), ReportNode.NO_OP)) {
            LfNetwork largestNetwork = lfNetworks.getLargest().orElseThrow();
            largestNetwork.getBranchById("C1").setDisabled(true);
            try (AcLoadFlowContext context = new AcLoadFlowContext(largestNetwork, acLoadFlowParameters)) {
                new AcloadFlowEngine(context).run();
            }
            // should be the same as with previous LF
            assertEquals(401.757, largestNetwork.getBranchById("L1").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
            assertEquals(100.878, largestNetwork.getBranchById("L2").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
            assertEquals(100.878, largestNetwork.getBranchById("L3").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testOpenAtOneSideZeroImpedanceBranch() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getLine("L2").setR(0.0).setX(0.0);
        network.getLine("L2").getTerminal2().disconnect();
        LoadFlowResult result = loadFlowRunner.run(network);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal1().getP()));
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal2().getP()));

        LoadFlowParameters parameters = new LoadFlowParameters()
                .setDc(true);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal1().getP()));
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal2().getP()));
    }

    private static Network retainSwitches(Network network, String... switchIds) {
        for (String switchId : switchIds) {
            network.getSwitch(switchId).setRetained(true);
        }
        return network;
    }

    private AcLoadFlowParameters createAcParameters(Network network, LoadFlowParameters loadFlowParameters, OpenLoadFlowParameters parametersExt) {
        return OpenLoadFlowParameters.createAcParameters(network, loadFlowParameters, parametersExt,
                                                         commonTestConfig.matrixFactory(),
                                                         new NaiveGraphConnectivityFactory<>(LfBus::getNum),
                                                         true, false);
    }

    private static LfBranch getSpanningTreeCoupler(LfNetwork lfNetwork) {
        return Stream.of("CPL12_BR", "CPL13_BR")
                .map(lfNetwork::getBranchById)
                .filter(branch -> branch.isSpanningTreeEdge(LoadFlowModel.AC))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Check that the line flows of the LF network are the same as the ones of a load flow run on the network with
     * the given switch open.
     */
    private static void checkSameLineFlowsAsLoadFlowWithOpenSwitch(Network network, AcLoadFlowParameters parameters, LfNetwork lfNetwork,
                                                                  String switchId, List<String> lineIds) {
        network.getSwitch(switchId).setOpen(true);
        LfNetwork lfNetworkWithOpenSwitch = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetworkWithOpenSwitch, parameters)) {
            assertEquals(AcSolverStatus.CONVERGED, new AcloadFlowEngine(context).run().getSolverStatus());
            for (String lineId : lineIds) {
                LfBranch expectedLine = lfNetworkWithOpenSwitch.getBranchById(lineId);
                LfBranch line = lfNetwork.getBranchById(lineId);
                assertEquals(expectedLine.getP1().eval() * PerUnit.SB, line.getP1().eval() * PerUnit.SB, 1e-2);
                assertEquals(expectedLine.getQ1().eval() * PerUnit.SB, line.getQ1().eval() * PerUnit.SB, 1e-2);
            }
        }
        network.getSwitch(switchId).setOpen(false);
    }

    @Test
    void testDisabledSpanningTreeNonImpedantBranchWithRemoteControllerOfDependentVoltageControl() {
        // G1 at BBS1 remotely controls the voltage of BBS4 which is coupled to BBS5 whose voltage is locally controlled
        // at a higher target by G4: the voltage control of G1 is a merged dependent one, and the reactive power
        // distribution equations of the main one (G4) include BBS1 reactive terms, built with the dummy reactive power
        // variables of the spanning tree non impedant branches. They have to be recreated when the spanning tree of
        // the zero impedance network of BBS1 changes, even if no controlled bus of the main voltage control is part of
        // this zero impedance network.
        // CPL12_BR and CPL13_BR are retained and are 2 parallel non impedant branches between BBS1 and BBS2+BBS3
        Network network = retainSwitches(NodeBreakerNetworkFactory.create3BarsWithCouplerLoop(), "CPL12_BR", "CPL13_BR");
        network.getGenerator("G2").removeVoltageRegulation();
        network.getGenerator("G3").removeVoltageRegulation();
        network.getGenerator("G1").newVoltageRegulation()
                .withMode(RegulationMode.VOLTAGE)
                .withTargetValue(400.0)
                .withTerminal(network.getLoad("LD").getTerminal())
                .withRegulating(true)
                .build();
        VoltageLevel vl2 = network.getVoltageLevel("VL2");
        vl2.getNodeBreakerView().newBusbarSection().setId("BBS5").setNode(4).add();
        vl2.getNodeBreakerView().newBreaker().setId("CPL45_BR").setNode1(0).setNode2(4).setRetained(true).add();
        vl2.getNodeBreakerView().newBreaker().setId("G4_BR").setNode1(4).setNode2(5).add();
        vl2.newGenerator()
                .setId("G4")
                .setNode(5)
                .setMinP(0.0)
                .setMaxP(500.0)
                .setLocalTargetV(401.0)
                .newVoltageRegulation()
                    .withMode(RegulationMode.VOLTAGE)
                    .withRegulating(true)
                    .add()
                .setTargetP(0.0)
                .setLocalTargetQ(0.0)
                .add();
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(loadFlowParameters)
                .setSlackBusPMaxMismatch(1e-3); // the slack bus might be different in the load flow with the switch open
        AcLoadFlowParameters parameters = createAcParameters(network, loadFlowParameters, parametersExt);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters.getNetworkParameters()).get(0);
        LfBus g1Bus = lfNetwork.getGeneratorById("G1").getBus();
        GeneratorVoltageControl g1VoltageControl = g1Bus.getGeneratorVoltageControl().orElseThrow();
        assertFalse(g1VoltageControl.isLocalControl());

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, parameters)) {
            var engine = new AcloadFlowEngine(context);
            assertEquals(AcSolverStatus.CONVERGED, engine.run().getSolverStatus());
            assertEquals(VoltageControl.MergeStatus.DEPENDENT, g1VoltageControl.getMergeStatus());
            assertSame(lfNetwork.getGeneratorById("G4").getBus(), g1VoltageControl.getMainVoltageControl().getControlledBus());
            var equationSystem = context.getEquationSystem();
            var qEq = equationSystem.getEquation(g1Bus.getNum(), AcEquationType.DISTR_Q).orElseThrow();

            LfBranch coupler = getSpanningTreeCoupler(lfNetwork);
            coupler.setDisabled(true);
            assertNotSame(qEq, equationSystem.getEquation(g1Bus.getNum(), AcEquationType.DISTR_Q).orElseThrow());

            assertEquals(AcSolverStatus.CONVERGED, engine.run().getSolverStatus());
            checkSameLineFlowsAsLoadFlowWithOpenSwitch(network, parameters, lfNetwork, coupler.getId(), List.of("L1", "L2"));
        }
    }
}
