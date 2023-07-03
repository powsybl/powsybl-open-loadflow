/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.NetworkCache;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.network.ShuntNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
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
        assertEquals(1, shunt.getSectionCount());

        shunt.setSectionCount(0);
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
        assertEquals(1, shunt.getSectionCount());

        shunt.setSectionCount(1);
        assertNull(NetworkCache.INSTANCE.findEntry(network).orElseThrow().getContexts()); // cache has been invalidated
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
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(0).getStatus());
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
}
