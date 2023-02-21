/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.action.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfActionTest extends AbstractConverterTest {

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

    static class UnknownAction extends AbstractAction {

        static final String NAME = "unknown-action";

        @JsonCreator
        protected UnknownAction(@JsonProperty("id") String id) {
            super(id);
        }

        @JsonProperty(value = "type", access = JsonProperty.Access.READ_ONLY)
        @Override
        public String getType() {
            return NAME;
        }
    }

    @Test
    void test() {
        Network network = NodeBreakerNetworkFactory.create();
        SwitchAction switchAction = new SwitchAction("switchAction", "C", true);
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(switchAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            String loadId = "LOAD";
            Contingency contingency = new Contingency(loadId, new LoadContingency("LD"));
            PropagatedContingency propagatedContingency = PropagatedContingency.createList(network,
                    Collections.singletonList(contingency), new HashSet<>(), new HashSet<>(), true, false, false, false).get(0);
            propagatedContingency.toLfContingency(lfNetwork).ifPresent(lfContingency -> {
                LfAction.apply(List.of(lfAction), lfNetwork, lfContingency, LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
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
    void testLfActionGeneratorUpdatesUpdateP() {
        // This test validates the creation of a GeneratorUpdate updating the target P with an absolute value.
        //
        // This is not yet supported, whence the Exception raised. It should be updated eventually.
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator actedOnGenerator = network.getGenerator(genId);
        double deltaTargetP = 2d;
        double newTargetP = actedOnGenerator.getTargetP() + deltaTargetP;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withVoltageRegulatorOn(false).withActivePowerRelativeValue(false).withActivePowerValue(newTargetP).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("LfAction.GeneratorUpdates: setTargetP with an absolute power value is not supported yet.", e.getMessage());
        }

    }

    @Test
    void testLfActionGeneratorUpdatesUpdatePRelativeValue() {

        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator actedOnGenerator = network.getGenerator(genId);
        final double deltaTargetP = 2d;
        final double oldTargetP = actedOnGenerator.getTargetP();
        final double newTargetP = oldTargetP + deltaTargetP;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction_" + genId).withGeneratorId(genId).withActivePowerRelativeValue(true).withActivePowerValue(deltaTargetP).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            lfAction.apply(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

            assertEquals(newTargetP / PerUnit.SB, lfNetwork.getGeneratorById(genId).getTargetP());
            assertEquals(genId, generatorAction.getGeneratorId());
            // Since no load flow was run, there is no reason for the actual targetP to have changed in the original network.
            assertEquals(oldTargetP, network.getGenerator(genId).getTargetP());
        }

    }

    @Test
    void testLfActionGeneratorUpdatesEmptyAction() {

        Network network = NodeBreakerNetworkFactory.create();
        String genId = "no-G";
        double newTargetQ = 2d;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withVoltageRegulatorOn(false).withActivePowerRelativeValue(false).withActivePowerValue(newTargetQ).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            assertFalse(LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).isPresent());
        }

    }

    @Test
    void testUnknownLfAction() {
        UnknownAction action = new UnknownAction("UselessAction");

        Network network = NodeBreakerNetworkFactory.create();

        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(action, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("Unsupported action type: " + action.getType(), e.getMessage());

        }
    }

    @Test
    void testEmptyGeneratorAction() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";

        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            lfAction.apply(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

            assertEquals(genId, generatorAction.getGeneratorId());
        }
    }

    @Test
    void testGeneratorUpdateSetTargetQ() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator actedOnGenerator = network.getGenerator(genId);
        double deltaTargetQ = 2d;
        double newTargetQ = actedOnGenerator.getTargetQ() + deltaTargetQ;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withTargetQ(newTargetQ).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("LfAction:GeneratorUpdate: Unsupported generator update: update target Q", e.getMessage());
        }
    }

    @Test
    void testGeneratorUpdateSetTargetV() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator actedOnGenerator = network.getGenerator(genId);
        double deltaTargetV = 2d;
        double newTargetV = actedOnGenerator.getTargetV() + deltaTargetV;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withTargetV(newTargetV).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("LfAction:GeneratorUpdate: Unsupported generator update: update target V", e.getMessage());

        }
    }

    @Test
    void testGeneratorUpdateSetVoltageControl() {
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withVoltageRegulatorOn(true).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()));
            assertEquals("LfAction:GeneratorUpdate: Unsupported generator update: update voltage regulation", e.getMessage());

        }
    }

    static class LfActionAcRunner extends LoadFlow.Runner {

        private LoadFlowParameters parameters;

        public LfActionAcRunner() {
            super(new OpenLoadFlowProvider(new DenseMatrixFactory()));
            parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                    .setDistributedSlack(false);
            OpenLoadFlowParameters.create(parameters).setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        }

        public LoadFlowParameters getParameters() {
            return parameters;
        }
    }

    @Test
    void testGeneratorUpdatesTargetPProportionalToPMax() {
        LfActionAcRunner runner = new LfActionAcRunner();
        Network network = NodeBreakerNetworkFactory.create();
        String genId = "G";
        Generator actedOnGenerator = network.getGenerator(genId);
        double deltaTargetP = 2d;
        double newTargetP = actedOnGenerator.getTargetP() + deltaTargetP;
        GeneratorAction generatorAction =
                new GeneratorActionBuilder().withId("genAction" + genId).withGeneratorId(genId).withActivePowerRelativeValue(true).withActivePowerValue(deltaTargetP).build();
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {

            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(generatorAction, lfNetwork, network, acParameters.getNetworkParameters().isBreakers()).orElseThrow();
            lfAction.apply(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

            assertEquals(newTargetP / PerUnit.SB, lfNetwork.getGeneratorById(genId).getTargetP());
            assertEquals(genId, generatorAction.getGeneratorId());

            LoadFlowResult result = runner.run(network, runner.getParameters());
            assertTrue(result.isOk());
        }

    }

}
