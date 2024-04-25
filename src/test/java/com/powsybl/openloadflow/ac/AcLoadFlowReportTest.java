/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.MaxVoltageChangeStateVectorScaling;
import com.powsybl.openloadflow.ac.solver.StateVectorScalingMode;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
class AcLoadFlowReportTest {

    @Test
    void testEsgTutoDetailedNrLogsLf() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testEsgTutoReport", "Test ESG tutorial report")
                .build();
        var lfParameters = new LoadFlowParameters()
                .setTransformerVoltageControlOn(true);
        var olfParameters = OpenLoadFlowParameters.create(lfParameters);
        olfParameters.setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW))
                     .setTransformerVoltageControlMode(OpenLoadFlowParameters.TransformerVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL)
                     .setStateVectorScalingMode(StateVectorScalingMode.MAX_VOLTAGE_CHANGE)
                     .setMaxVoltageChangeStateVectorScalingMaxDv(MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DV / 10)
                     .setMaxVoltageChangeStateVectorScalingMaxDphi(MaxVoltageChangeStateVectorScaling.DEFAULT_MAX_DPHI / 10);

        LoadFlowProvider provider = new OpenLoadFlowProvider(new DenseMatrixFactory(), new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LoadFlow.Runner runner = new LoadFlow.Runner(provider);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reportNode);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        LoadFlowAssert.assertReportEquals("/esgTutoReportDetailedNrReportLf.txt", reportNode);
    }

    @Test
    void testShuntVoltageControlOuterLoopReport() throws IOException {
        Network network = ShuntNetworkFactory.createWithTwoShuntCompensators();
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        var lfParameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);
        var olfParameters = OpenLoadFlowParameters.create(lfParameters);
        olfParameters.setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW))
                .setStateVectorScalingMode(StateVectorScalingMode.LINE_SEARCH)
                .setShuntVoltageControlMode(OpenLoadFlowParameters.ShuntVoltageControlMode.INCREMENTAL_VOLTAGE_CONTROL);

        LoadFlowProvider provider = new OpenLoadFlowProvider(new DenseMatrixFactory(), new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LoadFlow.Runner runner = new LoadFlow.Runner(provider);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reportNode);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        LoadFlowAssert.assertReportEquals("/shuntVoltageControlOuterLoopReport.txt", reportNode);
    }

    @Test
    void testTransformerReactivePowerControlOuterLoopReport() throws IOException {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        var t2wt = network.getTwoWindingsTransformer("T2wT");
        t2wt.getRatioTapChanger()
                .setTargetDeadband(0)
                .setRegulating(true)
                .setTapPosition(0)
                .setRegulationTerminal(t2wt.getTerminal1())
                .setRegulationMode(RatioTapChanger.RegulationMode.REACTIVE_POWER)
                .setRegulationValue(-0.55);
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        var lfParameters = new LoadFlowParameters();
        var olfParameters = OpenLoadFlowParameters.create(lfParameters);
        olfParameters.setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW))
                .setTransformerReactivePowerControl(true);

        LoadFlowProvider provider = new OpenLoadFlowProvider(new DenseMatrixFactory(), new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LoadFlow.Runner runner = new LoadFlow.Runner(provider);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reportNode);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        LoadFlowAssert.assertReportEquals("/transformerReactivePowerControlOuterLoopReport.txt", reportNode);
    }

    @Test
    void test() throws IOException {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        // open everything at bus b4 to create 3 components
        network.getBusBreakerView().getBus("b4").getConnectedTerminalStream().forEach(Terminal::disconnect);

        // CC1 SC1 has generator but no voltage control. OK in DC, but KO in AC.
        network.getGenerator("g6").setTargetQ(0.0).setVoltageRegulatorOn(false);
        // CC2 SC2 has no generator connected. Ignored in for DC and AC.
        network.getGenerator("g10").disconnect();

        var lfParameters = new LoadFlowParameters().setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);

        LoadFlowProvider provider = new OpenLoadFlowProvider(new DenseMatrixFactory(), new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LoadFlow.Runner runner = new LoadFlow.Runner(provider);

        // test in AC
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reportNode);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.FAILED, result.getComponentResults().get(1).getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.NO_CALCULATION, result.getComponentResults().get(2).getStatus());
        LoadFlowAssert.assertReportEquals("/multipleConnectedComponentsAcReport.txt", reportNode);

        // test in DC
        lfParameters.setDc(true);
        reportNode = ReportNode.newRootReportNode()
                .withMessageTemplate("testReport", "Test Report")
                .build();
        result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reportNode);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(1).getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.NO_CALCULATION, result.getComponentResults().get(2).getStatus());
        LoadFlowAssert.assertReportEquals("/multipleConnectedComponentsDcReport.txt", reportNode);
    }
}
