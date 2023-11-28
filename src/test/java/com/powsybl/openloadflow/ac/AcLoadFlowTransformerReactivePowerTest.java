/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
class AcLoadFlowTransformerReactivePowerTest {

    private Network network;
    private TwoWindingsTransformer t2wt;
    private TwoWindingsTransformer t2wt2;
    private ThreeWindingsTransformer t3wt;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setTransformerVoltageControlOn(false);
        parameters.setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
    }

    @Test
    void baseCaseT2wtTestReactivePower() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertReactivePowerEquals(7.618, network.getLine("LINE_12").getTerminal1());
        assertReactivePowerEquals(-7.318, network.getLine("LINE_12").getTerminal2());
        assertReactivePowerEquals(-0.181, t2wt.getTerminal1());
        assertReactivePowerEquals(3.205e-5, t2wt.getTerminal2());
    }

    @Test
    void tapPlusTwoT2wtTestReactivePower() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        t2wt.getRatioTapChanger().setTapPosition(3);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertReactivePowerEquals(7.285, network.getLine("LINE_12").getTerminal1());
        assertReactivePowerEquals(-6.927, network.getLine("LINE_12").getTerminal2());
        assertReactivePowerEquals(-0.573, t2wt.getTerminal1());
        assertReactivePowerEquals(5.170e-5, t2wt.getTerminal2());
    }

    @Test
    void reactivePowerControlT2wtTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        parameters.setTransformerReactivePowerControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(-0.55);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(7.285, network.getLine("LINE_12").getTerminal1());
        assertReactivePowerEquals(-6.927, network.getLine("LINE_12").getTerminal2());
        assertReactivePowerEquals(-0.573, t2wt.getTerminal1());
        assertReactivePowerEquals(5.170e-5, t2wt.getTerminal2());
        assertEquals(3, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void reactivePowerControlT2wtTest2() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        parameters.setTransformerReactivePowerControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(network.getLine("LINE_12").getTerminal1())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(7.6);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(7.618, network.getLine("LINE_12").getTerminal1());
        assertReactivePowerEquals(-7.318, network.getLine("LINE_12").getTerminal2());
        assertReactivePowerEquals(-0.181, t2wt.getTerminal1());
        assertReactivePowerEquals(3.205e-5, t2wt.getTerminal2());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void reactivePowerControlT2wtTest3() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());

        parameters.setTransformerReactivePowerControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(1)
                .setRegulationTerminal(network.getLine("LINE_12").getTerminal2())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(-7.3);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertReactivePowerEquals(7.618, network.getLine("LINE_12").getTerminal1());
        assertReactivePowerEquals(-7.318, network.getLine("LINE_12").getTerminal2());
        assertReactivePowerEquals(-0.181, t2wt.getTerminal1());
        assertReactivePowerEquals(3.205e-5, t2wt.getTerminal2());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void openControllerTransformerReactivePowerTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());
        parameters.setTransformerReactivePowerControlOn(true);

        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(-0.55);

        // no transformer reactive power control if terminal 2 is opened
        t2wt.getTerminal2().disconnect();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());

        // TODO : add case when side 2 is controlled and side 1 is disconnected
    }

    @Test
    void openControlledTransformerReactivePowerTest() {
        selectNetwork2(VoltageControlNetworkFactory.createNetworkWith2T2wt());
        parameters.setTransformerReactivePowerControlOn(true);

        // TODO : change values of the control
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt2.getTerminal1())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(-0.55);

        // no transformer reactive power control if terminal 2 is opened on controlled branch
        t2wt2.getTerminal2().disconnect();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());

        // no transformer reactive power control if terminal 1 is opened on controlled branch
        t2wt2.getTerminal2().connect();
        t2wt.getRatioTapChanger().setRegulationTerminal(t2wt2.getTerminal2());
        t2wt2.getTerminal1().disconnect();
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(0, t2wt.getRatioTapChanger().getTapPosition());
    }

    @Test
    void regulatingTerminalDisconnectedTransformerReactivePowerControlTest() {
        selectNetwork(VoltageControlNetworkFactory.createNetworkWithT2wt());
        Load load = network.getLoad("LOAD_2");
        load.getTerminal().disconnect();

        parameters.setTransformerReactivePowerControlOn(true);
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(2)
                .setRegulationTerminal(load.getTerminal())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(33.0);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertEquals(2, t2wt.getRatioTapChanger().getTapPosition());
    }

    private void selectNetwork(Network network) {
        this.network = network;

        t2wt = network.getTwoWindingsTransformer("T2wT");
        t3wt = network.getThreeWindingsTransformer("T3wT");
    }

    private void selectNetwork2(Network network) {
        this.network = network;

        t2wt = network.getTwoWindingsTransformer("T2wT1");
        t2wt2 = network.getTwoWindingsTransformer("T2wT2");
    }

    // TODO : add case when two transformer reactive power controls are on same controlled branch

}
