/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControl.PilotPoint;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControl.Zone;
import com.powsybl.openloadflow.network.impl.extensions.SecondaryVoltageControlAdder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SecondaryVoltageControlTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true);
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void test() {
        Network network = IeeeCdfNetworkFactory.create14();
        PilotPoint pilotPoint = new PilotPoint("B10", 15);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addZone(new Zone("z1", pilotPoint, List.of("B1-G", "B2-G", "B3-G", "B6-G")))
                .add();
        Bus b1 = network.getBusBreakerView().getBus("B1");
        Bus b2 = network.getBusBreakerView().getBus("B2");
        Bus b3 = network.getBusBreakerView().getBus("B3");
        Bus b6 = network.getBusBreakerView().getBus("B6");
        Bus b10 = network.getBusBreakerView().getBus("B10");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(3, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14.262, b10);
        assertVoltageEquals(143.1, b1);
        assertVoltageEquals(141.075, b2);
        assertVoltageEquals(136.35, b3);
        assertVoltageEquals(12.84, b6);

        parametersExt.setSecondaryVoltageControl(true);

        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(6, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(15, b10);
        assertVoltageEquals(179.099, b1);
        assertVoltageEquals(152.352, b2);
        assertVoltageEquals(155.101, b3);
        assertVoltageEquals(13.147, b6);

        pilotPoint.setTargetV(14);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(5, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(14, b10);
        assertVoltageEquals(130.326, b1);
        assertVoltageEquals(137.073, b2);
        assertVoltageEquals(129.696, b3);
        assertVoltageEquals(12.73, b6);
    }

    @Test
    void multiZonesTest() {
        Network network = IeeeCdfNetworkFactory.create14();
        PilotPoint pilotPoint1 = new PilotPoint("B4", 142);
        PilotPoint pilotPoint2 = new PilotPoint("B10", 14.5);
        network.newExtension(SecondaryVoltageControlAdder.class)
                .addZone(new Zone("z1", pilotPoint1, List.of("B1-G", "B2-G", "B3-G")))
                .addZone(new Zone("z2", pilotPoint2, List.of("B6-G", "B8-G")))
                .add();

        Bus b4 = network.getBusBreakerView().getBus("B4");
        Bus b10 = network.getBusBreakerView().getBus("B10");

        parametersExt.setSecondaryVoltageControl(true);
        var result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(9, result.getComponentResults().get(0).getIterationCount());
        assertVoltageEquals(142, b4);
        assertVoltageEquals(14.5, b10);
    }
}
