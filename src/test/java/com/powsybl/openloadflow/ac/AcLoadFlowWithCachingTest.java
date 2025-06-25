/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.NetworkCache;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcLoadFlowWithCachingTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setNetworkCacheEnabled(true);
        NetworkCache.INSTANCE.clear();
    }

    @Test
    void testTargetV() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");
        var ngen = network.getBusBreakerView().getBus("NGEN");
        var nload = network.getBusBreakerView().getBus("NLOAD");

        assertEquals(0, NetworkCache.INSTANCE.getEntryCount());
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.5, ngen);
        assertVoltageEquals(147.578, nload);

        gen.setTargetV(24.1);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.1, ngen);
        assertVoltageEquals(144.402, nload);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(0, result.getComponentResults().get(0).getIterationCount());
    }

    @Test
    void testGeneratorTargetP() {
        var network = DistributedSlackNetworkFactory.create();
        var g1 = network.getGenerator("g1");
        var g2 = network.getGenerator("g2");
        var g3 = network.getGenerator("g3");
        var g4 = network.getGenerator("g4");
        // align active power control of the 3 generators to have understandable results
        g1.setMaxP(300);
        g2.setMaxP(300);
        g3.setMaxP(300);
        g4.setMaxP(300);
        g1.getExtension(ActivePowerControl.class).setDroop(1);
        g2.getExtension(ActivePowerControl.class).setDroop(1);
        g3.getExtension(ActivePowerControl.class).setDroop(1);
        g4.getExtension(ActivePowerControl.class).setDroop(1);

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        // mismatch 120 -> + 30 each
        assertActivePowerEquals(-130.0, g1.getTerminal()); // 100 -> 130
        assertActivePowerEquals(-230.0, g2.getTerminal()); // 200 -> 230
        assertActivePowerEquals(-120.0, g3.getTerminal()); // 90 -> 120
        assertActivePowerEquals(-120.0, g4.getTerminal()); // 90 -> 120

        g1.setTargetP(120); // 100 -> 120
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        // mismatch 100 -> + 25 each
        assertActivePowerEquals(-145.0, g1.getTerminal()); // 120 -> 125
        assertActivePowerEquals(-225.0, g2.getTerminal()); // 220 -> 225
        assertActivePowerEquals(-115.0, g3.getTerminal()); // 90 -> 115
        assertActivePowerEquals(-115.0, g4.getTerminal()); // 90 -> 115

        // check that if target_p > map_p the generator is discarded from active power control
        g1.setTargetP(310);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        // mismatch 90 -> + 60 each
        assertActivePowerEquals(-310.0, g1.getTerminal()); // unchanged
        assertActivePowerEquals(-170.0, g2.getTerminal()); // 200 -> 170
        assertActivePowerEquals(-60.0, g3.getTerminal()); // 90 -> 60
        assertActivePowerEquals(-60.0, g4.getTerminal()); // 90 -> 60
    }

    @Test
    void testBatteryTargetP() {
        var network = DistributedSlackNetworkFactory.createWithBattery();
        var b1 = network.getBattery("bat1");
        var b2 = network.getBattery("bat2");

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(-2.0, b1.getTerminal());
        assertActivePowerEquals(2.983, b2.getTerminal());

        b1.setTargetP(4);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(-4.0, b1.getTerminal());
        assertActivePowerEquals(3.016, b2.getTerminal());
    }

    @Test
    void testParameterChange() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        assertEquals(0, NetworkCache.INSTANCE.getEntryCount());
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        NetworkCache.Entry entry = NetworkCache.INSTANCE.findEntry(network).orElseThrow();
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        NetworkCache.Entry entry2 = NetworkCache.INSTANCE.findEntry(network).orElseThrow();
        assertSame(entry, entry2); // reuse same cache

        // run with different parameters
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.INSTANCE.getEntryCount());
        NetworkCache.Entry entry3 = NetworkCache.INSTANCE.findEntry(network).orElseThrow();
        assertNotSame(entry, entry3); // cache has been evicted and recreated
    }

    @Test
    @Disabled("Disabled by default because not reliable, depends on JVM, garbage collector, and machine performance")
    void testCacheEvictionBusBreaker() {
        int runCount = 10;
        for (int i = 0; i < runCount; i++) {
            var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
            loadFlowRunner.run(network, parameters);
            System.gc();
        }
        assertTrue(NetworkCache.INSTANCE.getEntryCount() < runCount);
    }

    @Test
    @Disabled("Disabled by default because not reliable, depends on JVM, garbage collector, and machine performance")
    void testCacheEvictionNodeBreaker() {
        int runCount = 10;
        parametersExt.setActionableSwitchesIds(Set.of("S1VL1_LD1_BREAKER"));
        for (int i = 0; i < runCount; i++) {
            var network = FourSubstationsNodeBreakerFactory.create();
            loadFlowRunner.run(network, parameters);
            System.gc();
        }
        assertTrue(NetworkCache.INSTANCE.getEntryCount() < runCount);
    }

    @Test
    void testEntryEviction() {
        var network = FourSubstationsNodeBreakerFactory.create();
        assertEquals(1, network.getVariantManager().getVariantIds().size());

        parametersExt.setActionableSwitchesIds(Set.of("S1VL1_LD1_BREAKER"));
        loadFlowRunner.run(network, parameters);
        assertEquals(2, network.getVariantManager().getVariantIds().size());

        parametersExt.setActionableSwitchesIds(Set.of("S1VL1_TWT_BREAKER"));
        loadFlowRunner.run(network, parameters);
        assertEquals(2, network.getVariantManager().getVariantIds().size());
    }

    @Test
    void testUnsupportedAttributeChange() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        gen.setTargetQ(10);
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testPropertiesChange() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        gen.setProperty("foo", "bar");
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        gen.setProperty("foo", "baz");
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        gen.removeProperty("foo");
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testVariantChange() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v");
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v", true);
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getVariantManager().removeVariant("v");
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testLoadAddition() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getVoltageLevel("VLLOAD").newLoad()
                .setId("NEWLOAD")
                .setConnectableBus("NLOAD")
                .setBus("NLOAD")
                .setP0(10)
                .setQ0(10)
                .add();

        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testLoadRemoval() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getLoad("LOAD").remove();

        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testShunt() {
        var network = ShuntNetworkFactory.create();
        var shunt = network.getShuntCompensator("SHUNT");

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(0, shunt.getTerminal());

        shunt.setSectionCount(1);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // cache has not been invalidated but updated

        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
    }

    @Test
    void testShunt2() {
        var network = ShuntNetworkFactory.create();
        var shunt = network.getShuntCompensator("SHUNT");

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters); // Run a first LF before changing a parameter.
        parameters.setShuntCompensatorVoltageControlOn(true);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
        assertEquals(1, shunt.getSolvedSectionCount());
        assertEquals(0, shunt.getSectionCount());

        shunt.setSolvedSectionCount(0);
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // cache has been invalidated
    }

    @Test
    void testShunt3() {
        var network = ShuntNetworkFactory.createWithTwoShuntCompensators();
        var shunt = network.getShuntCompensator("SHUNT"); // with voltage control capabilities.

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters);
        parameters.setShuntCompensatorVoltageControlOn(true);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
        assertEquals(1, shunt.getSolvedSectionCount());
        assertEquals(0, shunt.getSectionCount());

        shunt.setSolvedSectionCount(1);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testSwitchOpen() {
        var network = NodeBreakerNetworkFactory.create();
        var l1 = network.getLine("L1");
        var l2 = network.getLine("L2");

        parametersExt.setActionableSwitchesIds(Set.of("C"));

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(301.884, l1.getTerminal1());
        assertActivePowerEquals(301.884, l2.getTerminal1());

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getSwitch("C").setOpen(true);

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(0.0993, l1.getTerminal1());
        assertActivePowerEquals(607.682, l2.getTerminal1());
    }

    @Test
    void testSwitchClose() {
        var network = NodeBreakerNetworkFactory.create();
        var l1 = network.getLine("L1");
        var l2 = network.getLine("L2");

        parametersExt.setActionableSwitchesIds(Set.of("C"));

        var c = network.getSwitch("C");
        c.setOpen(true);

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(0.0994, l1.getTerminal1());
        assertActivePowerEquals(607.681, l2.getTerminal1());

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        network.getSwitch("C").setOpen(false);

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(301.884, l1.getTerminal1());
        assertActivePowerEquals(301.884, l2.getTerminal1());
    }

    @Test
    void testInvalidNetwork() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var result = loadFlowRunner.run(network, parameters);

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        var gen = network.getGenerator("GEN");
        gen.setTargetV(1000);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.NO_CALCULATION, result.getComponentResults().get(0).getStatus());
    }

    @Test
    @Disabled("To support later")
    void testInitiallyInvalidNetwork() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");
        gen.setTargetV(1000);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(0).getStatus());

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        gen.setTargetV(24);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testSwitchIssueWithInit() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var vlload = network.getVoltageLevel("VLLOAD");
        vlload.getBusBreakerView().newBus()
                .setId("NLOAD2")
                .add();
        var br = vlload.getBusBreakerView().newSwitch()
                .setId("BR")
                .setBus1("NLOAD")
                .setBus2("NLOAD2")
                .setOpen(true)
                .add();
        vlload.newLoad()
                .setId("LOAD2")
                .setBus("NLOAD2")
                .setP0(5)
                .setQ0(5)
                .add();

        parametersExt.setActionableSwitchesIds(Set.of("BR"));

        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());

        br.setOpen(false);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());

        br.setOpen(true);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
    }

    private static void checkVoltageIsDefinedForAllBuses(Network network) {
        for (Bus bus : network.getBusView().getBuses()) {
            assertFalse(Double.isNaN(bus.getV()));
            assertFalse(Double.isNaN(bus.getAngle()));
        }
    }

    @Test
    void testUpdateNetworkFix() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        checkVoltageIsDefinedForAllBuses(network);
    }

    @Test
    void testUpdateWithMultipleSynchronousComponents() {
        Network network = HvdcNetworkFactory.createVsc();
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        checkVoltageIsDefinedForAllBuses(network);
        var g1 = network.getGenerator("g1");
        g1.setTargetV(g1.getTargetV() + 0.1);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        checkVoltageIsDefinedForAllBuses(network);
    }

    @Test
    void fixCacheInvalidationWhenUpdatingTapPosition() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        var t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        parameters.setTransformerVoltageControlOn(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testSecondaryVoltageControl() {
        parametersExt.setSecondaryVoltageControl(true);
        var network = IeeeCdfNetworkFactory.create14();
        network.newExtension(SecondaryVoltageControlAdder.class)
                .newControlZone()
                    .withName("z1")
                    .newPilotPoint()
                        .withTargetV(12.7)
                        .withBusbarSectionsOrBusesIds(List.of("B10"))
                    .add()
                    .newControlUnit()
                        .withId("B6-G")
                        .add()
                    .newControlUnit()
                        .withId("B8-G")
                        .add()
                    .add()
                .add();
        SecondaryVoltageControl control = network.getExtension(SecondaryVoltageControl.class);
        ControlZone z1 = control.getControlZone("z1").orElseThrow();
        PilotPoint pilotPoint = z1.getPilotPoint();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        var b10 = network.getBusBreakerView().getBus("B10");
        assertVoltageEquals(12.7, b10);
        assertReactivePowerEquals(-17.826, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-17.827, network.getGenerator("B8-G").getTerminal());

        // update pilot point target voltage
        pilotPoint.setTargetV(12.5);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.5, b10);
        assertReactivePowerEquals(-11.832, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-11.832, network.getGenerator("B8-G").getTerminal());

        ControlUnit b6g = z1.getControlUnit("B6-G").orElseThrow();
        b6g.setParticipate(false);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
        // there is no re-run of secondary voltage control outer loop, this is expected as pilot point has already reached
        // its target voltage and remaining control unit is necessarily aligned.
        assertVoltageEquals(12.5, b10);
        assertReactivePowerEquals(-11.832, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-11.832, network.getGenerator("B8-G").getTerminal());

        pilotPoint.setTargetV(12.7);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.613, b10); // we cannot reach back to 12.7 Kv with only one control unit
        assertReactivePowerEquals(-6.771, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-24.0, network.getGenerator("B8-G").getTerminal());

        // get b6 generator back
        b6g.setParticipate(true);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.7, b10); // we can reach now 12.7 Kv with the 2 control units
        assertReactivePowerEquals(-17.822, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-17.83, network.getGenerator("B8-G").getTerminal());
    }

    @Test
    void testTransfo2VoltageTargetChange() {
        var network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        var twt = network.getTwoWindingsTransformer("T2wT");

        parameters.setTransformerVoltageControlOn(true);
        twt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(twt.getTerminal2())
                .setTargetV(30.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(1, twt.getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getRatioTapChanger().getTapPosition());

        twt.getRatioTapChanger().setTargetV(32);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(2, twt.getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getRatioTapChanger().getTapPosition());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
    }

    @Test
    void testTransfo3VoltageTargetChange() {
        var network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        var twt = network.getThreeWindingsTransformer("T3wT");

        twt.getLeg2().getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(twt.getLeg2().getTerminal())
                .setTargetV(30);

        parameters.setTransformerVoltageControlOn(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(1, twt.getLeg2().getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getLeg2().getRatioTapChanger().getTapPosition());

        twt.getLeg2().getRatioTapChanger().setTargetV(26);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(2, twt.getLeg2().getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getLeg2().getRatioTapChanger().getTapPosition());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
    }

    @Test
    void testTransfo2TapPositionChange() {
        var network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        var twt = network.getTwoWindingsTransformer("T2wT");
        assertNull(twt.getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getRatioTapChanger().getTapPosition());

        parametersExt.setActionableTransformersIds(Set.of("T2wT"));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        twt.getRatioTapChanger().setTapPosition(1);

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testTransfo3TapPositionChange() {
        var network = VoltageControlNetworkFactory.createNetworkWithT3wt();
        var twt = network.getThreeWindingsTransformer("T3wT");
        assertNull(twt.getLeg2().getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getLeg2().getRatioTapChanger().getTapPosition());

        parametersExt.setActionableTransformersIds(Set.of("T3wT"));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());

        twt.getLeg2().getRatioTapChanger().setTapPosition(1);

        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
    }

    @Test
    void testCacheInvalidationIssueWhenChangeNotInSameComponent() {
        var network = EurostagTutorialExample1Factory.create();
        var gen = network.getGenerator("GEN");
        var vlgen = network.getVoltageLevel("VLGEN");
        vlgen.getBusBreakerView().newBus()
                .setId("NEW_BUS")
                .add();
        var newGen = vlgen.newGenerator()
                .setId("NEW_GEN")
                .setBus("NEW_BUS")
                .setTargetP(10)
                .setVoltageRegulatorOn(true)
                .setTargetV(24)
                .setMinP(0)
                .setMaxP(1000)
                .add();
        assertTrue(NetworkCache.INSTANCE.findEntry(network).isEmpty());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts());
        gen.setTargetV(gen.getTargetV() + 0.1);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
        newGen.setTargetV(newGen.getTargetV() + 0.1);
        assertNotNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // check cache has not been invalidated
    }
}
