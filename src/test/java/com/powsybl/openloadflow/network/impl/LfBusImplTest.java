/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.DELTA_POWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Fabien Rigaux (https://github.com/frigaux)
 */
class LfBusImplTest {
    private Network network;
    private LfNetwork lfNetwork;
    private Bus bus1;
    private Bus bus2;
    private StaticVarCompensator svc1;
    private StaticVarCompensator svc2;
    private StaticVarCompensator svc3;
    private Load load;

    private Network createNetwork() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vl2 = s1.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        svc1 = vl1.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulating(false)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setBmin(-0.006)
                .setBmax(0.006)
                .add();
        svc1.setVoltageSetpoint(385)
                .setRegulating(true)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
        svc2 = vl1.newStaticVarCompensator()
                .setId("svc2")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulating(false)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setBmin(-0.001)
                .setBmax(0.001)
                .add();
        svc2.setVoltageSetpoint(385)
                .setRegulating(true)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.015)
                .add();
        svc3 = vl1.newStaticVarCompensator()
                .setId("svc3")
                .setConnectableBus("b1")
                .setBus("b1")
                .setRegulating(false)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setBmin(-0.00075)
                .setBmax(0.00075)
                .add();
        svc3.setVoltageSetpoint(385)
                .setRegulating(true)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.02)
                .add();
        load = vl2.newLoad()
                .setId("load")
                .setConnectableBus("b2")
                .setBus("b2")
                .setQ0(100)
                .setP0(0)
                .add();
        network.newLine()
                .setId("line")
                .setBus1("b1")
                .setBus2("b2")
                .setR(1)
                .setX(1)
                .add();
        return network;
    }

    @Test
    void updateGeneratorsStateTest() {
        network = createNetwork();

        List<LfNetwork> networks = Networks.load(EurostagTutorialExample1Factory.create(), new MostMeshedSlackBusSelector());
        LfNetwork mainNetwork = networks.get(0);

        LfNetworkParameters parameters = new LfNetworkParameters()
                .setBreakers(true);
        LfBusImpl lfBus = new LfBusImpl(bus1, mainNetwork, 385, 0, parameters, true);
        LfNetworkLoadingReport lfNetworkLoadingReport = new LfNetworkLoadingReport();
        lfBus.addStaticVarCompensator(svc1, parameters, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc2, parameters, lfNetworkLoadingReport);
        lfBus.addStaticVarCompensator(svc3, parameters, lfNetworkLoadingReport);
        double generationQ = -6.412103131789854;
        lfBus.updateGeneratorsState(generationQ, true, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION);
        double sumQ = 0;
        for (LfGenerator lfGenerator : lfBus.getGenerators()) {
            sumQ += lfGenerator.getCalculatedQ();
        }
        assertEquals(generationQ, sumQ, DELTA_POWER, "sum of generators calculatedQ should be equals to qToDispatch");
    }

    private static List<LfGenerator> createLfGeneratorsWithInitQ(List<Double> initQs) {
        return createLfGeneratorsWithInitQ(FourSubstationsNodeBreakerFactory.create(), initQs);
    }

    private static List<LfGenerator> createLfGeneratorsWithInitQ(Network network, List<Double> initQs) {
        LfNetwork lfNetwork = new LfNetwork(0, 0, new FirstSlackBusSelector(), 1, new NaiveGraphConnectivityFactory<>(LfBus::getNum), new ReferenceBusFirstSlackSelector());
        LfNetworkParameters parameters1 = new LfNetworkParameters()
                .setPlausibleActivePowerLimit(100)
                .setMinPlausibleTargetVoltage(0.9)
                .setMaxPlausibleTargetVoltage(1.1);
        LfNetworkLoadingReport lfNetworkLoadingReport = new LfNetworkLoadingReport();
        LfGenerator lfGenerator1 = LfGeneratorImpl.create(network.getGenerator("GH1"), lfNetwork, parameters1, lfNetworkLoadingReport);
        lfGenerator1.setCalculatedQ(initQs.get(0));
        LfNetworkParameters parameters23 = new LfNetworkParameters()
                .setPlausibleActivePowerLimit(200)
                .setMinPlausibleTargetVoltage(0.9)
                .setMaxPlausibleTargetVoltage(1.1);
        LfGenerator lfGenerator2 = LfGeneratorImpl.create(network.getGenerator("GH2"), lfNetwork, parameters23, lfNetworkLoadingReport);
        lfGenerator2.setCalculatedQ(initQs.get(1));
        LfGenerator lfGenerator3 = LfGeneratorImpl.create(network.getGenerator("GH3"), lfNetwork, parameters23, lfNetworkLoadingReport);
        lfGenerator3.setCalculatedQ(initQs.get(2));
        List<LfGenerator> generators = new ArrayList<>();
        generators.add(lfGenerator1);
        generators.add(lfGenerator2);
        generators.add(lfGenerator3);
        return generators;
    }

    @Test
    void dispatchQForMaxTest() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        // GH1 reactive limits are not plausible => fallback into split Q equally
        network.getGenerator("GH1").newMinMaxReactiveLimits().setMinQ(-10000).setMaxQ(10000).add();
        List<LfGenerator> generators = createLfGeneratorsWithInitQ(network, List.of(0d, 0d, 0d));
        LfGenerator generatorToRemove = generators.get(1);
        double qToDispatch = 21;
        double residueQ = AbstractLfBus.dispatchQ(generators, true, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, qToDispatch);
        double totalCalculatedQ = generators.get(0).getCalculatedQ() + generators.get(1).getCalculatedQ() + generatorToRemove.getCalculatedQ();
        assertEquals(7.0, generators.get(0).getCalculatedQ());
        assertEquals(7.0, generators.get(1).getCalculatedQ());
        assertEquals(2, generators.size());
        assertEquals(qToDispatch - totalCalculatedQ, residueQ, 0.00001);
        assertEquals(generatorToRemove.getMaxQ(), generatorToRemove.getCalculatedQ());
    }

    @Test
    void dispatchQTestWithInitialQForMax() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        // GH1 reactive limits are not plausible => fallback into split Q equally
        network.getGenerator("GH1").newMinMaxReactiveLimits().setMinQ(-10000).setMaxQ(10000).add();
        List<LfGenerator> generators = createLfGeneratorsWithInitQ(network, List.of(1.5d, 1d, 3d));
        double qInitial = generators.get(0).getCalculatedQ() + generators.get(1).getCalculatedQ() + generators.get(2).getCalculatedQ();
        LfGenerator generatorToRemove1 = generators.get(1);
        LfGenerator generatorToRemove2 = generators.get(2);
        double qToDispatch = 20;
        double residueQ = AbstractLfBus.dispatchQ(generators, true, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, qToDispatch);
        assertEquals(1, generators.size());
        double totalCalculatedQ = generators.get(0).getCalculatedQ() + generatorToRemove1.getCalculatedQ() + generatorToRemove2.getCalculatedQ();
        assertEquals(qToDispatch + qInitial - totalCalculatedQ, residueQ, 0.0001);
        assertEquals(8.17, generators.get(0).getCalculatedQ(), 0.01);
        assertEquals(generatorToRemove1.getMaxQ(), generatorToRemove1.getCalculatedQ(), 0.01);
        assertEquals(generatorToRemove2.getMaxQ(), generatorToRemove2.getCalculatedQ(), 0.01);
    }

    @Test
    void dispatchQForMinTest() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        // GH1 reactive limits are not plausible => fallback into split Q equally
        network.getGenerator("GH1").newMinMaxReactiveLimits().setMinQ(-10000).setMaxQ(10000).add();
        List<LfGenerator> generators = createLfGeneratorsWithInitQ(network, List.of(0d, 0d, 0d));
        LfGenerator generatorToRemove2 = generators.get(1);
        LfGenerator generatorToRemove3 = generators.get(2);
        double qToDispatch = -21;
        double residueQ = AbstractLfBus.dispatchQ(generators, true, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, qToDispatch);
        double totalCalculatedQ = generators.get(0).getCalculatedQ() + generatorToRemove2.getCalculatedQ() + generatorToRemove3.getCalculatedQ();
        assertEquals(-7.0, generators.get(0).getCalculatedQ());
        assertEquals(1, generators.size());
        assertEquals(qToDispatch - totalCalculatedQ, residueQ, 0.00001);
        assertEquals(generatorToRemove2.getMinQ(), generatorToRemove2.getCalculatedQ());
        assertEquals(generatorToRemove3.getMinQ(), generatorToRemove3.getCalculatedQ());
    }

    @Test
    void dispatchQEmptyListTest() {
        List<LfGenerator> generators = new ArrayList<>();
        double qToDispatch = -21;
        Assertions.assertThrows(IllegalArgumentException.class, () -> AbstractLfBus.dispatchQ(generators, true, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, qToDispatch),
                "the generator list to dispatch Q can not be empty");
    }

    @Test
    void dispatchQwithKequalProportion() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<LfGenerator> generators = createLfGeneratorsWithInitQ(network, List.of(0.3d, 0.1d, 0.4d));
        LfGenerator g0 = generators.get(0);
        LfGenerator g1 = generators.get(1);
        LfGenerator g2 = generators.get(2);
        g0.setCalculatedQ(0);
        g1.setCalculatedQ(0);
        g2.setCalculatedQ(0);
        double qToDispatch = 20;
        double residueQ = AbstractLfBus.dispatchQ(new ArrayList<>(generators), true, ReactivePowerDispatchMode.K_EQUAL_PROPORTION, qToDispatch);
        assertEquals(0, residueQ, 0);
        assertEquals(8.537, g0.getCalculatedQ(), 1e-3);
        assertEquals(4.985, g1.getCalculatedQ(), 1e-3);
        assertEquals(6.477, g2.getCalculatedQ(), 1e-3);
        assertEquals(0.91, LfGenerator.qToK(g0, g0.getCalculatedQ()), 1e-3);
        assertEquals(0.91, LfGenerator.qToK(g1, g1.getCalculatedQ()), 1e-3);
        assertEquals(0.91, LfGenerator.qToK(g2, g2.getCalculatedQ()), 1e-3);
    }

    @Test
    void dispatchQwithKequalProportionWithFallBack() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        network.getGenerator("GH2").newMinMaxReactiveLimits().setMinQ(-9999).setMaxQ(9999).add();
        List<LfGenerator> generators = createLfGeneratorsWithInitQ(network, List.of(0.3d, 0.1d, 0.4d));
        LfGenerator g0 = generators.get(0);
        LfGenerator g1 = generators.get(1);
        LfGenerator g2 = generators.get(2);
        g0.setCalculatedQ(0);
        g1.setCalculatedQ(0);
        g2.setCalculatedQ(0);
        double qToDispatch = 20;
        double residueQ = AbstractLfBus.dispatchQ(new ArrayList<>(generators), true, ReactivePowerDispatchMode.K_EQUAL_PROPORTION, qToDispatch);
        assertEquals(0, residueQ, 0);
        assertEquals(6.666, g0.getCalculatedQ(), 1e-3);
        assertEquals(6.666, g1.getCalculatedQ(), 1e-3);
        assertEquals(6.666, g2.getCalculatedQ(), 1e-3);
        assertEquals(0.7, LfGenerator.qToK(g0, g0.getCalculatedQ()), 1e-3);
        assertEquals(0.066, LfGenerator.qToK(g1, g1.getCalculatedQ()), 1e-3);
        assertEquals(0.937, LfGenerator.qToK(g2, g2.getCalculatedQ()), 1e-3);
    }

    @Test
    void testBusHasCountryAttributeAfterLoading() {
        network = createNetwork();
        List<LfNetwork> networks = Networks.load(network, new MostMeshedSlackBusSelector());
        lfNetwork = networks.get(0);
        assertTrue(lfNetwork.getBusById("vl1_0").getCountry().isPresent());
        assertTrue(lfNetwork.getBusById("vl2_0").getCountry().isPresent());
        assertEquals(Country.FR, lfNetwork.getBusById("vl1_0").getCountry().orElseThrow());
        assertEquals(Country.FR, lfNetwork.getBusById("vl2_0").getCountry().orElseThrow());
    }
}
