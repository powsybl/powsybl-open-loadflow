package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.commons.test.TestUtil;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.RemoteVoltageTarget;
import com.powsybl.openloadflow.RemoteVoltageTargetChecker;
import com.powsybl.openloadflow.network.VoltageControlNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class RemoteVoltageCheckerTest {

    @Test
    void testIncompatibleReport() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance();

        network.getGenerator("g1").setTargetV(400);
        network.getGenerator("g2").setTargetV(403);
        network.getGenerator("g3").setTargetV(407);
        RemoteVoltageTarget.Result result = RemoteVoltageTargetChecker.findElementsToDiscardFromVoltageControl(network, new LoadFlowParameters());
        List<RemoteVoltageTarget.IncompatibleTargetResolution> incompatibleTargetResolutions = result.incompatibleTargetResolutions();
        RemoteVoltageTarget.IncompatibleTargetResolution incompatibleTargetResolution1 = incompatibleTargetResolutions.get(0);
        assertEquals("vl4_0", incompatibleTargetResolution1.controlledBusToFixId());
        assertEquals(Set.of("g1"), incompatibleTargetResolution1.elementsToDisableIds());
        String otherBusId = incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus1Id().equals("vl4_0") ?
                incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus2Id()
                :
                incompatibleTargetResolution1.largestIncompatibleTarget().controlledBus1Id();
        assertEquals("vl4_2", otherBusId);
        // For low impedance at high nominal voltage (short lines), the double precision limit is reached in the AdmittanceMatrix formula for getZ. We just get the information that z is small...
        assertTrue(incompatibleTargetResolution1.largestIncompatibleTarget().targetVoltagePlausibilityIndicator() > 900);

        RemoteVoltageTarget.IncompatibleTargetResolution incompatibleTargetResolution2 = incompatibleTargetResolutions.get(1);
        assertEquals("vl4_1", incompatibleTargetResolution2.controlledBusToFixId());
        assertEquals(Set.of("g2"), incompatibleTargetResolution2.elementsToDisableIds());
        otherBusId = incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus1Id().equals("vl4_1") ?
                incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus2Id()
                :
                incompatibleTargetResolution2.largestIncompatibleTarget().controlledBus1Id();
        assertEquals("vl4_2", otherBusId);
        // For low impedance at high nominal voltage (short lines), the double precision limit is reached in the AdmittanceMatrix formula for getZ. We just get the information that z is small...
        assertTrue(incompatibleTargetResolution2.largestIncompatibleTarget().targetVoltagePlausibilityIndicator() > 400);

        assertEquals(2, result.unrealisticTargets().size());

        assertEquals("g2", result.unrealisticTargets().get(0).generatorIds().get(0));
        assertEquals(18, result.unrealisticTargets().get(0).estimatedDvController(), 1);

        assertEquals("g3", result.unrealisticTargets().get(1).generatorIds().get(0));
        assertEquals(62, result.unrealisticTargets().get(1).estimatedDvController(), 1);
    }

    @Test
    void testAutomaticFix() {
        Network network = VoltageControlNetworkFactory.createWithGeneratorRemoteControlAndSmallSeparatingImpedance();
        network.getGenerator("g1").setTargetV(400);
        network.getGenerator("g2").setTargetV(403);
        network.getGenerator("g3").setTargetV(407);

        OpenLoadFlowProvider runner = new OpenLoadFlowProvider();
        LoadFlowParameters params = new LoadFlowParameters();
        LoadFlowResult result = runner.run(network, LocalComputationManager.getDefault(), network.getVariantManager().getWorkingVariantId(), params, ReportNode.NO_OP).join();
        assertEquals(LoadFlowResult.Status.FAILED, result.getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        OpenLoadFlowParameters.create(params).setFixRemoteVoltageTarget(true);
        result = runner.run(network, LocalComputationManager.getDefault(), network.getVariantManager().getWorkingVariantId(), params, ReportNode.NO_OP).join();
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
        LoadFlowParameters params = new LoadFlowParameters();
        LoadFlowResult result = runner.run(network, LocalComputationManager.getDefault(), network.getVariantManager().getWorkingVariantId(), params, ReportNode.NO_OP).join();
        assertEquals(LoadFlowResult.Status.FAILED, result.getStatus());
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());

        ReportNode testReport = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("test")
                .build();

        OpenLoadFlowParameters.create(params).setFixRemoteVoltageTarget(true);
        result = runner.run(network, LocalComputationManager.getDefault(), network.getVariantManager().getWorkingVariantId(), params, testReport).join();
        assertEquals(LoadFlowResult.Status.FULLY_CONVERGED, result.getStatus());

        // For high values, indicator values is hardware sensitive (based on small differences between large numbers)
        // So we remove them from the tests
        String reportString = TestUtil.normalizeLineSeparator(LoadFlowAssert.reportToString(testReport).replaceAll("indicator:.*\\)", "indicator: ***)"));

        // Even the display order (sorted in plausibility indicator) is different between architectures ! SO lets check expected sentenences alone
        assertTrue(reportString.contains("         + Checking remote voltage targets"));
        assertTrue(reportString.contains("           Controlled buses 'vl4_0' and 'vl4_2' have incompatible target voltages (plausibility indicator: ***): disabling controller elements [vl1_0]"));
        assertTrue(reportString.contains("           Controlled buses 'vl4_2' and 'vl5_0' have incompatible target voltages (plausibility indicator: ***): disabling controller elements [vl3_0]"));
        assertTrue(reportString.contains("           Controlled bus 'vl4_1' has an unrealistic target voltage 403 Kv, causing a severe controller bus 'vl4_1' voltage drop (estimated at 27.392811 pu): disabling controller bus"));
    }
}
