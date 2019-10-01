/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.loadflow.simple.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.simple.SimpleLoadFlowParameters;
import com.powsybl.loadflow.simple.SimpleLoadFlowProvider;
import com.powsybl.loadflow.simple.SlackBusSelectionMode;
import com.powsybl.math.matrix.DenseMatrixFactory;
import org.junit.Before;
import org.junit.Test;

import static com.powsybl.loadflow.simple.util.LoadFlowAssert.*;
import static org.junit.Assert.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowPhaseShifterTest {

    private Network network;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private Line line1;
    private Line line2;
    private TwoWindingsTransformer ps1;

    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    @Before
    public void setUp() {
        network = PhaseShifterTestCaseFactory.create();
        bus1 = network.getBusBreakerView().getBus("B1");
        bus2 = network.getBusBreakerView().getBus("B2");
        bus3 = network.getBusBreakerView().getBus("B3");

        line1 = network.getLine("L1");
        line2 = network.getLine("L2");
        ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);

        loadFlowRunner = new LoadFlow.Runner(new SimpleLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        SimpleLoadFlowParameters parametersExt = new SimpleLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setDistributedSlack(false);
        this.parameters.addExtension(SimpleLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void baseCaseTest() {
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
    public void tapPlusOneTest() {
        ps1.getPhaseTapChanger().setTapPosition(2);

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
}
