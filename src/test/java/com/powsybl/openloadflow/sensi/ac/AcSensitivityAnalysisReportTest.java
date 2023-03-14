/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.ac;

import com.google.common.io.ByteStreams;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Florian Dupuy <florian.dupuy at rte-france.com>
 */
class AcSensitivityAnalysisReportTest extends AbstractSensitivityAnalysisTest {

    protected static String normalizeLineSeparator(String str) {
        return str.replace("\r\n", "\n").replace("\r", "\n");
    }

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

        StringWriter sw = new StringWriter();
        reporter.export(sw);

        InputStream refStream = getClass().getResourceAsStream("/esgTutoReport.txt");
        String refLogExport = normalizeLineSeparator(new String(ByteStreams.toByteArray(refStream), StandardCharsets.UTF_8));
        String logExport = normalizeLineSeparator(sw.toString());
        assertEquals(refLogExport, logExport);
    }

    @Test
    void testEsgTutoDetailedNrLogsLf() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        var lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters);
        OpenLoadFlowParameters.create(lfParameters).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));
        runAcLf(network, reporter, lfParameters);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault(), reporter);

        StringWriter sw = new StringWriter();
        reporter.export(sw);

        InputStream refStream = getClass().getResourceAsStream("/esgTutoReportDetailedNrReportLf.txt");
        String refLogExport = normalizeLineSeparator(new String(ByteStreams.toByteArray(refStream), StandardCharsets.UTF_8));
        String logExport = normalizeLineSeparator(sw.toString());
        assertEquals(refLogExport, logExport);
    }

    @Test
    void testEsgTutoDetailedNrLogsSa() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        var lfParameters = new LoadFlowParameters();
        runAcLf(network, reporter, lfParameters);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters()).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SA));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault(), reporter);

        StringWriter sw = new StringWriter();
        reporter.export(sw);

        InputStream refStream = getClass().getResourceAsStream("/esgTutoReportDetailedNrReportSa.txt");
        String refLogExport = normalizeLineSeparator(new String(ByteStreams.toByteArray(refStream), StandardCharsets.UTF_8));
        String logExport = normalizeLineSeparator(sw.toString());
        assertEquals(refLogExport, logExport);
    }

    @Test
    void testEsgTutoDetailedNrLogsLfSa() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        var lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));
        runAcLf(network, reporter, lfParameters);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VLLOAD_0");
        sensiParameters.getLoadFlowParameters().setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        OpenLoadFlowParameters.create(sensiParameters.getLoadFlowParameters()).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SA));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));
        sensiRunner.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault(), reporter);

        StringWriter sw = new StringWriter();
        reporter.export(sw);

        InputStream refStream = getClass().getResourceAsStream("/esgTutoReportDetailedNrReportLfSa.txt");
        String refLogExport = normalizeLineSeparator(new String(ByteStreams.toByteArray(refStream), StandardCharsets.UTF_8));
        String logExport = normalizeLineSeparator(sw.toString());
        assertEquals(refLogExport, logExport);
    }
}
