/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class KnitroSolverTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAcSolverType(AcSolverType.KNITRO);
        // No OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
        parameters.getExtension(OpenLoadFlowParameters.class)
                .setSvcVoltageMonitoring(false);
        // Sparse matrix solver only
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    @Test
    void knitroSolverTestEurostag() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Bus genBus = network.getBusBreakerView().getBus("NGEN");
        Bus bus1 = network.getBusBreakerView().getBus("NHV1");
        Bus bus2 = network.getBusBreakerView().getBus("NHV2");
        Bus loadBus = network.getBusBreakerView().getBus("NLOAD");
        Line line1 = network.getLine("NHV1_NHV2_1");
        Line line2 = network.getLine("NHV1_NHV2_2");
        Generator gen = network.getGenerator("GEN");
        VoltageLevel vlgen = network.getVoltageLevel("VLGEN");
        VoltageLevel vlload = network.getVoltageLevel("VLLOAD");
        VoltageLevel vlhv1 = network.getVoltageLevel("VLHV1");
        VoltageLevel vlhv2 = network.getVoltageLevel("VLHV2");

        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325965, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832323, bus2);
        assertVoltageEquals(147.578, loadBus);
        assertAngleEquals(-11.940451, loadBus);
        assertActivePowerEquals(302.444, line1.getTerminal1());
        assertReactivePowerEquals(98.74, line1.getTerminal1());
        assertActivePowerEquals(-300.434, line1.getTerminal2());
        assertReactivePowerEquals(-137.188, line1.getTerminal2());
        assertActivePowerEquals(302.444, line2.getTerminal1());
        assertReactivePowerEquals(98.74, line2.getTerminal1());
        assertActivePowerEquals(-300.434, line2.getTerminal2());
        assertReactivePowerEquals(-137.188, line2.getTerminal2());
        assertReactivePowerEquals(-225.282, gen.getTerminal());
    }

    @Test
    void knitroSolverTest4busWithCondenser() {
        Network network = FourBusNetworkFactory.createWithCondenser();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        List<Bus> busList = network.getBusView().getBusStream().toList();
//        Generator generator = network.getGenerator("g1");
//        generator.newMinMaxReactiveLimits().setMinQ(1.0);
//        generator.newMinMaxReactiveLimits().setMaxQ(0.5);
//        b1.getGeneratorStream().toList().get(0).newMinMaxReactiveLimits().setMinQ(1.0);
//        network.getGenerator("g1").getTerminal().setQ(0.9);

        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
        assertTrue(knitroResult.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-6.531907, b4);
    }
}
