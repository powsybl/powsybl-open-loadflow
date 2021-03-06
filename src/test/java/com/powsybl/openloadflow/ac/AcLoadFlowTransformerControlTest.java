/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class AcLoadFlowTransformerControlTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Bus bus4;
    private Line line12;
    private Line line24;
    private TwoWindingsTransformer t2wt;
    private TwoWindingsTransformer t2wt2;
    private ThreeWindingsTransformer t3wt;
    private Load load3;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setTransformerVoltageControlOn(false);
        parameters.setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        this.parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void baseCaseT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(135.0, bus1);
        assertVoltageEquals(134.273, bus2);
        assertVoltageEquals(27.0038, bus3);
    }

    @Test
    void tapPlusTwoT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        t2wt.getRatioTapChanger().setTapPosition(2);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(135.0, bus1);
        assertVoltageEquals(134.281, bus2);
        assertVoltageEquals(34.427, bus3);
    }

    @Test
    void voltageControlT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        parameters.setTransformerVoltageControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(134.281, bus2);
        assertVoltageEquals(34.433, t2wt.getTerminal2().getBusView().getBus()); //FIXME: should be 34.427
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void remoteVoltageControlT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        Substation substation = network.newSubstation()
                .setId("SUBSTATION4")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl4 = substation.newVoltageLevel()
                .setId("VL_4")
                .setNominalV(32.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(100)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus4 = vl4.getBusBreakerView().newBus()
                .setId("BUS_4")
                .add();
        vl4.newLoad()
                .setId("LOAD_4")
                .setBus("BUS_4")
                .setP0(2.)
                .setQ0(0.5)
                .add();

        Line line34 = network.newLine()
                .setId("LINE_34")
                .setVoltageLevel1("VL_3")
                .setVoltageLevel2("VL_4")
                .setBus1("BUS_3")
                .setBus2("BUS_4")
                .setR(1.05)
                .setX(10.0)
                .setG1(0.0000005)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
                .add();

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(false)
                .setTapPosition(2)
                .setRegulationTerminal(line34.getTerminal2())
                .setTargetV(33.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertVoltageEquals(31.861, bus4);
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());

        parameters.setTransformerVoltageControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(line34.getTerminal2())
                .setTargetV(33.0);

        result = loadFlowRunner.run(network, parameters);
        assertVoltageEquals(31.862, bus4); //FIXME: should be 31.861
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void nonSupportedVoltageControlT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(network.getGenerator("GEN_1").getTerminal())
                .setTargetV(33.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(135.0, bus1);
    }

    @Test
    void sharedVoltageControlT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        TwoWindingsTransformer t2wt2 = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT2")
                .setVoltageLevel1("VL_2")
                .setVoltageLevel2("VL_3")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_3")
                .add();

        t2wt2.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(network.getLoad("LOAD_3").getTerminal())
                .add();

        parameters.setTransformerVoltageControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);
        t2wt2.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(134.279, bus2);
        assertVoltageEquals(35.730, t2wt.getTerminal2().getBusView().getBus());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void sharedVoltageControlT2wtWithZeroImpedanceLinesTest() {
        selectNetwork(createNetworkWithSharedControl());

        parameters.setTransformerVoltageControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);
        t2wt2.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt2.getTerminal2())
                .setTargetV(34.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(134.279, bus2);
        assertVoltageEquals(35.73, t2wt.getTerminal2().getBusView().getBus());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void sharedVoltageControlT2wtWithZeroImpedanceLinesTest2() {
        selectNetwork(createNetworkWithSharedControl());
        network.getVoltageLevel("VL_3").newGenerator()
                .setId("GEN_3")
                .setBus("BUS_3")
                .setMinP(0.0)
                .setMaxP(35.0)
                .setTargetP(2)
                .setTargetV(34.0)
                .setVoltageRegulatorOn(true)
                .add();

        parameters.setTransformerVoltageControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal2())
                .setTargetV(34.0);
        t2wt2.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt2.getTerminal2())
                .setTargetV(34.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(136.605, bus2);
        assertVoltageEquals(34.0, t2wt.getTerminal2().getBusView().getBus());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void openT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(network.getGenerator("GEN_1").getTerminal())
                .setTargetV(33.0);
        t2wt.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void regulatingTerminalDisconnectedTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());
        Load load = network.getLoad("LOAD_2");
        load.getTerminal().disconnect();

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(load.getTerminal())
                .setTargetV(33.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void t2wtWithoutLoadTapChangingCapabilitiesTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(network.getGenerator("GEN_1").getTerminal())
                .setTargetV(33.0)
                .setLoadTapChangingCapabilities(false);
        t2wt.getTerminal2().disconnect();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void baseCaseT3wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT3wt());

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(135.0, bus1);
        assertVoltageEquals(134.265, bus2);
        assertVoltageEquals(34.402, bus3);
        assertVoltageEquals(10.320, bus4);
    }

    @Test
    void tapPlusTwoT3wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT3wt());

        t3wt.getLeg2().getRatioTapChanger().setTapPosition(2);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(135.0, bus1);
        assertVoltageEquals(134.264, bus2);
        assertVoltageEquals(28.147, bus3);
        assertVoltageEquals(10.320, bus4);
    }

    @Test
    void voltageControlT3wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT3wt());

        t3wt.getLeg2().getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t3wt.getLeg2().getTerminal())
                .setTargetV(28.);

        parameters.setTransformerVoltageControlOn(true);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(28.147, bus3);
        assertEquals(2, t3wt.getLeg2().getRatioTapChanger().getTapPosition());
    }

    @Test
    void remoteVoltageControlT3wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT3wt());

        Substation substation = network.newSubstation()
                .setId("SUBSTATION5")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl5 = substation.newVoltageLevel()
                .setId("VL_5")
                .setNominalV(30.0)
                .setLowVoltageLimit(0)
                .setHighVoltageLimit(100.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus bus5 = vl5.getBusBreakerView().newBus()
                .setId("BUS_5")
                .add();
        vl5.newLoad()
                .setId("LOAD_5")
                .setBus("BUS_5")
                .setP0(2.)
                .setQ0(0.5)
                .add();

        Line line35 = network.newLine()
                .setId("LINE_35")
                .setVoltageLevel1("VL_3")
                .setVoltageLevel2("VL_5")
                .setBus1("BUS_3")
                .setBus2("BUS_5")
                .setR(0.5)
                .setX(1.0)
                .setG1(0.0000005)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
                .add();

        t3wt.getLeg2().getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(false)
                .setTapPosition(0)
                .setRegulationTerminal(line35.getTerminal2())
                .setTargetV(33.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(31.200, bus5);

        parameters.setTransformerVoltageControlOn(true);
        t3wt.getLeg2().getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(line35.getTerminal2())
                .setTargetV(33.0);

        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(31.200, bus5);
    }

    /**
     * A very small network to test with a T2wt.
     *
     *     G1        LD2
     *     |    L12   |
     *     |  ------- |
     *     B1         B2 - T2WT2 - B4 - L43 - B3
     *                  \                      \
     *                   T2WT - B5 - L53 - B3 - LD3
     */
    private Network createNetworkWithSharedControl() {

        Network network = VoltageControlNetworkFactory.createTransformerBaseNetwork("two-windings-transformer-control");

        VoltageLevel vl4 = network.getSubstation("SUBSTATION").newVoltageLevel()
                .setId("VL_4")
                .setNominalV(33.0)
                .setLowVoltageLimit(30.0)
                .setHighVoltageLimit(40.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl4.getBusBreakerView().newBus()
                .setId("BUS_4")
                .add();

        VoltageLevel vl5 = network.getSubstation("SUBSTATION").newVoltageLevel()
                .setId("VL_5")
                .setNominalV(33.0)
                .setLowVoltageLimit(30.0)
                .setHighVoltageLimit(40.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl5.getBusBreakerView().newBus()
                .setId("BUS_5")
                .add();

        Line line43 = network.newLine()
                .setId("LINE_43")
                .setVoltageLevel1("VL_4")
                .setVoltageLevel2("VL_3")
                .setBus1("BUS_4")
                .setBus2("BUS_3")
                .setR(0.)
                .setX(0.)
                .setG1(0.)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
                .add();

        Line line53 = network.newLine()
                .setId("LINE_53")
                .setVoltageLevel1("VL_5")
                .setVoltageLevel2("VL_3")
                .setBus1("BUS_5")
                .setBus2("BUS_3")
                .setR(0.)
                .setX(0.)
                .setG1(0.)
                .setG2(0.)
                .setB1(0.)
                .setB2(0.)
                .add();

        t2wt = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT")
                .setVoltageLevel1("VL_2")
                .setVoltageLevel2("VL_5")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_5")
                .add();

        t2wt.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(network.getLoad("LOAD_3").getTerminal())
                .add();

        t2wt2 = network.getSubstation("SUBSTATION").newTwoWindingsTransformer()
                .setId("T2wT2")
                .setVoltageLevel1("VL_2")
                .setVoltageLevel2("VL_4")
                .setRatedU1(132.0)
                .setRatedU2(33.0)
                .setR(17.0)
                .setX(10.0)
                .setG(0.00573921028466483)
                .setB(0.000573921028466483)
                .setBus1("BUS_2")
                .setBus2("BUS_4")
                .add();

        t2wt2.newRatioTapChanger()
                .beginStep()
                .setRho(0.9)
                .setR(0.1089)
                .setX(0.01089)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.0)
                .setR(0.121)
                .setX(0.0121)
                .setG(0.8264462809917356)
                .setB(0.08264462809917356)
                .endStep()
                .beginStep()
                .setRho(1.1)
                .setR(0.1331)
                .setX(0.01331)
                .setG(0.9090909090909092)
                .setB(0.09090909090909092)
                .endStep()
                .setTapPosition(0)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(false)
                .setTargetV(33.0)
                .setRegulationTerminal(network.getLoad("LOAD_3").getTerminal())
                .add();

        return network;
    }

    private void selectNetwork(Network network) {
        this.network = network;
        bus1 = network.getBusBreakerView().getBus("BUS_1");
        bus2 = network.getBusBreakerView().getBus("BUS_2");
        bus3 = network.getBusBreakerView().getBus("BUS_3");
        bus4 = network.getBusBreakerView().getBus("BUS_4");

        line12 = network.getLine("LINE_12");

        t2wt = network.getTwoWindingsTransformer("T2wT");
        t3wt = network.getThreeWindingsTransformer("T3wT");
    }
}
