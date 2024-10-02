/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro.done;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.LinesWithDifferentNominalVoltagesNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @see LinesWithDifferentNominalVoltagesNetworkFactory
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LinesWithDifferentNominalVoltagesTest {

    private Network network;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;

    private Bus b225g;
    private Bus b225l;
    private Bus b220A;
    private Bus b230A;
    private Bus b220B;
    private Bus b230B;
    private Line l225to225;
    private Line l225to230;
    private Line l225to220;
    private Line l230to225;
    private Line l220to225;

    @BeforeEach
    void setUp() {
        network = LinesWithDifferentNominalVoltagesNetworkFactory.create();

        b225g = network.getBusBreakerView().getBus("b225g");
        b225l = network.getBusBreakerView().getBus("b225l");
        b220A = network.getBusBreakerView().getBus("b220A");
        b230A = network.getBusBreakerView().getBus("b230A");
        b220B = network.getBusBreakerView().getBus("b220B");
        b230B = network.getBusBreakerView().getBus("b230B");
        l225to225 = network.getLine("l225-225");
        l225to230 = network.getLine("l225-230");
        l225to220 = network.getLine("l225-220");
        l230to225 = network.getLine("l230-225");
        l220to225 = network.getLine("l220-225");

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters().setDistributedSlack(true);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.LARGEST_GENERATOR)
                .setAcSolverType(AcSolverType.KNITRO);
    }

    @Test
    void test() {

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertEquals(1, result.getComponentResults().size());

        // slack bus
        assertVoltageEquals(225.0, b225g);
        assertAngleEquals(0.0, b225g);

        // load buses
        List.of(b225l, b220A, b230A, b220B, b230B).forEach(bus -> {
            assertVoltageEquals(218.294, bus);
            assertAngleEquals(-3.4606395, bus);
        });

        // line flows, load side
        List.of(
            l225to225.getTerminal(TwoSides.TWO),
            l225to220.getTerminal(TwoSides.TWO),
            l225to230.getTerminal(TwoSides.TWO),
            l220to225.getTerminal(TwoSides.ONE),
            l230to225.getTerminal(TwoSides.ONE)).forEach(terminal -> {
                assertActivePowerEquals(-100, terminal);
                assertReactivePowerEquals(-40, terminal);
            });

        // line flows, generator side
        List.of(
            l225to225.getTerminal(TwoSides.ONE),
            l225to220.getTerminal(TwoSides.ONE),
            l225to230.getTerminal(TwoSides.ONE),
            l220to225.getTerminal(TwoSides.TWO),
            l230to225.getTerminal(TwoSides.TWO)).forEach(terminal -> {
                assertActivePowerEquals(103.444104, terminal);
                assertReactivePowerEquals(45.4712006, terminal);
            });
    }

}
