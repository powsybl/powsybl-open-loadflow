/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Thomas Adam <tadam at silicom.fr>
 */
class AcLoadFlowPhaseShifter3WindingsTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Line line1;
    private Line line2;
    private ThreeWindingsTransformer ps1;
    private PhaseTapChanger phaseTapChanger;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        network = create();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");

        line1 = network.getLine("L1");
        line2 = network.getLine("L2");
        ps1 = network.getThreeWindingsTransformer("PS1");
        phaseTapChanger = ps1.getLeg2().getPhaseTapChanger();
        phaseTapChanger.getStep(0).setAlpha(-5);
        phaseTapChanger.getStep(2).setAlpha(5);

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setNoGeneratorReactiveLimits(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setDistributedSlack(false);
        this.parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void baseCaseTest() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385.698, bus2);
        assertAngleEquals(-3.679569, bus2);
        assertVoltageEquals(392.648, bus3);
        assertAngleEquals(-1.806254, bus3);
        assertActivePowerEquals(50.089, line1.getTerminal1());
        assertReactivePowerEquals(29.192, line1.getTerminal1());
        assertActivePowerEquals(-50.005, line1.getTerminal2());
        assertReactivePowerEquals(-24.991, line1.getTerminal2());
        assertActivePowerEquals(50.048, line2.getTerminal1());
        assertReactivePowerEquals(27.097, line2.getTerminal1());
        assertActivePowerEquals(-50.006, line2.getTerminal2());
        assertReactivePowerEquals(-24.996, line2.getTerminal2());
    }

    @Test
    void tapPlusOneTest() {
        ps1.getLeg2().getPhaseTapChanger().setTapPosition(2);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(385.296, bus2);
        assertAngleEquals(-1.186517, bus2);
        assertVoltageEquals(392.076, bus3);
        assertAngleEquals(1.964715, bus3);
        assertActivePowerEquals(16.541, line1.getTerminal1());
        assertReactivePowerEquals(29.241, line1.getTerminal1());
        assertActivePowerEquals(-16.513, line1.getTerminal2());
        assertReactivePowerEquals(-27.831, line1.getTerminal2());
        assertActivePowerEquals(83.587, line2.getTerminal1());
        assertReactivePowerEquals(27.195, line2.getTerminal1());
        assertActivePowerEquals(-83.487, line2.getTerminal2());
        assertReactivePowerEquals(-22.169, line2.getTerminal2());
    }

    @Test
    void flowControlTest() {
        parameters.setPhaseShifterRegulationOn(true);
        phaseTapChanger.setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                       .setTargetDeadband(1) // FIXME how to take this into account
                       .setRegulating(true)
                       .setTapPosition(1)
                       .setRegulationTerminal(ps1.getLeg1().getTerminal())
                       .setRegulationValue(83);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(83.587, line2.getTerminal1());
        assertActivePowerEquals(-83.486, line2.getTerminal2());
        assertEquals(2, ps1.getLeg2().getPhaseTapChanger().getTapPosition());

        phaseTapChanger.setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                       .setTargetDeadband(1) // FIXME how to take this into account
                       .setRegulating(true)
                       .setTapPosition(1)
                       .setRegulationValue(83)
                       .setRegulationTerminal(ps1.getLeg2().getTerminal());

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(16.528, line2.getTerminal1());
        assertActivePowerEquals(-16.514, line2.getTerminal2());
        assertEquals(0, ps1.getLeg2().getPhaseTapChanger().getTapPosition());

    }

    public static Network create() {
        Network network = NetworkFactory.findDefault().createNetwork("three-windings-transformer", "test");
        network.setCaseDate(DateTime.parse("2020-04-05T14:11:00.000+01:00"));
        Substation s1 = network.newSubstation()
                .setId("S1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("B1")
                .add();
        b1.setV(400).setAngle(0);
        Generator g1 = vl1.newGenerator()
                .setId("G1")
                .setConnectableBus("B1")
                .setBus("B1")
                .setVoltageRegulatorOn(true)
                .setTargetP(100.0)
                .setTargetV(400.0)
                .setMinP(50.0)
                .setMaxP(150.0)
                .add();
        g1.getTerminal().setP(-100.16797).setQ(-58.402832);
        Substation s2 = network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("VL2")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b2 = vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        b2.setV(385.6934).setAngle(-3.6792064);
        Load ld2 = vl2.newLoad()
                .setId("LD2")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(75.0)
                .setQ0(50.0)
                .add();
        ld2.getTerminal().setP(75.0).setQ(50.0);
        Line l1 = network.newLine()
                .setId("L1")
                .setVoltageLevel1("VL1")
                .setConnectableBus1("B1")
                .setBus1("B1")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(4.0)
                .setX(200.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        l1.getTerminal1().setP(50.084026).setQ(29.201416);
        l1.getTerminal2().setP(-50.0).setQ(-25.0);
        VoltageLevel vl3 = s1.newVoltageLevel()
                .setId("VL3")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        b3.setV(392.6443).setAngle(-1.8060945);

        VoltageLevel vl4 = s1.newVoltageLevel()
                .setId("VL4")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        b4.setV(392.6443).setAngle(-1.8060945);
        Load ld3 = vl4.newLoad()
                .setId("LD3")
                .setConnectableBus("B4")
                .setBus("B4")
                .setP0(25.0)
                .setQ0(50.0)
                .add();
        ld3.getTerminal().setP(25.0).setQ(50.0);
        ThreeWindingsTransformer ps1 = s1.newThreeWindingsTransformer()
                .setId("PS1")
                .setRatedU0(400.0)
                .newLeg1()
                    .setR(2.0)
                    .setX(100.0)
                    .setG(0.0)
                    .setB(0.0)
                    .setRatedU(380.0)
                    .setVoltageLevel(vl1.getId())
                    .setConnectableBus(b1.getId())
                    .setBus(b1.getId())
                .add()
                .newLeg2()
                    .setR(2.0)
                    .setX(100.0)
                    .setG(0.0)
                    .setB(0.0)
                    .setRatedU(380.0)
                    .setVoltageLevel(vl3.getId())
                    .setConnectableBus(b3.getId())
                    .setBus(b3.getId())
                .add()
                .newLeg3()
                    .setR(2.0)
                    .setX(100.0)
                    .setG(0.0)
                    .setB(0.0)
                    .setRatedU(380.0)
                    .setVoltageLevel(vl4.getId())
                    .setConnectableBus(b4.getId())
                    .setBus(b4.getId())
                .add()
                .add();
        ps1.getLeg1().getTerminal().setP(50.08403).setQ(29.201416);
        ps1.getLeg2().getTerminal().setP(-25.042015).setQ(-27.100708);
        ps1.getLeg3().getTerminal().setP(-25.042015).setQ(-27.100708);
        ps1.getLeg2().newPhaseTapChanger()
                .setTapPosition(1)
                .setRegulationTerminal(ps1.getLeg2().getTerminal())
                .setRegulationMode(PhaseTapChanger.RegulationMode.FIXED_TAP)
                .setRegulationValue(200)
                .beginStep()
                    .setAlpha(-5.0)
                    .setRho(1.0)
                    .setR(0.0)
                    .setX(0.0)
                    .setG(0.0)
                    .setB(0.0)
                .endStep()
                .beginStep()
                    .setAlpha(0.0)
                    .setRho(1.0)
                    .setR(0.0)
                    .setX(0.0)
                    .setG(0.0)
                    .setB(0.0)
                .endStep()
                .beginStep()
                    .setAlpha(5)
                    .setRho(1.0)
                    .setR(0.0)
                    .setX(0.0)
                    .setG(0.0)
                    .setB(0.0)
                .endStep()
                .add();
        Line l2 = network.newLine()
                .setId("L2")
                .setVoltageLevel1("VL3")
                .setConnectableBus1("B3")
                .setBus1("B3")
                .setVoltageLevel2("VL2")
                .setConnectableBus2("B2")
                .setBus2("B2")
                .setR(2.0)
                .setX(100.0)
                .setG1(0.0)
                .setB1(0.0)
                .setG2(0.0)
                .setB2(0.0)
                .add();
        l2.getTerminal1().setP(50.042015).setQ(27.100708);
        l2.getTerminal2().setP(-50.0).setQ(-25.0);

        return network;
    }
}
