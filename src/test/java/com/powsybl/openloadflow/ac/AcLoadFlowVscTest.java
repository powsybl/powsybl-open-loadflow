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
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlowVscTest {

    @Test
    void test() {
        Network network = HvdcNetworkFactory.createVsc();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);

        Bus bus2 = network.getBusView().getBus("vl2_0");
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.116917, bus2);

        Bus bus3 = network.getBusView().getBus("vl3_0");
        assertVoltageEquals(383, bus3);
        assertAngleEquals(0, bus3);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-102.56, g1.getTerminal());
        assertReactivePowerEquals(-615.733, g1.getTerminal());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(50.55, cs2.getTerminal());
        assertReactivePowerEquals(598.046, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-49.90, cs3.getTerminal());
        assertReactivePowerEquals(-10.0, cs3.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(103.112, l12.getTerminal1());
        assertReactivePowerEquals(615.733, l12.getTerminal1());
        assertActivePowerEquals(-100.55, l12.getTerminal2());
        assertReactivePowerEquals(-608.046, l12.getTerminal2());
    }

    @Test
    public void testRegulatingTerminal() {
        Network network = HvdcNetworkFactory.createNetworkWithGenerators2();
        VscConverterStation vscConverterStation = network.getVscConverterStation("cs4");
        vscConverterStation.setRegulatingTerminal(network.getGenerator("g5")
                .getTerminal()).setVoltageSetpoint(1.2);
        vscConverterStation.setVoltageRegulatorOn(true);

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("b1_vl_0");
        assertVoltageEquals(1.0, bus1);

        Bus bus2 = network.getBusView().getBus("b2_vl_0");
        assertVoltageEquals(1.0, bus2);

        Bus bus3 = network.getBusView().getBus("b3_vl_0");
        assertVoltageEquals(1.2, bus3);

        Bus bus4 = network.getBusView().getBus("b4_vl_0");
        assertVoltageEquals(1.26, bus4);

        Bus bus5 = network.getBusView().getBus("b5_vl_0");
        assertVoltageEquals(1.2, bus5);

        Bus bus6 = network.getBusView().getBus("b6_vl_0");
        assertVoltageEquals(1.0, bus6);

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-1.978, cs3.getTerminal());
        assertReactivePowerEquals(-4.928, cs3.getTerminal());
        Line l12 = network.getLine("l12");
        assertActivePowerEquals(1.244, l12.getTerminal1());
        assertReactivePowerEquals(0.078, l12.getTerminal1());
        assertActivePowerEquals(-1.244, l12.getTerminal2());
        assertReactivePowerEquals(0.078, l12.getTerminal2());
    }
}
