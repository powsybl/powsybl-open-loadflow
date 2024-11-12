/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
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

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NonImpedantBranchDisablingTest {

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    @Test
    void test() {
        Network network = NodeBreakerNetworkFactory.create();
        network.getSwitch("B3").setRetained(false);
        network.getSwitch("C").setRetained(true);
        AcLoadFlowParameters parameters = OpenLoadFlowParameters.createAcParameters(new LoadFlowParameters(),
                                                                                    new OpenLoadFlowParameters(),
                                                                                    new DenseMatrixFactory(),
                                                                                    new EvenShiloachGraphDecrementalConnectivityFactory<>(),
                                                                                    true,
                                                                                    false);
        parameters.setAcSolverParameters(new KnitroSolverParameters());
        parameters.getNetworkParameters().setSlackBusSelector(new NameSlackBusSelector("VL1_1"));
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), parameters.getNetworkParameters()).get(0);

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, parameters)) {
            var engine = new AcloadFlowEngine(context, new KnitroSolverFactory());
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
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters).setAcSolverType(KnitroSolverFactory.NAME);
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getLine("L2").setR(0.0).setX(0.0);
        network.getLine("L1").getTerminal1().disconnect();
        loadFlowRunner.run(network, parameters);
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
                .setSlackBusesIds(List.of("VL2_0"))
                .setAcSolverType(KnitroSolverFactory.NAME);

        loadFlowRunner.run(network, parameters);

        assertActivePowerEquals(401.757, network.getLine("L1").getTerminal1());
        assertActivePowerEquals(100.878, network.getLine("L2").getTerminal1());
        assertActivePowerEquals(100.878, network.getLine("L3").getTerminal1());

        AcLoadFlowParameters acLoadFlowParameters
                = OpenLoadFlowParameters.createAcParameters(network,
                                                            parameters, olfParameters,
                                                            new DenseMatrixFactory(),
                                                            new NaiveGraphConnectivityFactory<>(LfElement::getNum),
                                                            true,
                                                            false);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(c1);
        try (LfNetworkList lfNetworks = Networks.load(network, acLoadFlowParameters.getNetworkParameters(), topoConfig, ReportNode.NO_OP)) {
            LfNetwork largestNetwork = lfNetworks.getLargest().orElseThrow();
            largestNetwork.getBranchById("C1").setDisabled(true);
            try (AcLoadFlowContext context = new AcLoadFlowContext(largestNetwork, acLoadFlowParameters)) {
                new AcloadFlowEngine(context, new KnitroSolverFactory()).run();
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
        OpenLoadFlowParameters.create(parameters).setAcSolverType(KnitroSolverFactory.NAME);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal1().getP()));
        assertTrue(Double.isNaN(network.getLine("L2").getTerminal2().getP()));
    }
}
