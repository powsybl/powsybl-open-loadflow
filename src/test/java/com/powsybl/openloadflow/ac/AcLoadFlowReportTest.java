/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.EurostagFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static com.powsybl.openloadflow.util.ReportTestsUtil.compareReportWithReference;

/**
 * @author Bertrand Rix <bertrand.rix at artelys.com>
 */
class AcLoadFlowReportTest {

    @Test
    void testEsgTutoDetailedNrLogsLf() throws IOException {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        var lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));

        LoadFlow.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reporter);

        Assertions.assertTrue(compareReportWithReference(reporter, getClass().getResourceAsStream("/esgTutoReportDetailedNrReportLf.txt")));
    }

}
