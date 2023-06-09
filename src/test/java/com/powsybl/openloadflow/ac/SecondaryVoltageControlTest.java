/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.ControlUnit;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.ControlZone;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl.PilotPoint;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SecondaryVoltageControlTest {

    private Network network;

    private Bus b4;
    private Bus b6;
    private Bus b8;
    private Bus b10;
    private Generator g6;
    private Generator g8;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        b4 = network.getBusBreakerView().getBus("B4");
        b6 = network.getBusBreakerView().getBus("B6");
        b8 = network.getBusBreakerView().getBus("B8");
        b10 = network.getBusBreakerView().getBus("B10");
        g6 = network.getGenerator("B6-G");
        g8 = network.getGenerator("B8-G");

        // adjust reactive limit to avoid generators be to limit
        g8.newMinMaxReactiveLimits()
                .setMinQ(-6)
                .setMaxQ(200)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setMaxPlausibleTargetVoltage(1.4);
    }

    @Test
    void testNoReactiveLimits() {
        parameters.setUseReactiveLimits(false);
        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14.4);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B6-G"),
                                                                          new ControlUnit("B8-G"))))
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.611, b10);
        assertVoltageEquals(12.84, b6);
        assertVoltageEquals(21.8, b8);
        double q6 = g6.getTerminal().getQ();
        double q8 = g8.getTerminal().getQ();

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.4, b10);
        assertVoltageEquals(14.755, b6);
        assertVoltageEquals(26.611, b8);
        // not so bad... reactive power shift are closed
        assertEquals(56.357, q6 - g6.getTerminal().getQ(), DELTA_POWER);
        assertEquals(71.988, q8 - g8.getTerminal().getQ(), DELTA_POWER);

        pilotPoint.setTargetV(14);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14, b10);
        assertVoltageEquals(14.326, b6);
        assertVoltageEquals(25.534, b8);
        // not so bad... reactive power shift are closed
        assertEquals(42.369, q6 - g6.getTerminal().getQ(), DELTA_POWER);
        assertEquals(53.695, q8 - g8.getTerminal().getQ(), DELTA_POWER);
    }

    @Test
    void testReactiveLimits() {
        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B6-G"),
                                                                          new ControlUnit("B8-G"))))
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(12.606, b10);
        assertVoltageEquals(12.84, b6);
        assertVoltageEquals(21.8, b8);
        assertReactivePowerEquals(-12.730, g6.getTerminal()); // [-6, 24]
        assertReactivePowerEquals(-17.623, g8.getTerminal()); // [-6, 200]

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(9, result.getComponentResults().get(0).getIterationCount());

        assertVoltageEquals(14, b10);
        assertVoltageEquals(13.92, b6);
        assertVoltageEquals(26.9, b8);
        assertReactivePowerEquals(-24, g6.getTerminal()); // [-6, 24] => qmax
        assertReactivePowerEquals(-109.141, g8.getTerminal()); // [-6, 200]
    }

    @Test
    void multiNoReactiveLimitsZonesTest() {
        parameters.setUseReactiveLimits(false);
        PilotPoint pilotPoint1 = new PilotPoint(List.of("B4"), 142);
        PilotPoint pilotPoint2 = new PilotPoint(List.of("B10"), 14.5);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint1, List.of(new ControlUnit("B1-G"), new ControlUnit("B2-G"), new ControlUnit("B3-G"))))
                .addControlZone(new ControlZone("z2", pilotPoint2, List.of(new ControlUnit("B6-G"), new ControlUnit("B8-G"))))
                .add();

        parametersExt.setSecondaryVoltageControl(true);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(10, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(142, b4);
        assertVoltageEquals(14.487, b10);
    }

    @Test
    void testOpenBranchIssue() {
        // open branch L6-13-1 on side 2 so that a neighbor branch of B6-G is disconnected to the other side
        network.getLine("L6-13-1").getTerminal2().disconnect();

        parameters.setUseReactiveLimits(false);
        parametersExt.setSecondaryVoltageControl(true);

        PilotPoint pilotPoint = new PilotPoint(List.of("B10"), 14.4);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addControlZone(new ControlZone("z1", pilotPoint, List.of(new ControlUnit("B6-G"),
                                                                          new ControlUnit("B8-G"))))
                .add();

        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.38, b10);
        assertVoltageEquals(14.86, b6);
        assertVoltageEquals(26.74, b8);
    }
}
