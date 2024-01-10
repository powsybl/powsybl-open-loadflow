/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.test.AbstractSerDeTest;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.action.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
class LfActionTest extends AbstractSerDeTest {

    @Override
    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        super.tearDown();
    }

    @Test
    void test() {
        Network network = NodeBreakerNetworkFactory.create();
        SwitchAction switchAction = new SwitchAction("switchAction", "C", true);
        var matrixFactory = new DenseMatrixFactory();
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                loadFlowParameters, new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToOpen().add(network.getSwitch("C"));
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), topoConfig, Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(switchAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            String loadId = "LOAD";
            Contingency contingency = new Contingency(loadId, new LoadContingency("LD"));
            PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
                    .setHvdcAcEmulation(false);
            PropagatedContingency propagatedContingency = PropagatedContingency.createList(network,
                    Collections.singletonList(contingency), new LfTopoConfig(), creationParameters).get(0);
            propagatedContingency.toLfContingency(lfNetwork).ifPresent(lfContingency -> {
                LfAction.apply(List.of(lfAction), lfNetwork, lfContingency, acParameters.getNetworkParameters());
                assertTrue(lfNetwork.getBranchById("C").isDisabled());
                assertEquals("C", lfAction.getDisabledBranch().getId());
                assertNull(lfAction.getEnabledBranch());
            });

            assertTrue(LfAction.create(new SwitchAction("switchAction", "S", true), lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).isEmpty());
            assertTrue(LfAction.create(new LineConnectionAction("A line action", "x", true), lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).isEmpty());
            assertTrue(LfAction.create(new PhaseTapChangerTapPositionAction("A phase tap change action", "y", false, 3), lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).isEmpty());
            var lineAction = new LineConnectionAction("A line action", "L1", true, false);
            assertEquals("Line connection action: only open line at both sides is supported yet.", assertThrows(UnsupportedOperationException.class, () -> LfAction.create(lineAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers())).getMessage());
        }
    }

    @Test
    void testUnsupportedGeneratorAction() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        GeneratorAction generatorAction = new GeneratorActionBuilder()
                .withId("genAction" + genId)
                .withGeneratorId(genId)
                .withTargetQ(100) // to be done soon
                .build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), new LfTopoConfig(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("Generator action: configuration not supported yet.", e.getMessage());
        }
    }

    @Test
    void testGeneratorActionWithRelativeActivePowerValue() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator generator = network.getGenerator(genId);
        final double deltaTargetP = 2d;
        final double oldTargetP = generator.getTargetP();
        final double newTargetP = oldTargetP + deltaTargetP;
        GeneratorAction generatorAction = new GeneratorActionBuilder()
                .withId("genAction_" + genId)
                .withGeneratorId(genId)
                .withActivePowerRelativeValue(true)
                .withActivePowerValue(deltaTargetP)
                .build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), new LfTopoConfig(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            lfAction.apply(acParameters.getNetworkParameters());
            assertEquals(newTargetP / PerUnit.SB, lfNetwork.getGeneratorById(genId).getTargetP());
            assertEquals(genId, generatorAction.getGeneratorId());
            assertEquals(oldTargetP, network.getGenerator(genId).getTargetP());
        }
    }

    @Test
    void testHvdcAction() {
        // the hvc line is operated in AC emulation before applying the action. A change in P0 and droop is not supported yet.
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();
        HvdcAction hvdcAction = new HvdcActionBuilder()
                .withId("action")
                .withHvdcId("hvdc34")
                .withAcEmulationEnabled(true)
                .withP0(200.0)
                .withDroop(90.0)
                .build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), new LfTopoConfig(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(hvdcAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("Hvdc action: line is already in AC emulation, not supported yet.", e.getMessage());
        }
    }

    @Test
    void testHvdcAction2() {
        // the hvc line is in active power setpoint mode before applying the action. Not supported yet.
        Network network = HvdcNetworkFactory.createVsc();
        HvdcAction hvdcAction = new HvdcActionBuilder()
                .withId("action")
                .withHvdcId("hvdc23")
                .withAcEmulationEnabled(true)
                .withP0(200.0)
                .withDroop(90.0)
                .build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), new LfTopoConfig(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            assertTrue(LfAction.create(hvdcAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).isEmpty());
        }
    }
}
