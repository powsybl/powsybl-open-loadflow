/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
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
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.LfBus;
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
        ReporterModel reporter = new ReporterModel("testEsgTutoReport", "Test ESG tutorial report");
        var lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(lfParameters).setReportedFeatures(Set.of(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_LOAD_FLOW));

        LoadFlowProvider provider = new OpenLoadFlowProvider(new DenseMatrixFactory(), new NaiveGraphConnectivityFactory<>(LfBus::getNum));
        LoadFlow.Runner runner = new LoadFlow.Runner(provider);
        LoadFlowResult result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), lfParameters, reporter);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        LoadFlowAssert.assertReportEquals("/esgTutoReportDetailedNrReportLf.txt", reporter);
    }
}
