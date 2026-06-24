/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.ac.solver.NewtonRaphsonStoppingCriteriaType;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@ExtendWith(ServiceParameterResolver.class)
class LoadFlowWithCachingTest {

    private final CommonTestConfig commonTestConfig;

    LoadFlowWithCachingTest(CommonTestConfig commonTestConfig) {
        this.commonTestConfig = commonTestConfig;
    }

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private final BiFunction<Network, Boolean, NetworkCache.Entry> findEntryFunction = (n, isDc) -> isDc ? NetworkCache.DC_LF_INSTANCE.findEntry(n).orElseThrow() : NetworkCache.AC_LF_INSTANCE.findEntry(n).orElseThrow();

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(commonTestConfig.matrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setNetworkCacheEnabled(true);
        NetworkCache.AC_LF_INSTANCE.clear();
        NetworkCache.DC_LF_INSTANCE.clear();
    }

    @Test
    void testTargetV() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");
        var ngen = network.getBusBreakerView().getBus("NGEN");
        var nload = network.getBusBreakerView().getBus("NLOAD");

        assertEquals(0, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.5, ngen);
        assertVoltageEquals(147.578, nload);

        gen.setTargetV(24.1);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(24.1, ngen);
        assertVoltageEquals(144.402, nload);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(0, result.getComponentResults().get(0).getIterationCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testGeneratorTargetP(boolean isDc) {
        parameters.setDc(isDc);
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
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        // mismatch 120 -> + 30 each
        assertActivePowerEquals(-130.0, g1.getTerminal()); // 100 -> 130
        assertActivePowerEquals(-230.0, g2.getTerminal()); // 200 -> 230
        assertActivePowerEquals(-120.0, g3.getTerminal()); // 90 -> 120
        assertActivePowerEquals(-120.0, g4.getTerminal()); // 90 -> 120

        g1.setTargetP(120); // 100 -> 120
        assertNotNull(findEntryFunction.apply(network, isDc).getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        // mismatch 100 -> + 25 each
        assertActivePowerEquals(-145.0, g1.getTerminal()); // 120 -> 125
        assertActivePowerEquals(-225.0, g2.getTerminal()); // 220 -> 225
        assertActivePowerEquals(-115.0, g3.getTerminal()); // 90 -> 115
        assertActivePowerEquals(-115.0, g4.getTerminal()); // 90 -> 115

        // check that if target_p > map_p the generator is discarded from active power control
        g1.setTargetP(310);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        // mismatch 90 -> + 60 each
        assertActivePowerEquals(-310.0, g1.getTerminal()); // unchanged
        assertActivePowerEquals(-170.0, g2.getTerminal()); // 200 -> 170
        assertActivePowerEquals(-60.0, g3.getTerminal()); // 90 -> 60
        assertActivePowerEquals(-60.0, g4.getTerminal()); // 90 -> 60
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testBatteryTargetP(boolean isDc) {
        parameters.setDc(isDc);
        var network = DistributedSlackNetworkFactory.createWithBattery();
        var b1 = network.getBattery("bat1");
        var b2 = network.getBattery("bat2");

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(-2.0, b1.getTerminal());
        assertActivePowerEquals(2.983, b2.getTerminal());

        b1.setTargetP(4);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(-4.0, b1.getTerminal());
        assertActivePowerEquals(3.016, b2.getTerminal());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testLoadP(boolean isDc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        Generator gen = network.getGenerator("GEN");
        parameters.setDc(isDc);

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(600, load.getTerminal());
        assertActivePowerEquals(isDc ? -600 : -605.559, gen.getTerminal());

        load.setP0(620);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(620, load.getTerminal());
        assertActivePowerEquals(isDc ? -620 : -625.895, gen.getTerminal());

        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        load.setQ0(20);
        assertNull(findEntryFunction.apply(network, isDc).getValues()); // cache is invalidated because unsupported update
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testUnsupportedLoadUpdate(boolean isDc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Load load = network.getLoad("LOAD");
        parameters.setDc(isDc)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        load.setP0(620);
        assertNull(findEntryFunction.apply(network, isDc).getValues()); // cache is invalidated because of PROPORTIONAL_TO_LOAD mode

        load.newExtension(LoadDetailAdder.class)
                .withVariableActivePower(40)
                .withFixedActivePower(20)
                .add();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        load.setP0(35);
        assertNull(findEntryFunction.apply(network, isDc).getValues()); // cache is invalidated because of Load detail
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testLccActivePowerSetpoint(boolean isDc) {
        parameters.setDc(isDc);
        parametersExt.setMaxActivePowerMismatch(0.001) // finer tolerance because network cache can lead to slightly different active power distribution
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        Network network = HvdcNetworkFactory.createLcc();
        HvdcLine hvdcLine = network.getHvdcLine("hvdc23");
        Line line = network.getLine("l12");
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(50.0, hvdcLine.getConverterStation1().getTerminal());
        assertActivePowerEquals(-49.399, hvdcLine.getConverterStation2().getTerminal());
        assertActivePowerEquals(-100, line.getTerminal2());

        hvdcLine.setActivePowerSetpoint(30);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(30.0, hvdcLine.getConverterStation1().getTerminal());
        assertActivePowerEquals(-29.639, hvdcLine.getConverterStation2().getTerminal());
        assertActivePowerEquals(50, network.getLoad("ld2").getTerminal());
        assertActivePowerEquals(isDc ? -80 : -80.049, network.getGenerator("g1").getTerminal());
        assertReactivePowerEquals(isDc ? 0 : -32.647, network.getGenerator("g1").getTerminal());

        // test unsupported update
        hvdcLine.setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER);
        assertNull(findEntryFunction.apply(network, isDc).getValues());
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVscActivePowerSetpoint(boolean isDc) {
        parameters.setDc(isDc);
        parametersExt.setMaxActivePowerMismatch(0.001) // finer tolerance because network cache can lead to slightly different active power distribution
                .setNewtonRaphsonStoppingCriteriaType(NewtonRaphsonStoppingCriteriaType.PER_EQUATION_TYPE_CRITERIA);
        Network network = HvdcNetworkFactory.createVsc(true);
        network.getGenerator("g3").setMaxP(20);
        HvdcLine hvdcLine = network.getHvdcLine("hvdc23");
        Line line = network.getLine("l12");
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(50.0, hvdcLine.getConverterStation1().getTerminal());
        assertActivePowerEquals(-49.349, hvdcLine.getConverterStation2().getTerminal());
        assertActivePowerEquals(-100, line.getTerminal2());

        hvdcLine.setActivePowerSetpoint(40);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(40.0, hvdcLine.getConverterStation1().getTerminal());
        assertActivePowerEquals(-39.479, hvdcLine.getConverterStation2().getTerminal());
        assertActivePowerEquals(50, network.getLoad("ld2").getTerminal());
        assertActivePowerEquals(isDc ? -90 : -92.578, network.getGenerator("g1").getTerminal());

        // test unsupported update
        hvdcLine.setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER);
        assertNull(findEntryFunction.apply(network, isDc).getValues());
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testUnsupportedACEmulationUpdate() {
        Network network = HvdcNetworkFactory.createVsc(true);
        network.getGenerator("g3").setMaxP(20);
        HvdcLine hvdcLine = network.getHvdcLine("hvdc23");
        hvdcLine.newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());

        hvdcLine.setActivePowerSetpoint(40);
        assertNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // Network cache invalidated because AC emulation

        parameters.setHvdcAcEmulation(false);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());

        hvdcLine.setActivePowerSetpoint(50);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // Network cache is used because AC emulation has been disabled
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(2, result.getComponentResults().get(0).getIterationCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testBoundaryLineP0(boolean isDc) {
        parameters.setDc(isDc);
        Network network = BoundaryFactory.create();
        BoundaryLine boundaryLine = network.getBoundaryLine("bl1");
        Line line = network.getLine("l1");
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(isDc ? 101 : 101.303, boundaryLine.getTerminal());
        assertActivePowerEquals(isDc ? -101 : -101.150, line.getTerminal2());

        boundaryLine.setP0(90);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 2, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(isDc ? 90 : 90.293, boundaryLine.getTerminal());
        assertActivePowerEquals(isDc ? -90 : -90.306, line.getTerminal2());

        // test unsupported update
        boundaryLine.setQ0(0);
        assertNull(findEntryFunction.apply(network, isDc).getValues());
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testBoundaryLinePaired() {
        Network network = BoundaryFactory.createWithTieLine();
        BoundaryLine boundaryLine = network.getBoundaryLine("h1");
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
        boundaryLine.setP0(90); // unsupported change because boundary line is paired
        assertNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
    }

    @Test
    void testParameterChange() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        assertEquals(0, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        NetworkCache.Entry entry = NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow();
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        NetworkCache.Entry entry2 = NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow();
        assertSame(entry, entry2); // reuse same cache

        // run with different parameters
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        loadFlowRunner.run(network, parameters);
        assertEquals(1, NetworkCache.AC_LF_INSTANCE.getEntryCount());
        NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry3 = NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow();
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
        assertTrue(NetworkCache.AC_LF_INSTANCE.getEntryCount() < runCount);
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
        assertTrue(NetworkCache.AC_LF_INSTANCE.getEntryCount() < runCount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testEntryEviction(boolean isDc) {
        parameters.setDc(isDc);
        var network = FourSubstationsNodeBreakerFactory.create();
        assertEquals(1, network.getVariantManager().getVariantIds().size());

        parametersExt.setActionableSwitchesIds(Set.of("S1VL1_LD1_BREAKER"));
        loadFlowRunner.run(network, parameters);
        assertEquals(2, network.getVariantManager().getVariantIds().size());

        parametersExt.setActionableSwitchesIds(Set.of("S1VL1_TWT_BREAKER"));
        loadFlowRunner.run(network, parameters);
        assertEquals(2, network.getVariantManager().getVariantIds().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testUnsupportedAttributeChange(boolean isDc) {
        parameters.setDc(isDc);
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        gen.setTargetQ(10);
        assertNull(findEntryFunction.apply(network, isDc).getValues());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testPropertiesChange(boolean isDc) {
        parameters.setDc(isDc);
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        gen.setProperty("foo", "bar");
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        gen.setProperty("foo", "baz");
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
        gen.removeProperty("foo");
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVariantChange(boolean isDc) {
        parameters.setDc(isDc);
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v");
        assertNotNull(findEntryFunction.apply(network, isDc).getValues()); // no reason to invaludate the cache has initial variant has not been changed

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v", true);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        network.getVariantManager().setWorkingVariant("v");
        network.getVariantManager().cloneVariant("v", VariantManagerConstants.INITIAL_VARIANT_ID, true);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        assertNull(findEntryFunction.apply(network, isDc).getValues());

        loadFlowRunner.run(network, parameters);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        network.getVariantManager().removeVariant("v");
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());
    }

    @Test
    void testUpdateOnDifferentVariantDoesNotInvalidateCache() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var gen = network.getGenerator("GEN");

        // create cache entry for INITIAL_VARIANT_ID
        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        // switch to a different variant
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "v");
        network.getVariantManager().setWorkingVariant("v");

        // make an unsupported change in "v" - fires onUpdate with variantId="v"
        gen.setTargetQ(10);

        // switch back to INITIAL_VARIANT_ID: cache should not have been invalidated
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
    }

    @Test
    void testLoadAddition() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        network.getVoltageLevel("VLLOAD").newLoad()
                .setId("NEWLOAD")
                .setConnectableBus("NLOAD")
                .setBus("NLOAD")
                .setP0(10)
                .setQ0(10)
                .add();

        assertNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
    }

    @Test
    void testLoadRemoval() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        network.getLoad("LOAD").remove();

        assertNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
    }

    @Test
    void testShunt() {
        var network = ShuntNetworkFactory.create();
        var shunt = network.getShuntCompensator("SHUNT");

        assertTrue(NetworkCache.AC_LF_INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(0, shunt.getTerminal());

        shunt.setSectionCount(1);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // cache has not been invalidated but updated

        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
    }

    @Test
    void testShunt2() {
        var network = ShuntNetworkFactory.create();
        var shunt = network.getShuntCompensator("SHUNT");

        assertTrue(NetworkCache.AC_LF_INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters); // Run a first LF before changing a parameter.
        parameters.setShuntCompensatorVoltageControlOn(true);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
        assertEquals(1, shunt.getSolvedSectionCount());
        assertEquals(0, shunt.getSectionCount());

        shunt.setSolvedSectionCount(0);
        assertNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // cache has been invalidated
    }

    @Test
    void testShunt3() {
        var network = ShuntNetworkFactory.createWithTwoShuntCompensators();
        var shunt = network.getShuntCompensator("SHUNT"); // with voltage control capabilities.

        assertTrue(NetworkCache.AC_LF_INSTANCE.findEntry(network).isEmpty());
        loadFlowRunner.run(network, parameters);
        parameters.setShuntCompensatorVoltageControlOn(true);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, shunt.getTerminal());
        assertReactivePowerEquals(-152.826, shunt.getTerminal());
        assertEquals(1, shunt.getSolvedSectionCount());
        assertEquals(0, shunt.getSectionCount());

        shunt.setSolvedSectionCount(1);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSwitchOpen(boolean isDc) {
        parameters.setDc(isDc);
        Network network = NodeBreakerNetworkFactory.create();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");

        parametersExt.setActionableSwitchesIds(Set.of("C"));

        assertTrue((isDc ? NetworkCache.DC_LF_INSTANCE : NetworkCache.AC_LF_INSTANCE).findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(isDc ? 300 : 301.884, l1.getTerminal1());
        assertActivePowerEquals(isDc ? 300 : 301.884, l2.getTerminal1());

        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        network.getSwitch("C").setOpen(true);

        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(isDc ? 0 : 0.0993, l1.getTerminal1());
        assertActivePowerEquals(isDc ? 600 : 607.682, l2.getTerminal1());
    }

    @Test
    void testSwitchClose() {
        Network network = NodeBreakerNetworkFactory.create();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");

        parametersExt.setActionableSwitchesIds(Set.of("C"));

        var c = network.getSwitch("C");
        c.setOpen(true);

        assertTrue(NetworkCache.AC_LF_INSTANCE.findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(0.0994, l1.getTerminal1());
        assertActivePowerEquals(607.681, l2.getTerminal1());

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        network.getSwitch("C").setOpen(false);

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(301.884, l1.getTerminal1());
        assertActivePowerEquals(301.884, l2.getTerminal1());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSwitchOpenWithLostElements(boolean isDc) {
        parameters.setDc(isDc);
        Network network = NodeBreakerNetworkFactory.createWith4Bars();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");

        parametersExt.setSlackBusPMaxMismatch(0.0001)
                .setNewtonRaphsonConvEpsPerEq(0.0001)
                .setActionableSwitchesIds(Set.of("C", "B3"));

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 7, result.getComponentResults().get(0).getIterationCount());
        assertActivePowerEquals(isDc ? 394.555 : 396.935, l1.getTerminal1());
        assertActivePowerEquals(isDc ? 394.555 : 396.935, l2.getTerminal1());

        network.getSwitch("C").setOpen(true);
        network.getSwitch("B3").setOpen(true);
        assertNotNull(findEntryFunction.apply(network, isDc).getValues());

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 6, result.getComponentResults().get(0).getIterationCount());
        // Check that lost elements have been correctly reset (to NaN)
        assertActivePowerEquals(Double.NaN, l1.getTerminal1());
        assertVoltageEquals(Double.NaN, l1.getTerminal1().getBusBreakerView().getBus());
        assertAngleEquals(Double.NaN, l1.getTerminal1().getBusBreakerView().getBus());
        assertActivePowerEquals(isDc ? 736.924 : 745.266, l2.getTerminal1());
    }

    @Test
    void testInvalidNetwork() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        loadFlowRunner.run(network, parameters);

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        var gen = network.getGenerator("GEN");
        gen.setTargetV(1000);
        var result = loadFlowRunner.run(network, parameters);
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

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        gen.setTargetV(24);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSwitchIssueWithInit(boolean isDc) {
        parameters.setDc(isDc);
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

        parametersExt.setActionableSwitchesIds(Set.of("BR")).setNetworkCacheEnabled(true);

        assertTrue((isDc ? NetworkCache.DC_LF_INSTANCE : NetworkCache.AC_LF_INSTANCE).findEntry(network).isEmpty());

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 4, result.getComponentResults().get(0).getIterationCount());

        br.setOpen(false);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 3, result.getComponentResults().get(0).getIterationCount());

        br.setOpen(true);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(isDc ? 0 : 1, result.getComponentResults().get(0).getIterationCount());
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.5, b10);
        assertReactivePowerEquals(-11.832, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-11.832, network.getGenerator("B8-G").getTerminal());

        ControlUnit b6g = z1.getControlUnit("B6-G").orElseThrow();
        b6g.setParticipate(false);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(1, result.getComponentResults().get(0).getIterationCount());
        // there is no re-run of secondary voltage control outer loop, this is expected as pilot point has already reached
        // its target voltage and remaining control unit is necessarily aligned.
        assertVoltageEquals(12.5, b10);
        assertReactivePowerEquals(-11.832, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-11.832, network.getGenerator("B8-G").getTerminal());

        pilotPoint.setTargetV(12.7);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.613, b10); // we cannot reach back to 12.7 Kv with only one control unit
        assertReactivePowerEquals(-6.771, network.getGenerator("B6-G").getTerminal());
        assertReactivePowerEquals(-24.0, network.getGenerator("B8-G").getTerminal());

        // get b6 generator back
        b6g.setParticipate(true);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(2, twt.getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getRatioTapChanger().getTapPosition());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(2, twt.getLeg2().getRatioTapChanger().getSolvedTapPosition());
        assertEquals(0, twt.getLeg2().getRatioTapChanger().getTapPosition());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        twt.getRatioTapChanger().setTapPosition(1);

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(4, result.getComponentResults().get(0).getIterationCount());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
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
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());

        twt.getLeg2().getRatioTapChanger().setTapPosition(1);

        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
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
        assertTrue(NetworkCache.AC_LF_INSTANCE.findEntry(network).isEmpty());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues());
        gen.setTargetV(gen.getTargetV() + 0.1);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
        newGen.setTargetV(newGen.getTargetV() + 0.1);
        assertNotNull(NetworkCache.AC_LF_INSTANCE.findEntry(network).orElseThrow().getValues()); // check cache has not been invalidated
    }
}
