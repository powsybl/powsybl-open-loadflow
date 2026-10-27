/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.PowsyblTestReportResourceBundle;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.fastdc.LodfParameters;
import com.powsybl.openloadflow.dc.fastdc.LodfResult;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.util.report.PowsyblOpenLoadFlowReportResourceBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class LodfAnalysisTest {

    private static final double DELTA_LODF = 1e-6;

    @Test
    void testLodf() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        List<String> ids = List.of("NHV1_NHV2_1", "NHV1_NHV2_2", "NGEN_NHV1", "NHV2_NLOAD");
        LodfResult result = LodfAnalysis.run(network, ids, ids, new LodfParameters().setThreshold(0.5), new LoadFlowParameters(), ReportNode.NO_OP);
        DenseMatrix lodf = result.lodfMatrix().toDense();
        assertEquals(1d, lodf.get(1, 0), DELTA_LODF);
        assertEquals(1d, lodf.get(0, 1), DELTA_LODF);
        assertEquals(List.of(2, 3), result.undefinedOutageIndices());
        for (double value : result.lodfMatrix().getValues()) {
            assertTrue(Math.abs(value) >= 0.5);
        }
    }

    @Test
    void testDefaultParameters() {
        Network network = FourBusNetworkFactory.create();
        List<String> ids = List.of("l14", "l12", "l23", "l34", "l13");
        LodfParameters defaults = new LodfParameters();
        assertEquals(0.05, defaults.getThreshold());
        assertEquals(Integer.MAX_VALUE, defaults.getMaxFactorsPerOutage());
        assertEquals(500, defaults.getOutageBatchSize());
        assertEquals(LodfAnalysis.run(network, ids, ids, defaults, new LoadFlowParameters(), ReportNode.NO_OP),
                LodfAnalysis.run(network, ids, ids, new LoadFlowParameters()));
    }

    @Test
    void testReport() {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withResourceBundles(PowsyblOpenLoadFlowReportResourceBundle.BASE_NAME, PowsyblTestReportResourceBundle.TEST_BASE_NAME)
                .withMessageTemplate("testReport")
                .build();
        List<String> ids = List.of("l12");
        LodfAnalysis.run(FourBusNetworkFactory.create(), ids, ids, new LodfParameters(), new LoadFlowParameters(), reportNode);
        assertFalse(reportNode.getChildren().isEmpty());
    }

    @Test
    void testZeroImpedanceBranch() {
        Network network = FourBusNetworkFactory.create();
        network.getLine("l13").setR(0).setX(0);
        LodfResult result = LodfAnalysis.run(network, List.of("l13", "l12"), List.of("l12", "l13"), new LodfParameters().setThreshold(0), new LoadFlowParameters(), ReportNode.NO_OP);
        // monitorable thanks to the minimal impedance
        assertNotEquals(0d, result.lodfMatrix().toDense().get(0, 0));
        assertEquals(List.of(1), result.undefinedOutageIndices());
    }

    @Test
    void testDisconnectedBranch() {
        Network network = FourBusNetworkFactory.create();
        network.getLine("l23").getTerminal1().disconnect();
        network.getLine("l23").getTerminal2().disconnect();
        LodfResult result = LodfAnalysis.run(network, List.of("l14", "l23"), List.of("l14", "l23"), new LodfParameters().setThreshold(0), new LoadFlowParameters(), ReportNode.NO_OP);
        DenseMatrix lodf = result.lodfMatrix().toDense();
        assertEquals(List.of(1), result.undefinedOutageIndices());
        assertEquals(0d, lodf.get(1, 0));
        assertEquals(0d, lodf.get(1, 1));
    }

    @Test
    void testUnknownBranch() {
        Network network = FourBusNetworkFactory.create();
        LoadFlowParameters parameters = new LoadFlowParameters();
        List<String> ids = List.of("l12");
        List<String> unknown = List.of("unknown");
        PowsyblException e = assertThrows(PowsyblException.class, () -> LodfAnalysis.run(network, ids, unknown, parameters));
        assertEquals("Branch 'unknown' not found", e.getMessage());
    }
}
