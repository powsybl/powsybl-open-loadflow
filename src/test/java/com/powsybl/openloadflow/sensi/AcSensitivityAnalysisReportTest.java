/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReportEquals;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class AcSensitivityAnalysisReportTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testEsgTuto() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testEsgTutoReport")
                .build();
        runAcLf(network, reportNode);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setReportNode(reportNode)
                .setParameters(sensiParameters);
        sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, runParameters);

        assertReportEquals("/esgTutoReport.txt", reportNode);
    }

    @Test
    void testEsgTutoDetailedNrLogsSensi() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testEsgTutoReport")
                .build();

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters())
                .setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SENSITIVITY_ANALYSIS));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setReportNode(reportNode)
                .setParameters(sensiParameters);
        sensiRunner.run(network, network.getVariantManager().getWorkingVariantId(), factors, runParameters);

        assertReportEquals("/esgTutoReportDetailedNrReportSensi.txt", reportNode);
    }
}
