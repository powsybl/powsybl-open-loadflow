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
    private VoltageLevel vl1;
    private Bus bus1;
    private VoltageLevel vl2;
    private Bus bus2;
    private Line bus1bus2line;
    private Generator bus1gen;
    private Load bus2ld;
    private StaticVarCompensator bus2svc;
    private Generator bus2gen;
    private ShuntCompensator bus2sc;
    private Line bus2line;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    private AcLoadFlowSvcTest createNetworkBus1GenBus2LoadSvc() {
        network = Network.create("svc", "test");
        Substation s1 = network.newSubstation()
                .setId("s1")
                .add();
        Substation s2 = network.newSubstation()
                .setId("s2")
                .add();
        vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("bus1")
                .add();
        bus1gen = vl1.newGenerator()
                .setId("bus1gen")
                .setConnectableBus(bus1.getId())
                .setBus(bus1.getId())
                .setTargetP(101.3664)
                .setTargetV(390)
                .setMinP(0)
                .setMaxP(150)
                .setVoltageRegulatorOn(true)
                .add();
        vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("bus2")
                .add();
        bus2ld = vl2.newLoad()
                .setId("bus2ld")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setP0(101)
                .setQ0(150)
                .add();
        bus2svc = vl2.newStaticVarCompensator()
                .setId("bus2svc")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setRegulationMode(StaticVarCompensator.RegulationMode.OFF)
                .setBmin(-0.008)
                .setBmax(0.008)
                .add();
        bus1bus2line = network.newLine()
                .setId("bus1bus2line")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return this;
    }

    private AcLoadFlowSvcTest setSvcVoltage() {
        bus2svc.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        return this;
    }

    private AcLoadFlowSvcTest addBus2Gen() {
        bus2gen = bus2.getVoltageLevel()
                .newGenerator()
                .setId("bus2gen")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(300)
                .setTargetP(200)
                .setTargetQ(300)
                .setVoltageRegulatorOn(false)
                .add();
//        g2.newExtension(ActivePowerControlAdder.class)
//                .withParticipate(true)
//                .withDroop(2)
//                .add();
        return this;
    }

    private AcLoadFlowSvcTest addBus2Sc() {
        bus2sc = bus2.getVoltageLevel().newShuntCompensator()
                .setId("bus2sc")
                .setBus(bus2.getId())
                .setConnectableBus(bus2.getId())
                .setSectionCount(1)
                .newLinearModel()
                    .setBPerSection(Math.pow(10, -4))
                    .setMaximumSectionCount(1)
                    .add()
                .add();
        return this;
    }

    private AcLoadFlowSvcTest addOpenLine() {
        bus2line = network.newLine()
                .setId("bus2line")
                .setVoltageLevel1(vl1.getId())
                .setBus1(bus1.getId())
                .setVoltageLevel2(vl2.getId())
                .setBus2(bus2.getId())
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        return this;
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
        createNetworkBus1GenBus2LoadSvc();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(388.581824, bus2);
        assertAngleEquals(-0.057845, bus2);
        assertActivePowerEquals(101.216, bus1bus2line.getTerminal1());
        assertReactivePowerEquals(150.649, bus1bus2line.getTerminal1());
        assertActivePowerEquals(-101, bus1bus2line.getTerminal2());
        assertReactivePowerEquals(-150, bus1bus2line.getTerminal2());
        assertTrue(Double.isNaN(bus2svc.getTerminal().getP()));
        assertTrue(Double.isNaN(bus2svc.getTerminal().getQ()));

        bus2svc.setVoltageSetPoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116345, bus2);
        assertActivePowerEquals(103.562, bus1bus2line.getTerminal1());
        assertReactivePowerEquals(615.582, bus1bus2line.getTerminal1());
        assertActivePowerEquals(-101, bus1bus2line.getTerminal2());
        assertReactivePowerEquals(-607.897, bus1bus2line.getTerminal2());
        assertActivePowerEquals(0, bus2svc.getTerminal());
        assertReactivePowerEquals(457.896, bus2svc.getTerminal());
    }

    @Test
    void shouldReachReactiveMaxLimit() {
        createNetworkBus1GenBus2LoadSvc();
        bus2svc.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-bus2svc.getBmin() * bus2svc.getVoltageSetpoint() * bus2svc.getVoltageSetpoint(), bus2svc.getTerminal()); // min reactive limit has been correctly reached
    }

    private void runLoadFlowAndStoreResults(String busType, Map<String, Map<String, Double>> reports) {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        Map<String, Double> report = reports.computeIfAbsent(busType, key -> new LinkedHashMap<>());
        report.put("bus1bus2line.getTerminal1().getP()", bus1bus2line.getTerminal1().getP());
        report.put("bus1bus2line.getTerminal1().getQ()", bus1bus2line.getTerminal1().getQ());
        report.put("bus1bus2line.getTerminal2().getP()", bus1bus2line.getTerminal2().getP());
        report.put("bus1bus2line.getTerminal2().getQ()", bus1bus2line.getTerminal2().getQ());

        report.put("bus1.getV()", bus1.getV());
        report.put("bus1.getAngle()", bus1.getAngle());
        report.put("bus1gen.getTerminal().getP()", bus1gen.getTerminal().getP());
        report.put("bus1gen.getTerminal().getQ()", bus1gen.getTerminal().getQ());

        report.put("bus2.getV()", bus2.getV());
        report.put("bus2.getAngle()", bus2.getAngle());
        report.put("bus2ld.getTerminal().getP()", bus2ld.getTerminal().getP());
        report.put("bus2ld.getTerminal().getQ()", bus2ld.getTerminal().getQ());
        report.put("bus2svc.getTerminal().getP()", bus2svc.getTerminal().getP());
        report.put("bus2svc.getTerminal().getQ()", bus2svc.getTerminal().getQ());
        if (bus2gen != null) {
            report.put("bus2gen.getTargetQ()", bus2gen.getTargetQ());
            report.put("bus2gen.getTerminal().getP()", bus2gen.getTerminal().getP());
            report.put("bus2gen.getTerminal().getQ()", bus2gen.getTerminal().getQ());
        }
        if (bus2sc != null) {
            report.put("bus2sc.getTerminal().getQ()", bus2sc.getTerminal().getQ());
        }

        assertTrue(result.isOk());
    }

    private void shouldUseLessReactivePowerWithBusVLQOnGivenNetwork(Runnable networkCreator) {
        // Map<busType, <Map<getter, value>>
        Map<String, Map<String, Double>> reports = new LinkedHashMap<>();
        Double slope = 0.01;

        // 1 - run with bus2 as bus PV
        networkCreator.run();
        parametersExt.setUseBusPVLQ(false);
        runLoadFlowAndStoreResults("busPV", reports);

        // 2 - run with bus2 as bus PVLQ
        parametersExt.setUseBusPVLQ(true);
        networkCreator.run();
        bus2svc.newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(slope)
                .add();
        runLoadFlowAndStoreResults("busPVLQ", reports);

        // display reports
        for (Map.Entry<String, Map<String, Double>> report : reports.entrySet()) {
            LOGGER.debug(">>> Report about bus : {}", report.getKey());
            for (Map.Entry<String, Double> getter : report.getValue().entrySet()) {
                LOGGER.debug("{} = {}", getter.getKey(), getter.getValue());
            }
        }

        // assertions
        assertThat("with PV bus, V on bus2 should remains constant", reports.get("busPV").get("bus2.getV()"),
                new IsEqual(bus2svc.getVoltageSetpoint()));
        assertThat("with PVLQ bus, V on bus2 should be equals to 'voltageSetpoint + slope * Qsvc'", reports.get("busPVLQ").get("bus2.getV()"),
                new LoadFlowAssert.EqualsTo(bus2svc.getVoltageSetpoint() +  slope * (reports.get("busPVLQ").get("bus2svc.getTerminal().getQ()")), DELTA_V));
        assertThat("Q on bus2svc should be lower with bus PVLQ than PV", reports.get("busPVLQ").get("bus2svc.getTerminal().getQ()"),
                new LoadFlowAssert.LowerThan(reports.get("busPV").get("bus2svc.getTerminal().getQ()")));
        assertThat("V on bus2 should be greater with bus PVLQ than PV", reports.get("busPVLQ").get("bus2.getV()"),
                new LoadFlowAssert.GreaterThan(reports.get("busPV").get("bus2.getV()")));
    }

    @Test
    void shouldUseLessReactivePowerWithBusVLQ() {
        this.shouldUseLessReactivePowerWithBusVLQOnGivenNetwork(() -> this.createNetworkBus1GenBus2LoadSvc().setSvcVoltage());
        this.shouldUseLessReactivePowerWithBusVLQOnGivenNetwork(() -> this.createNetworkBus1GenBus2LoadSvc().setSvcVoltage().addBus2Gen());
        this.shouldUseLessReactivePowerWithBusVLQOnGivenNetwork(() -> this.createNetworkBus1GenBus2LoadSvc().setSvcVoltage().addBus2Gen().addBus2Sc());
    }
}
