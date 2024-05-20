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
//import com.powsybl.openloadflow.ac.solver.AcSolverResult;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
//import com.powsybl.openloadflow.ac.solver.KnitroSolver;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
//import java.util.stream.Collectors;

//import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
//import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class KnitroSolverTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        // Utilisation de Knitro comme solver + défintion du slack bus
        OpenLoadFlowParameters.create(parameters).setAcSolverType(AcSolverType.KNITRO);
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

        // Pas d'OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
//        parameters.getExtension(OpenLoadFlowParameters.class)
//                .setSvcVoltageMonitoring(false);

        // Utilisation de Knitro comme solver + défintion du slack bus
        OpenLoadFlowParameters.create(parameters).setAcSolverType(AcSolverType.KNITRO).setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        LoadFlowResult KNresult = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, KNresult.getComponentResults().get(0).getStatus());
        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325965, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832329, bus2);
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

        // check pv bus reactive power update
        assertReactivePowerEquals(-225.279, gen.getTerminal());
    }

    @Test
    void knitroSolverTest4bus() {
        Network network = FourBusNetworkFactory.createWithCondenser();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        Bus b2 = network.getBusBreakerView().getBus("b2");
        Bus b3 = network.getBusBreakerView().getBus("b3");
        List<Bus> busList = network.getBusView().getBusStream().toList();

        // Pas d'OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
        parameters.getExtension(OpenLoadFlowParameters.class)
                .setSvcVoltageMonitoring(false);


        LoadFlowResult KNresult = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, KNresult.getComponentResults().get(0).getStatus());
        assertTrue(KNresult.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-2.584977, b4);
    }

}
