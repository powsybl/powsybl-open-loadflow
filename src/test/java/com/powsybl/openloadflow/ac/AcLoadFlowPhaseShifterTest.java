/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
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
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AcLoadFlowPhaseShifterTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;
    private Line line1;
    private Line line2;
    private TwoWindingsTransformer ps1TwoWT;
    private ThreeWindingsTransformer ps1ThreeWT;
    private PhaseTapChanger phaseTapChanger;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setNoGeneratorReactiveLimits(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new FirstSlackBusSelector())
                .setDistributedSlack(false);
        this.parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void baseCase2WTTest() {
        selectNetwork(create2WTNetwork());

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
    void tapPlusOne2WTTest() {
        selectNetwork(create2WTNetwork());
        phaseTapChanger.setTapPosition(2);

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
    void flowControl2WTTest() {
        selectNetwork(create2WTNetwork());
        parameters.setPhaseShifterRegulationOn(true);
        phaseTapChanger
                .setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(ps1TwoWT.getTerminal1())
                .setRegulationValue(83);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(83.587, line2.getTerminal1());
        assertActivePowerEquals(-83.486, line2.getTerminal2());
        assertEquals(2, phaseTapChanger.getTapPosition());

        phaseTapChanger
                .setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationValue(83)
                .setRegulationTerminal(ps1TwoWT.getTerminal2());

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(16.528, line2.getTerminal1());
        assertActivePowerEquals(-16.514, line2.getTerminal2());
        assertEquals(0, phaseTapChanger.getTapPosition());

    }

    @Test
    void baseCase3WTTest() {
        selectNetwork(create3WTNetwork());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(378.25191733234965, bus2); // 385.698
        assertAngleEquals(-3.635322112477251, bus2); // -3.679569
        assertVoltageEquals(381.45561867320447, bus3); // 392.648
        assertAngleEquals(-2.6012527358034787, bus3); // -1.806254
        assertVoltageEquals(372.1620401532227, bus4); // NEW
        assertAngleEquals(-2.550385462101632, bus4); // NEW
        assertActivePowerEquals(48.84754295342194, line1.getTerminal1()); // 50.089
        assertReactivePowerEquals(44.04142938099036, line1.getTerminal1()); // 29.192
        assertActivePowerEquals(-48.739399704559176, line1.getTerminal2()); // -50.005
        assertReactivePowerEquals(-38.634266937856474, line1.getTerminal2()); // -24.991
        assertActivePowerEquals(26.277861524499986, line2.getTerminal1()); // 50.048
        assertReactivePowerEquals(11.930125089002578, line2.getTerminal1()); // 27.097
        assertActivePowerEquals(-26.26641402107318, line2.getTerminal2()); // -50.006
        assertReactivePowerEquals(-11.357749917671041, line2.getTerminal2()); // -24.996
    }

    @Test
    void tapPlusOne3WTTest() {
        selectNetwork(create3WTNetwork());
        phaseTapChanger.setTapPosition(2);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(377.7817093021516, bus2); // 385.296
        assertAngleEquals(-5.697417220150056, bus2); // -1.186517
        assertVoltageEquals(381.2928579136935, bus3); // 392.076
        assertAngleEquals(-5.737530604739468, bus3); // 1.964715
        assertVoltageEquals(372.1717464076424, bus4); // NEW
        assertAngleEquals(-1.6404666559146512, bus4); // NEW
        assertActivePowerEquals(75.94148268594054, line1.getTerminal1()); // 16.541
        assertReactivePowerEquals(46.65020020813443, line1.getTerminal1()); // 29.241
        assertActivePowerEquals(-75.7428989366405, line1.getTerminal2()); // -16.513
        assertReactivePowerEquals(-36.721012743136995, line1.getTerminal2()); // -27.831
        assertActivePowerEquals(-0.7404191500133481, line2.getTerminal1()); // 83.587
        assertReactivePowerEquals(13.402920292490858, line2.getTerminal1()); // 27.195
        assertActivePowerEquals(0.7428979123408874, line2.getTerminal2()); // -83.487
        assertReactivePowerEquals(-13.27898217612303, line2.getTerminal2()); // -22.169
    }

    @Test
    void flowControl3WTTest() {
        selectNetwork(create3WTNetwork());
        parameters.setPhaseShifterRegulationOn(true);
        phaseTapChanger.setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(ps1ThreeWT.getLeg1().getTerminal())
                .setRegulationValue(83);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(26.277861524499986, line2.getTerminal1()); // 83.587
        assertActivePowerEquals(-26.26641402107318, line2.getTerminal2()); // -83.486
        assertEquals(1, phaseTapChanger.getTapPosition()); // 2

        phaseTapChanger.setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationValue(83)
                .setRegulationTerminal(ps1ThreeWT.getLeg2().getTerminal());

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(26.277861524499986, line2.getTerminal1()); // 16.528
        assertActivePowerEquals(-26.26641402107318, line2.getTerminal2()); // -16.514
        assertEquals(1, phaseTapChanger.getTapPosition()); // 0
    }

    /**
     * A very small network to test phase shifters with 2WT.
     *
     *     G1                   LD2
     *     |          L1        |
     *     |  ----------------- |
     *     B1                   B2
     *        --------B3-------
     *           PS1       L2
     */

    private static Network create2WTNetwork() {
        Network network = PhaseShifterTestCaseFactory.create();
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(-5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);
        return network;
    }

    /**
     * A very small network to test phase shifters with 3WT.
     *
     *     G1                   LD2
     *     |          L1        |
     *     |  ----------------- |
     *     B1         B3 ------ B2
     *       \      /      L2
     *         PS1
     *          |
     *          B4
     *          |
     *         LD4
     */
    private static Network create3WTNetwork() {
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
                .setP0(75.0) // 100
                .setQ0(50.0)
                .add();
        ld2.getTerminal().setP(75.0).setQ(50.0); // P(100)
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
        ps1.getLeg3().getTerminal().setP(-25.042015).setQ(-27.100708); // NEW
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

    private void selectNetwork(Network network) {
        this.network = network;
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");
        bus4 = network.getBusBreakerView().getBus("B4");

        line1 = network.getLine("L1");
        line2 = network.getLine("L2");
        ps1TwoWT = network.getTwoWindingsTransformer("PS1");
        ps1ThreeWT = network.getThreeWindingsTransformer("PS1");
        if (ps1TwoWT != null) {
            phaseTapChanger = ps1TwoWT.getPhaseTapChanger();
        } else {
            phaseTapChanger = ps1ThreeWT.getLeg2().getPhaseTapChanger();
        }
    }
}
