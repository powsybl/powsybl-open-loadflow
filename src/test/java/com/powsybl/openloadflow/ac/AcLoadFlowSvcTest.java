/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.ActivePowerControlAdder;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowSvcTest.class);

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Line l1;
    private Generator g1;
    private Load ld1;
    private Generator g2;
    private StaticVarCompensator svc1;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    private void createNetwork() {
        network = Network.create("svc", "test");
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
        g1 = vl1.newGenerator()
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
        ld1 = vl2.newLoad()
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
    }

    private void createNetworkExtended() {
        this.createNetwork();
        svc1.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        g2 = bus2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(300)
                .setTargetP(200)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
        g2.newExtension(ActivePowerControlAdder.class)
                .withParticipate(true)
                .withDroop(2)
                .add();
    }

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(false)
                .setDistributedSlack(false);
        this.parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void test() {
        createNetwork();
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
        createNetwork();
        svc1.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-svc1.getBmin() * svc1.getVoltageSetpoint() * svc1.getVoltageSetpoint(), svc1.getTerminal()); // min reactive limit has been correctly reached
    }

    private void runLoadFlowAndStoreResults(String busType, Map<String, Map<String, Double>> reports) {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        Map<String, Double> report = reports.computeIfAbsent(busType, key -> new LinkedHashMap<>());
        report.put("line1.getTerminal1().getP()", l1.getTerminal1().getP());
        report.put("line1.getTerminal1().getQ()", l1.getTerminal1().getQ());
        report.put("line1.getTerminal2().getP()", l1.getTerminal2().getP());
        report.put("line1.getTerminal2().getQ()", l1.getTerminal2().getQ());

        report.put("bus1.getV()", bus1.getV());
        report.put("bus1.getAngle()", bus1.getAngle());
        report.put("g1.getTerminal().getP()", g1.getTerminal().getP());
        report.put("g1.getTerminal().getQ()", g1.getTerminal().getQ());
        report.put("g2.getTargetQ()", g2.getTargetQ());
        report.put("g2.getTerminal().getP()", g2.getTerminal().getP());
        report.put("g2.getTerminal().getQ()", g2.getTerminal().getQ());

        report.put("bus2.getV()", bus2.getV());
        report.put("bus2.getAngle()", bus2.getAngle());
        report.put("ld1.getTerminal().getP()", ld1.getTerminal().getP());
        report.put("ld1.getTerminal().getQ()", ld1.getTerminal().getQ());
        report.put("svc1.getTerminal().getP()", svc1.getTerminal().getP());
        report.put("svc1.getTerminal().getQ()", svc1.getTerminal().getQ());

        assertTrue(result.isOk());
    }

    @Test
    void shouldUseLessReactivePowerWithBusVLQ() {
        // Map<busType, <Map<getter, value>>
        Map<String, Map<String, Double>> reports = new LinkedHashMap<>();
        Double slope = 0.01;

        // 1 - run with bus2 as bus PV
        createNetworkExtended();
        parametersExt.setUseBusPVLQ(false);
        runLoadFlowAndStoreResults("busPV", reports);

        // 2 - run with bus2 as bus PVLQ
        parametersExt.setUseBusPVLQ(true);
        createNetworkExtended();
        svc1.newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(slope)
                .add();
        runLoadFlowAndStoreResults("busPVLQ", reports);

        // display report
        for (Map.Entry<String, Map<String, Double>> report : reports.entrySet()) {
            LOGGER.debug(">>> Report about bus : {}", report.getKey());
            for (Map.Entry<String, Double> getter : report.getValue().entrySet()) {
                LOGGER.debug("{} = {}", getter.getKey(), getter.getValue());
            }
        }

        // assertions
        assertThat("with PV bus, V on bus2 should remains constant", reports.get("busPV").get("bus2.getV()"),
                new IsEqual(svc1.getVoltageSetpoint()));
        assertThat("with PVLQ bus, V on bus2 should be equals to 'voltageSetpoint + slope * Qsvc'", reports.get("busPVLQ").get("bus2.getV()"),
                new LoadFlowAssert.EqualsTo(svc1.getVoltageSetpoint() +  slope * (reports.get("busPVLQ").get("svc1.getTerminal().getQ()")), DELTA_V));
        assertThat("Q on svc1 should be lower with bus PVLQ than PV", reports.get("busPVLQ").get("svc1.getTerminal().getQ()"),
                new LoadFlowAssert.LowerThan(reports.get("busPV").get("svc1.getTerminal().getQ()")));
        assertThat("V on bus2 should be greater with bus PVLQ than PV", reports.get("busPVLQ").get("bus2.getV()"),
                new LoadFlowAssert.GreaterThan(reports.get("busPV").get("bus2.getV()")));
    }
}
