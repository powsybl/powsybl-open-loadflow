/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
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
                .setUseReactiveLimits(false)
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
        LoadFlowParameters parameters = new LoadFlowParameters().setUseReactiveLimits(false)
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
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        Bus bus1 = network.getBusView().getBus("vl1_0");
        assertVoltageEquals(390.0, bus1);
    }

    @Test
    void testHvdcAcEmulation() {
        Network network = HvdcNetworkFactory.createVsc();
        network.getHvdcLine("hvdc23").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();
        network.newLine()
                .setId("l23")
                .setBus1("b2")
                .setBus2("b3")
                .setR(1)
                .setX(3)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(-4.9634, cs2.getTerminal());
        assertReactivePowerEquals(360.034, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(5.0286, cs3.getTerminal());
        assertReactivePowerEquals(226.984, cs3.getTerminal());
    }

    @Test
    void testHvdcAcEmulation2() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD).setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-0.114, cs3.getTerminal());
        assertReactivePowerEquals(-4.226, cs3.getTerminal());

        VscConverterStation cs4 = network.getVscConverterStation("cs4");
        assertActivePowerEquals(0.1166, cs4.getTerminal());
        assertReactivePowerEquals(-3.600, cs4.getTerminal());

        network.getVscConverterStation("cs3").setVoltageRegulatorOn(false);
        network.getVscConverterStation("cs4").setVoltageRegulatorOn(false);
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isOk());

        assertActivePowerEquals(-0.089, cs3.getTerminal());
        assertReactivePowerEquals(0.0, cs3.getTerminal());
        assertActivePowerEquals(0.0914, cs4.getTerminal());
        assertReactivePowerEquals(0.0, cs4.getTerminal());
    }

    @Test
    void testHvdcAcEmulationNonSupported() {
        Network network = HvdcNetworkFactory.createVsc();
        network.getHvdcLine("hvdc23").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        VscConverterStation cs2 = network.getVscConverterStation("cs2");
        assertActivePowerEquals(50.0, cs2.getTerminal());
        assertReactivePowerEquals(598.227, cs2.getTerminal());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-49.35, cs3.getTerminal());
        assertReactivePowerEquals(-10.0, cs3.getTerminal());
    }

    @Test
    void testHvdcAcEmulationNonSupported2() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD)
                .setDc(true)
                .setHvdcAcEmulation(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        VscConverterStation cs3 = network.getVscConverterStation("cs3");
        assertActivePowerEquals(-1.956, cs3.getTerminal());

        VscConverterStation cs4 = network.getVscConverterStation("cs4");
        assertActivePowerEquals(2.0, cs4.getTerminal());

    }
}
