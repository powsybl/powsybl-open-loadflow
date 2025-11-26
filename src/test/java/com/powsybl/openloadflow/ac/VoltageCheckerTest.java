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
import com.powsybl.commons.test.TestUtil;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.VoltageTargetCheck;
import com.powsybl.openloadflow.VoltageTargetChecker;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

class VoltageCheckerTest {

    @Test
    void testIncompatibleReport() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance();

        network.getGenerator("g1").setTargetV(400);
        network.getGenerator("g2").setTargetV(403);
        network.getGenerator("g3").setTargetV(407);
        VoltageTargetCheck.Result result = VoltageTargetChecker.findElementsToDiscardFromVoltageControl(network, new LoadFlowParameters());
        List<VoltageTargetCheck.IncompatibleTargetResolution> incompatibleTargetResolutions = result.incompatibleTargetResolutions();
        VoltageTargetCheck.IncompatibleTargetResolution incompatibleTargetResolution1 = incompatibleTargetResolutions.get(0);
        assertEquals("vl4_0", incompatibleTargetResolution1.controlledBusToFixId());
        assertEquals(Set.of("g1"), incompatibleTargetResolution1.elementsToDisableIds());
        String otherBusId = incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus1Id().equals("vl4_0") ?
                incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus2Id()
                :
                incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus1Id();
        assertEquals("vl4_2", otherBusId);
        // For low impedance at high nominal voltage (short lines), the double precision limit is reached in the AdmittanceMatrix formula for getZ. We just get the information that z is small...
        assertTrue(incompatibleTargetResolution1.largestIncompatibleTarget().targetVoltagePlausibilityIndicator() > 900);

        VoltageTargetCheck.IncompatibleTargetResolution incompatibleTargetResolution2 = incompatibleTargetResolutions.get(1);
        assertEquals("vl4_1", incompatibleTargetResolution2.controlledBusToFixId());
        assertEquals(Set.of("g2"), incompatibleTargetResolution2.elementsToDisableIds());
        otherBusId = incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus1Id().equals("vl4_1") ?
                incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus2Id()
                :
                incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus1Id();
        assertEquals("vl4_2", otherBusId);
        // For low impedance at high nominal voltage (short lines), the double precision limit is reached in the AdmittanceMatrix formula for getZ. We just get the information that z is small...
        assertTrue(incompatibleTargetResolution2.largestIncompatibleTarget().targetVoltagePlausibilityIndicator() > 400);
    }

    @Test
    void testAutomaticFix() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance();
        network.getGenerator("g1").setTargetV(400);
        network.getGenerator("g2").setTargetV(403);
        network.getGenerator("g3").setTargetV(407);

        OpenLoadFlowProvider runner = new OpenLoadFlowProvider();
        LoadFlowRunParameters runParameters = new LoadFlowRunParameters();
        LoadFlowParameters params = new LoadFlowParameters();
        runParameters.setParameters(params);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), runParameters).join();
        assertEquals(LoadFlowResult.Status.FAILED, result.getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        OpenLoadFlowParameters.create(params).setFixVoltageTargets(true);
        result = runner.run(network, network.getVariantManager().getWorkingVariantId(), runParameters).join();
        assertEquals(LoadFlowResult.Status.FULLY_CONVERGED, result.getStatus());

        // g1 and g2 have been disabled.
        assertEquals(407, network.getGenerator("g3").getRegulatingTerminal().getBusView().getBus().getV(), 0.001);
        assertEquals(406.995, network.getGenerator("g1").getRegulatingTerminal().getBusView().getBus().getV(), 0.001);
        assertEquals(406.995, network.getGenerator("g2").getRegulatingTerminal().getBusView().getBus().getV(), 0.001);
    }

    @Test
    void testAutomaticFixReports() throws IOException {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance();
        network.getGenerator("g1").setTargetV(400);
        network.getGenerator("g2").setTargetV(403);
        network.getGenerator("g3").setTargetV(407);

        // Add a PV node on the load
        network.getVoltageLevel("vl5").newGenerator()
                .setId("g5")
                .setTargetP(1)
                .setMinP(0)
                .setMaxP(10)
                .setVoltageRegulatorOn(true)
                .setTargetV(403)
                .setBus("b5")
                .setConnectableBus("b5")
                .add();

        OpenLoadFlowProvider runner = new OpenLoadFlowProvider();
        LoadFlowRunParameters runParameters = new LoadFlowRunParameters();
        LoadFlowParameters params = new LoadFlowParameters();
        runParameters.setParameters(params);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), runParameters).join();
        assertEquals(LoadFlowResult.Status.FAILED, result.getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        ReportNode testReport = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test")
                .build();
        runParameters.setReportNode(testReport);
        OpenLoadFlowParameters.create(params).setFixVoltageTargets(true);
        result = runner.run(network, network.getVariantManager().getWorkingVariantId(), runParameters).join();
        assertEquals(LoadFlowResult.Status.FULLY_CONVERGED, result.getStatus());

        // For high values, indicator values is hardware sensitive (based on small differences between large numbers)
        // So we remove them from the tests
        String reportString = TestUtil.normalizeLineSeparator(LoadFlowAssert.reportToString(testReport).replaceAll("indicator:.*\\)", "indicator: ***)"));
        // Also strongest incompatibility with vl4_0 can be vl4_2 or vl5_0 depending on incompatibility factor numerical errors
        reportString = reportString.replaceAll("and 'vl4_2' have incompatible", "and '***' have incompatible");
        reportString = reportString.replaceAll("and 'vl5_0' have incompatible", "and '***' have incompatible");

        // Even the display order (sorted in plausibility indicator) is different between architectures ! SO lets check expected sentences alone
        assertTrue(reportString.contains("         + Checking voltage targets"));
        assertTrue(reportString.contains("           Controlled buses 'vl4_0' and '***' have incompatible voltage targets (plausibility indicator: ***): disabling controller elements [vl1_0]"));
        assertTrue(reportString.contains("           Controlled buses 'vl4_2' and '***' have incompatible voltage targets (plausibility indicator: ***): disabling controller elements [vl3_0]"));
    }
}
