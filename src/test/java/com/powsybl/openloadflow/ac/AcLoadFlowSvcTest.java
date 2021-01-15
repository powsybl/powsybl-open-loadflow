/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControl;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.LoadFlowRunResults;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private AcLoadFlowSvcTest createNetworkBus1GenBus2Svc() {
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

    private AcLoadFlowSvcTest addBus2Load() {
        bus2ld = vl2.newLoad()
                .setId("bus2ld")
                .setConnectableBus(bus2.getId())
                .setBus(bus2.getId())
                .setP0(101)
                .setQ0(150)
                .add();
        return this;
    }

    private AcLoadFlowSvcTest setSvcVoltageAndSlope() {
        bus2svc.setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .newExtension(VoltagePerReactivePowerControlAdder.class)
                .withSlope(0.01)
                .add();
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
                .setTargetP(0)
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
//                .setBus1(bus1.getId())
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
        createNetworkBus1GenBus2Svc().addBus2Load();
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
        createNetworkBus1GenBus2Svc().addBus2Load();
        bus2svc.setBmin(-0.002)
                .setVoltageSetpoint(385)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(-bus2svc.getBmin() * bus2svc.getVoltageSetpoint() * bus2svc.getVoltageSetpoint(), bus2svc.getTerminal()); // min reactive limit has been correctly reached
    }

    private enum NetworkDescription { BUS1_GEN_BUS2_SVC, BUS1_GEN_BUS2_SVC_LOAD, BUS1_GEN_BUS2_SVC_LOAD_GEN, BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, BUS1_GEN_BUS2_SVC_LOAD_GEN_SC_LINE }
    private enum RunningParameters { USE_BUS_PV, USE_BUS_PVLQ }

    private void shouldMatchVoltageTerm(Runnable networkCreator, NetworkDescription networkDescription, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults) {
        // 1 - run with bus2 as bus PV
        parametersExt.setUseBusPVLQ(false);
        networkCreator.run();
        assertTrue(loadFlowRunner.run(network, parameters).isOk());
        Network networkPV = loadFlowRunResults.addLoadFlowReport(networkDescription, RunningParameters.USE_BUS_PV, network);

        // 2 - run with bus2 as bus PVLQ
        parametersExt.setUseBusPVLQ(true);
        networkCreator.run();
        assertTrue(loadFlowRunner.run(network, parameters).isOk());
        Network networkPVLQ = loadFlowRunResults.addLoadFlowReport(networkDescription, RunningParameters.USE_BUS_PVLQ, network);

        // assertions
        Double slope = bus2svc.getExtension(VoltagePerReactivePowerControl.class).getSlope();
        assertThat("with PV bus, V on bus2 should remains constant", networkPV.getBusView().getBus("vl2_0").getV(),
                new IsEqual(networkPV.getStaticVarCompensator("bus2svc").getVoltageSetpoint()));
        assertThat("with PVLQ bus, voltageSetpoint should be equals to 'V on bus2 + slope * Qsvc'", networkPVLQ.getStaticVarCompensator("bus2svc").getVoltageSetpoint(),
                new LoadFlowAssert.EqualsTo(networkPVLQ.getBusView().getBus("vl2_0").getV() +
                        slope * (-networkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ()), DELTA_V));
        assertThat("Qsvc should be greater with bus PVLQ than PV", -networkPVLQ.getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                new LoadFlowAssert.GreaterThan(-networkPV.getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        assertThat("V on bus2 should be greater with bus PVLQ than PV", networkPVLQ.getBusView().getBus("vl2_0").getV(),
                new LoadFlowAssert.GreaterThan(networkPV.getBusView().getBus("vl2_0").getV()));
    }

    private void shouldIncreaseQsvc(String reason, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults, NetworkDescription actualNetwork, NetworkDescription previousNetwork) {
        assertThat("with PV bus and " + reason, -loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PV).getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                new LoadFlowAssert.GreaterThan(-loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PV).getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        assertThat("with PVLQ bus and " + reason, -loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PVLQ).getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                new LoadFlowAssert.GreaterThan(-loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PVLQ).getStaticVarCompensator("bus2svc").getTerminal().getQ()));
    }

    private void shouldLowerQsvc(String reason, LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults, NetworkDescription actualNetwork, NetworkDescription previousNetwork) {
        assertThat("with PV bus and " + reason, -loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PV).getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                new LoadFlowAssert.LowerThan(-loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PV).getStaticVarCompensator("bus2svc").getTerminal().getQ()));
        assertThat("with PVLQ bus and " + reason, -loadFlowRunResults.getLoadFlowReport(actualNetwork, RunningParameters.USE_BUS_PVLQ).getStaticVarCompensator("bus2svc").getTerminal().getQ(),
                new LoadFlowAssert.LowerThan(-loadFlowRunResults.getLoadFlowReport(previousNetwork, RunningParameters.USE_BUS_PVLQ).getStaticVarCompensator("bus2svc").getTerminal().getQ()));
    }

    @Test
    void shouldProperlyRunWithBusVLQ() {
        LoadFlowRunResults<NetworkDescription, RunningParameters> loadFlowRunResults = new LoadFlowRunResults<>();
        this.shouldMatchVoltageTerm(() -> this.createNetworkBus1GenBus2Svc().setSvcVoltageAndSlope(), NetworkDescription.BUS1_GEN_BUS2_SVC, loadFlowRunResults);
        this.shouldMatchVoltageTerm(() -> this.createNetworkBus1GenBus2Svc().setSvcVoltageAndSlope().addBus2Load(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD, loadFlowRunResults);
        this.shouldMatchVoltageTerm(() -> this.createNetworkBus1GenBus2Svc().setSvcVoltageAndSlope().addBus2Load().addBus2Gen(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN, loadFlowRunResults);
        this.shouldMatchVoltageTerm(() -> this.createNetworkBus1GenBus2Svc().setSvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, loadFlowRunResults);
//        this.shouldCheckVoltageTerm(() -> this.createNetworkBus1GenBus2Svc().setSvcVoltageAndSlope().addBus2Load().addBus2Gen().addBus2Sc().addOpenLine(), NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN_SC_LINE, loadFlowRunResults);
        loadFlowRunResults.displayAll();

        loadFlowRunResults.getLoadFlowReport(NetworkDescription.BUS1_GEN_BUS2_SVC, RunningParameters.USE_BUS_PV);
        shouldIncreaseQsvc("a load addition, Qsvc should be greater", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD, NetworkDescription.BUS1_GEN_BUS2_SVC);
        shouldLowerQsvc("a generator addition, Qsvc should be lower", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD);
        shouldLowerQsvc("a shunt addition, Qsvc should be lower", loadFlowRunResults, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN_SC, NetworkDescription.BUS1_GEN_BUS2_SVC_LOAD_GEN);

        loadFlowRunResults.shouldHaveValidSumOfQinLines();
    }
}
