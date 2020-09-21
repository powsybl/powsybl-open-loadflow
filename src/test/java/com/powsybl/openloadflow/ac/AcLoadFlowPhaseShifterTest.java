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
    private TwoWindingsTransformer t2wt;
    private ThreeWindingsTransformer t3wt;

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
    void baseCaseT2wtTest() {
        selectNetwork(createNetworkWithT2wt());

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
    void tapPlusOneT2wtTest() {
        selectNetwork(createNetworkWithT2wt());
        t2wt.getPhaseTapChanger().setTapPosition(2);

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
    void flowControlT2wtTest() {
        selectNetwork(createNetworkWithT2wt());
        parameters.setPhaseShifterRegulationOn(true);
        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationValue(83);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(83.587, line2.getTerminal1());
        assertActivePowerEquals(-83.486, line2.getTerminal2());
        assertEquals(2, t2wt.getPhaseTapChanger().getTapPosition());

        t2wt.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationValue(83)
                .setRegulationTerminal(t2wt.getTerminal2());

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(16.528, line2.getTerminal1());
        assertActivePowerEquals(-16.514, line2.getTerminal2());
        assertEquals(0, t2wt.getPhaseTapChanger().getTapPosition());
    }

    @Test
    void baseCaseT3wtTest() {
        selectNetwork(createNetworkWithT3wt());
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(378.252, bus2);
        assertAngleEquals(-3.635322112477251, bus2);
        assertVoltageEquals(381.456, bus3);
        assertAngleEquals(-2.6012527358034787, bus3);
        assertVoltageEquals(372.162, bus4);
        assertAngleEquals(-2.550385462101632, bus4);
        assertActivePowerEquals(48.848, line1.getTerminal1());
        assertReactivePowerEquals(44.041, line1.getTerminal1());
        assertActivePowerEquals(-48.739, line1.getTerminal2());
        assertReactivePowerEquals(-38.634, line1.getTerminal2());
        assertActivePowerEquals(26.278, line2.getTerminal1());
        assertReactivePowerEquals(11.9308, line2.getTerminal1());
        assertActivePowerEquals(-26.266, line2.getTerminal2());
        assertReactivePowerEquals(-11.358, line2.getTerminal2());
    }

    @Test
    void tapPlusOneT3wtTest() {
        selectNetwork(createNetworkWithT3wt());
        t3wt.getLeg2().getPhaseTapChanger().setTapPosition(2);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(400, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(377.782, bus2);
        assertAngleEquals(-5.697417220150056, bus2);
        assertVoltageEquals(381.293, bus3);
        assertAngleEquals(-5.737530604739468, bus3);
        assertVoltageEquals(372.172, bus4);
        assertAngleEquals(-1.6404666559146515, bus4);
        assertActivePowerEquals(75.941, line1.getTerminal1());
        assertReactivePowerEquals(46.650, line1.getTerminal1());
        assertActivePowerEquals(-75.742, line1.getTerminal2());
        assertReactivePowerEquals(-36.721, line1.getTerminal2());
        assertActivePowerEquals(-0.740, line2.getTerminal1());
        assertReactivePowerEquals(13.403, line2.getTerminal1());
        assertActivePowerEquals(0.743, line2.getTerminal2());
        assertReactivePowerEquals(-13.279, line2.getTerminal2());
    }

    @Test
    void flowControlT3wtTest() {
        selectNetwork(createNetworkWithT3wt());
        parameters.setPhaseShifterRegulationOn(true);
        t3wt.getLeg2().getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(t3wt.getLeg2().getTerminal())
                .setRegulationValue(0.);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(-0.7403999884197101, line2.getTerminal1());
        assertActivePowerEquals(0.7428793087142719, line2.getTerminal2());
        assertEquals(2, t3wt.getLeg2().getPhaseTapChanger().getTapPosition());
    }

    @Test
    void remoteFlowControlT3wtTest() {
        selectNetwork(createNetworkWithT3wt());
        parameters.setPhaseShifterRegulationOn(true);
        t3wt.getLeg2().getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL)
                .setTargetDeadband(1) // FIXME how to take this into account
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(line1.getTerminal1())
                .setRegulationValue(75);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(75.94143342722937, line1.getTerminal1());
        assertEquals(2, t3wt.getLeg2().getPhaseTapChanger().getTapPosition());
    }

    /**
     * A very small network to test a phase shifter on a T2wt.
     *
     *     G1                   LD2
     *     |          L1        |
     *     |  ----------------- |
     *     B1                   B2
     *        --------B3-------
     *           PS1       L2
     */
    private static Network createNetworkWithT2wt() {
        Network network = PhaseShifterTestCaseFactory.create();
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(-5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);
        return network;
    }

    /**
     * A very small network to test a phase shifter on a T3wt.
     *
     *     G1                   LD2
     *     |          L1        |
     *     |  ----------------- |
     *     B1         B3 ------ B2
     *       \       /     L2
     *     leg1    leg2
     *        \   /
     *         PS1
     *          |
     *         leg3
     *          |
     *          B4
     *          |
     *         LD4
     */
    private static Network createNetworkWithT3wt() {
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
        vl2.newLoad()
                .setId("LD2")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(75.0)
                .setQ0(50.0)
                .add();
        network.newLine()
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
        VoltageLevel vl3 = s1.newVoltageLevel()
                .setId("VL3")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b3 = vl3.getBusBreakerView().newBus()
                .setId("B3")
                .add();
        VoltageLevel vl4 = s1.newVoltageLevel()
                .setId("VL4")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b4 = vl4.getBusBreakerView().newBus()
                .setId("B4")
                .add();
        vl4.newLoad()
                .setId("LD3")
                .setConnectableBus("B4")
                .setBus("B4")
                .setP0(25.0)
                .setQ0(50.0)
                .add();
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
        network.newLine()
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
        t2wt = network.getTwoWindingsTransformer("PS1");
        t3wt = network.getThreeWindingsTransformer("PS1");
    }
}
