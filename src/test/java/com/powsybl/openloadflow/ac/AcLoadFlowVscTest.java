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
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390, bus1);
        assertAngleEquals(0, bus1);

        Bus bus2 = network.getBusView().getBus("vl2_0");
        assertVoltageEquals(385, bus2);
        assertAngleEquals(0.117616, bus2);

        Bus bus3 = network.getBusView().getBus("vl3_0");
        assertVoltageEquals(383, bus3);
        assertAngleEquals(0, bus3);

        Generator g1 = network.getGenerator("g1");
        assertActivePowerEquals(-102.56, g1.getTerminal());
        assertReactivePowerEquals(-615.918, g1.getTerminal());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(50.00, cs2.getTerminal());
        assertReactivePowerEquals(598.228, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-49.35, cs3.getTerminal());
        assertReactivePowerEquals(-10.0, cs3.getTerminal());

        Line l12 = network.getLine("l12");
        assertActivePowerEquals(102.563, l12.getTerminal1());
        assertReactivePowerEquals(615.918, l12.getTerminal1());
        assertActivePowerEquals(-99.999, l12.getTerminal2());
        assertReactivePowerEquals(-608.228, l12.getTerminal2());
    }

    @Test
    void testRegulatingTerminal() {
        Network network = HvdcNetworkFactory.createVsc();
        network.getGenerator("g1").setTargetQ(50).setVoltageRegulatorOn(false);
        VscConverterStation vscConverterStation = network.getVscConverterStation("cs2");
        vscConverterStation.setRegulatingTerminal(network.getGenerator("g1").getTerminal()).setVoltageSetpoint(390);
        vscConverterStation.setVoltageRegulatorOn(true); //FIXME

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390.0, bus1);
    }

    @Test
    void testRegulatingTerminal2() {
        Network network = HvdcNetworkFactory.createVsc();
        network.getGenerator("g1").setTargetV(390);
        VscConverterStation vscConverterStation = network.getVscConverterStation("cs2");
        vscConverterStation.setRegulatingTerminal(network.getVscConverterStation("cs3").getTerminal()).setVoltageSetpoint(400); // will be discarded.
        vscConverterStation.setVoltageRegulatorOn(true); //FIXME

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390.0, bus1);
    }
}
