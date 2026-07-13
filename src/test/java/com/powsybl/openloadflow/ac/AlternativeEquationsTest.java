/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreator;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemUpdater;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.equations.AlternativeEquation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the alternative BUS_TARGET_Q / BUS_TARGET_V equation modeling for local generator voltage control
 * ({@link OpenLoadFlowParameters#setAlternativeEquations(boolean)}): PV/PQ switching preserves the Jacobian
 * matrix structure, and load flow results are identical to the legacy modeling.
 *
 * <p>Same network as {@code AcloadFlowReactiveLimitsTest}: GEN2 reaches its reactive limit and its bus switches from
 * PV to PQ during the outer loops.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationsTest {

    private Network network;
    private Generator gen;
    private Generator gen2;
    private TwoWindingsTransformer nhv2Nload;
    private TwoWindingsTransformer ngen2Nhv1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    private void createNetwork() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        nhv2Nload = network.getTwoWindingsTransformer("NHV2_NLOAD");
        gen = network.getGenerator("GEN");
        Substation p1 = network.getSubstation("P1");

        // reduce GEN reactive range
        gen.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(280)
                .add();

        // create a new generator GEN2, whose reactive limit will be reached
        VoltageLevel vlgen2 = p1.newVoltageLevel()
                .setId("VLGEN2")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlgen2.getBusBreakerView().newBus()
                .setId("NGEN2")
                .add();
        gen2 = vlgen2.newGenerator()
                .setId("GEN2")
                .setBus("NGEN2")
                .setConnectableBus("NGEN2")
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(100)
                .add();
        gen2.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(100)
                .add();
        int zb380 = 380 * 380 / 100;
        ngen2Nhv1 = p1.newTwoWindingsTransformer()
                .setId("NGEN2_NHV1")
                .setBus1("NGEN2")
                .setConnectableBus1("NGEN2")
                .setRatedU1(24.0)
                .setBus2("NHV1")
                .setConnectableBus2("NHV1")
                .setRatedU2(400.0)
                .setR(0.24 / 1800 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1800 * zb380)
                .add();

        // fix active power balance
        network.getLoad("LOAD").setP0(699.838);
    }

    @BeforeEach
    void setUp() {
        createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAlternativeEquations(true);
    }

    @Test
    void equationSystemStructureTest() {
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
        var creationParameters = new AcEquationSystemCreationParameters(false, true);
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(lfNetwork, creationParameters).create();

        // buses with local generator voltage control are modeled with a alternative equation, in PV mode after
        // creation; the separate voltage target equation still exists but stays inactive forever
        LfBus genBus = lfNetwork.getBusById("VLGEN_0");
        var q = equationSystem.getEquation(genBus.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        assertInstanceOf(AlternativeEquation.class, q);
        assertEquals(AcEquationType.BUS_TARGET_V, q.getActiveType());
        assertFalse(equationSystem.getEquation(genBus.getNum(), AcEquationType.BUS_TARGET_V).orElseThrow().isActive());

        // buses without generator voltage control stay on the legacy modeling
        LfBus loadBus = lfNetwork.getBusById("VLLOAD_0");
        // buses without generator voltage control also get alternative power balance equations (with a trivial
        // alternative for disabling/islanding) but no voltage control alternative
        var loadBusQ = equationSystem.getEquation(loadBus.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        assertInstanceOf(AlternativeEquation.class, loadBusQ);
        assertEquals(AcEquationType.BUS_TARGET_Q, loadBusQ.getActiveType());
        assertFalse(((AlternativeEquation<AcVariableType, AcEquationType>) loadBusQ).hasAlternative(AcEquationType.BUS_TARGET_V));
        assertTrue(equationSystem.getEquation(loadBus.getNum(), AcEquationType.BUS_TARGET_V).isPresent());

        // the reactive power balance stays evaluable while the bus is in PV mode
        assertNotNull(genBus.getQ());

        // PV -> PQ switching preserves the matrix and its structure
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
            Matrix m1 = j.getMatrix();
            double[] b = new double[equationSystem.getIndex().getColumnCount()];
            j.solve(b); // factorize a first time

            genBus.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
            AcEquationSystemCreator.updateGeneratorVoltageControl(genBus.getGeneratorVoltageControl().orElseThrow(), equationSystem);
            assertEquals(AcEquationType.BUS_TARGET_Q, q.getActiveType());
            assertSame(m1, j.getMatrix());
            j.solve(new double[equationSystem.getIndex().getColumnCount()]); // LU updated, not rebuilt

            genBus.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(true);
            AcEquationSystemCreator.updateGeneratorVoltageControl(genBus.getGeneratorVoltageControl().orElseThrow(), equationSystem);
            assertEquals(AcEquationType.BUS_TARGET_V, q.getActiveType());
            assertSame(m1, j.getMatrix());
        }
    }

    @Test
    void loadFlowWithReactiveLimitsTest() {
        // same reference values as AcloadFlowReactiveLimitsTest.test() with the legacy modeling
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-164.315, gen.getTerminal());
        assertReactivePowerEquals(-100, gen2.getTerminal()); // GEN2 is correctly limited to 100 MVar
        assertReactivePowerEquals(100, ngen2Nhv1.getTerminal1());
        assertReactivePowerEquals(-200, nhv2Nload.getTerminal2());
    }

    @Test
    void loadFlowWithoutReactiveLimitsTest() {
        // same reference values as AcloadFlowReactiveLimitsTest.test() with the legacy modeling
        parameters.setUseReactiveLimits(false);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-109.228, gen.getTerminal());
        assertReactivePowerEquals(-152.265, gen2.getTerminal());
        assertReactivePowerEquals(-199.998, nhv2Nload.getTerminal2());
    }

    @Test
    void sameResultsAsLegacyModelingTest() {
        // run with the legacy modeling
        parametersExt.setAlternativeEquations(false);
        LoadFlowResult legacyResult = loadFlowRunner.run(network, parameters);
        assertTrue(legacyResult.isFullyConverged());
        Map<String, Double> legacyVoltages = new HashMap<>();
        for (Bus bus : network.getBusView().getBuses()) {
            legacyVoltages.put(bus.getId(), bus.getV());
        }

        // run with the alternative equation modeling on a fresh network
        createNetwork();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAlternativeEquations(true);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(legacyResult.getComponentResults().get(0).getIterationCount(),
                result.getComponentResults().get(0).getIterationCount());
        for (Bus bus : network.getBusView().getBuses()) {
            // tolerance accounts for the different column ordering (and so LU pivoting) between the two modelings
            assertEquals(legacyVoltages.get(bus.getId()), bus.getV(), 1e-6, "voltage mismatch at bus " + bus.getId());
        }
    }

    private void compareWithLegacyModeling(Network testNetwork, double voltageTolerance) {
        LoadFlowParameters lfParameters = new LoadFlowParameters(); // reactive limits enabled by default
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.create(lfParameters)
                .setAlternativeEquations(false);

        LoadFlowResult legacyResult = loadFlowRunner.run(testNetwork, lfParameters);
        assertTrue(legacyResult.isFullyConverged());
        Map<String, Double> legacyVoltages = new HashMap<>();
        for (Bus bus : testNetwork.getBusView().getBuses()) {
            legacyVoltages.put(bus.getId(), bus.getV());
        }

        // ensure the alternative modeling actually applies to some buses of this network
        LfNetwork lfNetwork = Networks.load(testNetwork, new FirstSlackBusSelector()).get(0);
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(lfNetwork,
                new AcEquationSystemCreationParameters(false, true)).create();
        long alternativeEquationCount = lfNetwork.getBuses().stream()
                .map(bus -> equationSystem.getEquation(bus.getNum(), AcEquationType.BUS_TARGET_Q).orElse(null))
                .filter(AlternativeEquation.class::isInstance)
                .count();
        assertTrue(alternativeEquationCount > 0, "no bus eligible to alternative voltage equations");

        lfParametersExt.setAlternativeEquations(true);
        LoadFlowResult result = loadFlowRunner.run(testNetwork, lfParameters);
        assertTrue(result.isFullyConverged());
        assertEquals(legacyResult.getComponentResults().get(0).getIterationCount(),
                result.getComponentResults().get(0).getIterationCount());
        for (Bus bus : testNetwork.getBusView().getBuses()) {
            assertEquals(legacyVoltages.get(bus.getId()), bus.getV(), voltageTolerance, "voltage mismatch at bus " + bus.getId());
        }
    }

    @Test
    void ieee118SameResultsAsLegacyModelingTest() {
        // many generators with reactive limits: several PV -> PQ switches happen during the outer loops
        compareWithLegacyModeling(IeeeCdfNetworkFactory.create118(), 1e-6);
    }

    @Test
    void ieee300SameResultsAsLegacyModelingTest() {
        compareWithLegacyModeling(IeeeCdfNetworkFactory.create300(), 1e-6);
    }

    @Test
    void remoteControlEquationStructureTest() {
        // g1, g2, g3 at b1, b2, b3 remotely controlling b4 through transformers
        Network remoteNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        LfNetwork lfNetwork = Networks.load(remoteNetwork, new FirstSlackBusSelector()).get(0);
        var creationParameters = new AcEquationSystemCreationParameters(false, true);
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(lfNetwork, creationParameters).create();

        LfBus b1 = lfNetwork.getBusById("vl1_0");
        LfBus b2 = lfNetwork.getBusById("vl2_0");
        LfBus b3 = lfNetwork.getBusById("vl3_0");
        LfBus b4 = lfNetwork.getBusById("vl4_0");

        // each controller bus has a alternative equation with 3 alternatives: its reactive power balance, the
        // voltage target at the controlled bus and its reactive power distribution (the trivial disabled-bus
        // alternative is only created for security/sensitivity analysis)
        AlternativeEquation<AcVariableType, AcEquationType> q1 = (AlternativeEquation<AcVariableType, AcEquationType>)
                equationSystem.getEquation(b1.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        AlternativeEquation<AcVariableType, AcEquationType> q2 = (AlternativeEquation<AcVariableType, AcEquationType>)
                equationSystem.getEquation(b2.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        AlternativeEquation<AcVariableType, AcEquationType> q3 = (AlternativeEquation<AcVariableType, AcEquationType>)
                equationSystem.getEquation(b3.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        assertEquals(3, q1.getAlternativeCount());

        // the controlled bus keeps its reactive power balance (no voltage control alternative) and its voltage
        // target equation stays inactive: the voltage target is carried by the first enabled controller, referring
        // to the controlled bus element
        AlternativeEquation<AcVariableType, AcEquationType> q4 = (AlternativeEquation<AcVariableType, AcEquationType>)
                equationSystem.getEquation(b4.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        assertFalse(q4.hasAlternative(AcEquationType.BUS_TARGET_V));
        assertEquals(AcEquationType.BUS_TARGET_Q, q4.getActiveType());
        assertFalse(equationSystem.getEquation(b4.getNum(), AcEquationType.BUS_TARGET_V).orElseThrow().isActive());
        assertEquals(AcEquationType.BUS_TARGET_V, q1.getActiveType());
        assertEquals(b4.getNum(), q1.getActiveElementNum());
        assertEquals(AcEquationType.DISTR_Q, q2.getActiveType());
        assertEquals(AcEquationType.DISTR_Q, q3.getActiveType());

        // a controller switching to PQ mode moves the voltage target carrier without structural change
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
            Matrix m1 = j.getMatrix();
            j.solve(new double[equationSystem.getIndex().getColumnCount()]); // factorize a first time

            b1.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
            AcEquationSystemCreator.updateGeneratorVoltageControl(b1.getGeneratorVoltageControl().orElseThrow().getMainVoltageControl(), equationSystem);
            assertEquals(AcEquationType.BUS_TARGET_Q, q1.getActiveType());
            assertEquals(AcEquationType.BUS_TARGET_V, q2.getActiveType()); // b2 becomes the voltage target carrier
            assertEquals(AcEquationType.DISTR_Q, q3.getActiveType());
            assertSame(m1, j.getMatrix());
            j.solve(new double[equationSystem.getIndex().getColumnCount()]); // LU updated, not rebuilt

            // all controllers in PQ mode: no voltage target anymore
            b2.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
            b3.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(false);
            AcEquationSystemCreator.updateGeneratorVoltageControl(b2.getGeneratorVoltageControl().orElseThrow().getMainVoltageControl(), equationSystem);
            assertEquals(AcEquationType.BUS_TARGET_Q, q2.getActiveType());
            assertEquals(AcEquationType.BUS_TARGET_Q, q3.getActiveType());
            assertSame(m1, j.getMatrix());
            j.solve(new double[equationSystem.getIndex().getColumnCount()]);

            // and back to PV mode for all
            b1.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(true);
            b2.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(true);
            b3.setGeneratorVoltageControlEnabledAndRecomputeTargetQ(true);
            AcEquationSystemCreator.updateGeneratorVoltageControl(b1.getGeneratorVoltageControl().orElseThrow().getMainVoltageControl(), equationSystem);
            assertEquals(AcEquationType.BUS_TARGET_V, q1.getActiveType());
            assertEquals(AcEquationType.DISTR_Q, q2.getActiveType());
            assertEquals(AcEquationType.DISTR_Q, q3.getActiveType());
            assertSame(m1, j.getMatrix());
        }
    }

    @Test
    void remoteControlSameResultsAsLegacyModelingTest() {
        // same reference values as GeneratorRemoteControlTest.testWith3Generators() with the legacy modeling
        Network remoteNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        LoadFlowParameters lfParameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true)
                .setAlternativeEquations(true);
        LoadFlowResult result = loadFlowRunner.run(remoteNetwork, lfParameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(21.506559, remoteNetwork.getBusBreakerView().getBus("b1"));
        assertVoltageEquals(21.293879, remoteNetwork.getBusBreakerView().getBus("b2"));
        assertVoltageEquals(22.641227, remoteNetwork.getBusBreakerView().getBus("b3"));
        assertVoltageEquals(413.4, remoteNetwork.getBusBreakerView().getBus("b4"));
        assertReactivePowerEquals(-69.925, remoteNetwork.getGenerator("g1").getTerminal());
        assertReactivePowerEquals(-69.925, remoteNetwork.getGenerator("g2").getTerminal());
        assertReactivePowerEquals(-69.925, remoteNetwork.getGenerator("g3").getTerminal());
    }

    @Test
    void remoteControlWithReactiveLimitsSameResultsAsLegacyModelingTest() {
        // g1 reactive limit forces a controller PV -> PQ switch during the outer loops
        Network remoteNetwork = VoltageControlNetworkFactory.createWithGeneratorRemoteControl();
        remoteNetwork.getGenerator("g1").newMinMaxReactiveLimits()
                .setMinQ(-50)
                .setMaxQ(50)
                .add();
        LoadFlowParameters lfParameters = new LoadFlowParameters().setUseReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setVoltageRemoteControl(true)
                .setAlternativeEquations(false);

        LoadFlowResult legacyResult = loadFlowRunner.run(remoteNetwork, lfParameters);
        assertTrue(legacyResult.isFullyConverged());
        // the limit has been reached and g1 switched to PQ mode
        assertReactivePowerEquals(-50, remoteNetwork.getGenerator("g1").getTerminal());
        Map<String, Double> legacyVoltages = new HashMap<>();
        for (Bus bus : remoteNetwork.getBusView().getBuses()) {
            legacyVoltages.put(bus.getId(), bus.getV());
        }

        lfParametersExt.setAlternativeEquations(true);
        LoadFlowResult result = loadFlowRunner.run(remoteNetwork, lfParameters);
        assertTrue(result.isFullyConverged());
        assertReactivePowerEquals(-50, remoteNetwork.getGenerator("g1").getTerminal());
        assertEquals(legacyResult.getComponentResults().get(0).getIterationCount(),
                result.getComponentResults().get(0).getIterationCount());
        for (Bus bus : remoteNetwork.getBusView().getBuses()) {
            assertEquals(legacyVoltages.get(bus.getId()), bus.getV(), 1e-6, "voltage mismatch at bus " + bus.getId());
        }
    }

    @Test
    void partialJacobianValueUpdateExactnessTest() {
        // topology events at a constant state (the post-contingency first solve pattern) take the partial value
        // update path: restore the pinned snapshot and re-derive only the touched columns; the patched matrix must
        // be bit-identical to a full computation
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
        var creationParameters = new AcEquationSystemCreationParameters(false, true)
                .setAlternativeIslandableBusIds(Set.of("VLGEN2_0"));
        var equationSystem = new com.powsybl.openloadflow.ac.equations.vector.AcVectorizedEquationSystemCreator(lfNetwork, creationParameters).create();
        lfNetwork.addListener(new AcEquationSystemUpdater(equationSystem, creationParameters));
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        LfBus gen2Bus = lfNetwork.getBusById("VLGEN2_0");
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<AcVariableType, AcEquationType>(equationSystem, new SparseMatrixFactory())
                .setPartialValueUpdateEnabled(true)) { // enabled by AcLoadFlowContext when alternative equations are used
            j.getMatrix(); // structural build
            equationSystem.getStateVector().set(equationSystem.getStateVector().get()); // state event
            j.getMatrix(); // full value update, snapshot pinned at the current state
            assertEquals(0, j.getPartialValueUpdateCount());

            // islanding-like topology change at the very same state
            lfNetwork.getBranchById("NGEN2_NHV1").setDisabled(true);
            gen2Bus.setDisabled(true);
            var m = (com.powsybl.math.matrix.SparseMatrix) j.getMatrix();
            assertEquals(1, j.getPartialValueUpdateCount());
            int nonZeroCount = m.getColumnStart()[m.getColumnCount()];
            double[] partialValues = java.util.Arrays.copyOf(m.getValues(), nonZeroCount);
            try (JacobianMatrix<AcVariableType, AcEquationType> jRef = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
                var mRef = (com.powsybl.math.matrix.SparseMatrix) jRef.getMatrix();
                assertArrayEquals(java.util.Arrays.copyOf(mRef.getValues(), nonZeroCount), partialValues);
            }

            // restore and island again: the pin moves with each patch, the touched set stays bounded
            gen2Bus.setDisabled(false);
            lfNetwork.getBranchById("NGEN2_NHV1").setDisabled(false);
            m = (com.powsybl.math.matrix.SparseMatrix) j.getMatrix();
            assertEquals(2, j.getPartialValueUpdateCount());
            partialValues = java.util.Arrays.copyOf(m.getValues(), nonZeroCount);
            try (JacobianMatrix<AcVariableType, AcEquationType> jRef = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
                var mRef = (com.powsybl.math.matrix.SparseMatrix) jRef.getMatrix();
                assertArrayEquals(java.util.Arrays.copyOf(mRef.getValues(), nonZeroCount), partialValues);
            }
        }
    }

    @Test
    void busDisablingPreservesMatrixStructureTest() {
        // disabling / re-enabling a bus modeled with alternative equations switches its power balance equations
        // to their trivial alternatives (phi and v targets), keeping the equations active and the matrix structure
        // and symbolic factorization unchanged
        LfNetwork lfNetwork = Networks.load(network, new FirstSlackBusSelector()).get(0);
        var creationParameters = new AcEquationSystemCreationParameters(false, true)
                .setAlternativeIslandableBusIds(Set.of("VLGEN2_0"));
        EquationSystem<AcVariableType, AcEquationType> equationSystem = new AcEquationSystemCreator(lfNetwork, creationParameters).create();
        LfBus gen2Bus = lfNetwork.getBusById("VLGEN2_0");
        var p = equationSystem.getEquation(gen2Bus.getNum(), AcEquationType.BUS_TARGET_P).orElseThrow();
        var q = equationSystem.getEquation(gen2Bus.getNum(), AcEquationType.BUS_TARGET_Q).orElseThrow();
        assertInstanceOf(AlternativeEquation.class, q);
        assertTrue(q.isActive());
        assertEquals(AcEquationType.BUS_TARGET_V, q.getActiveType()); // PV mode

        lfNetwork.addListener(new AcEquationSystemUpdater(equationSystem, creationParameters));
        AcSolverUtil.initStateVector(lfNetwork, equationSystem, new UniformValueVoltageInitializer());
        try (JacobianMatrix<AcVariableType, AcEquationType> j = new JacobianMatrix<>(equationSystem, new SparseMatrixFactory())) {
            Matrix m1 = j.getMatrix();
            j.solve(new double[equationSystem.getIndex().getColumnCount()]); // factorize a first time

            // disabling the bus and its branch (like an islanding contingency detected by the connectivity analysis)
            // keeps both equations active on their trivial alternatives
            lfNetwork.getBranchById("NGEN2_NHV1").setDisabled(true);
            gen2Bus.setDisabled(true);
            assertTrue(p.isActive());
            assertTrue(q.isActive());
            assertEquals(AcEquationType.BUS_TARGET_PHI, p.getActiveType());
            assertEquals(AcEquationType.BUS_TARGET_V_DISABLED, q.getActiveType());
            assertSame(m1, j.getMatrix());
            j.solve(new double[equationSystem.getIndex().getColumnCount()]); // LU updated, not rebuilt

            // re-enabling restores the previous alternatives (PV mode for the generator bus)
            gen2Bus.setDisabled(false);
            lfNetwork.getBranchById("NGEN2_NHV1").setDisabled(false);
            assertEquals(AcEquationType.BUS_TARGET_P, p.getActiveType());
            assertEquals(AcEquationType.BUS_TARGET_V, q.getActiveType());
            assertSame(m1, j.getMatrix());
            j.solve(new double[equationSystem.getIndex().getColumnCount()]);
        }
    }
}
