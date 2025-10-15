/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.DistributedSlackNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class FictitiousInjectionTest {

    @Test
    void testFictiveInjection() throws IOException {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        network.getBusBreakerView().getBus("b3").setFictitiousP0(29);
        network.getBusBreakerView().getBus("b2").setFictitiousP0(1);
        network.getBusBreakerView().getBus("b1").setFictitiousQ0(50);
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowRunParameters parameters = new LoadFlowRunParameters();
        parameters.setReportNode(ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test").build());
        LoadFlowResult result = runner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        assertEquals(-30, result.getComponentResults().getFirst().getDistributedActivePower(), LoadFlowAssert.DELTA_POWER);
        LoadFlowAssert.assertReactivePowerEquals(-(network.getGenerator("g1").getTerminal().getQ() + 50 + 30),
                network.getLine("l14").getTerminal1()); // generator targetQ + fictitious injection ° Q(l1)
        LoadFlowAssert.assertReactivePowerEquals(-155.088, network.getGenerator("g1").getTerminal());

        String expected = """
                + test
                   + Load flow on network 'distributed-load-slack-bus'
                      + Network CC0 SC0
                         + Network info
                            Network has 4 buses and 3 branches
                            Network balance: active generation=300 MW, active load=270 MW, reactive generation=300 MVar, reactive load=455 MVar
                            Angle reference bus: b4_vl_0
                            Slack bus: b4_vl_0
                            Network has a cumulated sum of 30 MW and 50 MVar fictitious injection distributed on 3 buses
                         + Outer loop DistributedSlack
                            + Outer loop iteration 1
                               Slack bus active power (-30 MW) distributed in 1 distribution iteration(s)
                         Outer loop VoltageMonitoring
                         Outer loop ReactiveLimits
                         Outer loop DistributedSlack
                         Outer loop VoltageMonitoring
                         Outer loop ReactiveLimits
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """;

        LoadFlowAssert.assertReportEquals(new ByteArrayInputStream(expected.getBytes()), parameters.getReportNode());
    }

    @Test
    void testNoFictiveInjection() throws IOException {
        Network network = DistributedSlackNetworkFactory.createNetworkWithLoads();
        LoadFlow.Runner runner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowRunParameters parameters = new LoadFlowRunParameters();
        parameters.setReportNode(ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test").build());
        LoadFlowResult result = runner.run(network, parameters);

        assertTrue(result.isFullyConverged());

        assertEquals(-60, result.getComponentResults().getFirst().getDistributedActivePower(), LoadFlowAssert.DELTA_POWER);
        LoadFlowAssert.assertReactivePowerEquals(-105.08, network.getGenerator("g1").getTerminal());

        // No ficitious bus report
        String expected = """
                + test
                   + Load flow on network 'distributed-load-slack-bus'
                      + Network CC0 SC0
                         + Network info
                            Network has 4 buses and 3 branches
                            Network balance: active generation=300 MW, active load=240 MW, reactive generation=300 MVar, reactive load=405 MVar
                            Angle reference bus: b4_vl_0
                            Slack bus: b4_vl_0
                         + Outer loop DistributedSlack
                            + Outer loop iteration 1
                               Slack bus active power (-60 MW) distributed in 1 distribution iteration(s)
                         Outer loop VoltageMonitoring
                         Outer loop ReactiveLimits
                         Outer loop DistributedSlack
                         Outer loop VoltageMonitoring
                         Outer loop ReactiveLimits
                         AC load flow completed successfully (solverStatus=CONVERGED, outerloopStatus=STABLE)
                """;

        LoadFlowAssert.assertReportEquals(new ByteArrayInputStream(expected.getBytes()), parameters.getReportNode());
    }
}
