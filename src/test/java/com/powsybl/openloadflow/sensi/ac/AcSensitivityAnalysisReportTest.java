/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.ReportTestsUtil.compareReportWithReference;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class AcSensitivityAnalysisReportTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testEsgTuto() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        runAcLf(network, reporter);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
            network.getLineStream().collect(Collectors.toList()));
        sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault(), reporter);

        Assertions.assertTrue(compareReportWithReference(reporter, getClass().getResourceAsStream("/esgTutoReport.txt")));
    }

    @Test
    void testEsgTutoDetailedNrLogsSensi() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters())
                .setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SENSITIVITY_ANALYSIS));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        sensiRunner.run(network, network.getVariantManager().getWorkingVariantId(), factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault(), reporter);

        Assertions.assertTrue(compareReportWithReference(reporter, getClass().getResourceAsStream("/esgTutoReportDetailedNrReportSensi.txt")));
    }
}
