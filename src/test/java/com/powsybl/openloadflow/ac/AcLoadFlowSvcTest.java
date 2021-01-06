/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.powsybl.openloadflow.util.LoadFlowAssert;

/**
 * SVC test case.
 *
 * g1        ld1
 * |          |
 * b1---------b2
 *      l1    |
 *           svc1
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowSvcTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line l1;
    private StaticVarCompensator svc1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    private Network createNetwork() {
        Network network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("S1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("S2")
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(101)
                .setQ0(150)
                .add();
        svc1 = vl2.newStaticVarCompensator()
                .setId("svc1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        l1 = network.newLine()
                .setId("l1")
                .setVoltageLevel1("vl1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setBus2("b2")
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return network;
    }

    @BeforeEach
    void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(false)
                .setDistributedSlack(false);
        this.parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.581824, bus2);
        assertAngleEquals(-0.057845, bus2);
        assertActivePowerEquals(101.216, l1.getTerminal1());
        assertReactivePowerEquals(150.649, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-150, l1.getTerminal2());
        assertTrue(Double.isNaN(svc1.getTerminal().getP()));
        assertTrue(Double.isNaN(svc1.getTerminal().getQ()));

        svc1.setVoltageSetPoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertActivePowerEquals(103.562, l1.getTerminal1());
        assertReactivePowerEquals(615.582, l1.getTerminal1());
        assertActivePowerEquals(-101, l1.getTerminal2());
        assertReactivePowerEquals(-607.897, l1.getTerminal2());
        assertActivePowerEquals(0, svc1.getTerminal());
        assertReactivePowerEquals(457.896, svc1.getTerminal());
    }

    @Test
    void shouldReachReactiveMaxLimit() {
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-svc1.getBmin() * svc1.getVoltageSetpoint() * svc1.getVoltageSetpoint(), svc1.getTerminal()); // min reactive limit has been correctly reached
    }

    private void run(Network network, Map<String, List<Double>> getterValues) {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        Generator generator = bus1.getGenerators().iterator().next();
        Load load = bus2.getLoads().iterator().next();
        getterValues.computeIfAbsent("line1.getTerminal1().getP()", key -> new ArrayList<>()).add(l1.getTerminal1().getP());
        getterValues.computeIfAbsent("line1.getTerminal1().getQ()", key -> new ArrayList<>()).add(l1.getTerminal1().getQ());
        getterValues.computeIfAbsent("line1.getTerminal2().getP()", key -> new ArrayList<>()).add(l1.getTerminal2().getP());
        getterValues.computeIfAbsent("line1.getTerminal2().getQ()", key -> new ArrayList<>()).add(l1.getTerminal2().getQ());

        getterValues.computeIfAbsent("bus1.getV()", key -> new ArrayList<>()).add(bus1.getV());
        getterValues.computeIfAbsent("bus1.getAngle()", key -> new ArrayList<>()).add(bus1.getAngle());
        getterValues.computeIfAbsent("generator.getTerminal().getP()", key -> new ArrayList<>()).add(generator.getTerminal().getP());
        getterValues.computeIfAbsent("generator.getTerminal().getQ()", key -> new ArrayList<>()).add(generator.getTerminal().getQ());

        getterValues.computeIfAbsent("bus2.getV()", key -> new ArrayList<>()).add(bus2.getV());
        getterValues.computeIfAbsent("bus2.getAngle()", key -> new ArrayList<>()).add(bus2.getAngle());
        getterValues.computeIfAbsent("load.getTerminal().getP()", key -> new ArrayList<>()).add(load.getTerminal().getP());
        getterValues.computeIfAbsent("load.getTerminal().getQ()", key -> new ArrayList<>()).add(load.getTerminal().getQ());
        getterValues.computeIfAbsent("svc1.getTerminal().getP()", key -> new ArrayList<>()).add(svc1.getTerminal().getP());
        getterValues.computeIfAbsent("svc1.getTerminal().getQ()", key -> new ArrayList<>()).add(svc1.getTerminal().getQ());
        assertTrue(result.isOk());
    }

    @Test
    void shouldUseLessReactivePowerWithBusVLQ() {
        Map<String, List<Double>> getterValues = new LinkedHashMap<>();
        parametersExt.setUseBusPVLQ(false);

        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        run(network, getterValues);

        parametersExt.setUseBusPVLQ(true);
        Network network = createNetwork();
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.001)
                .add();
        run(network, getterValues);

        for (Map.Entry<String, List<Double>> entry : getterValues.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue().stream().map(d -> String.valueOf(d)).reduce("", (s1, s2) -> s1 + (s1.isEmpty() ? "" : " ; ") + s2));
        }

        assertThat("V on bus2 should be greater", getterValues.get("bus2.getV()").get(1), new LoadFlowAssert.GreaterThan(getterValues.get("bus2.getV()").get(0)));
        assertThat("Q on svc1 should be lower", getterValues.get("svc1.getTerminal().getQ()").get(1), new LoadFlowAssert.LowerThan(getterValues.get("svc1.getTerminal().getQ()").get(0)));
    }
}
