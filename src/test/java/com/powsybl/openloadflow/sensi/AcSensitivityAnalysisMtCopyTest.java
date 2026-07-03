/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With the network per thread COPY mode, a multi-threaded sensitivity analysis simulates the very
 * same network as the single-threaded one (built once with the topo config covering all the
 * contingencies), so the values must be identical whatever the thread count, including on
 * node-breaker networks where contingency propagation used to give each partition a different
 * network (different retained switches and topology view).
 *
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
class AcSensitivityAnalysisMtCopyTest extends AbstractSensitivityAnalysisTest {

    AcSensitivityAnalysisMtCopyTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    @ParameterizedTest
    @EnumSource(OpenSensitivityAnalysisParameters.NetworkPerThreadMode.class)
    void testMultiThreadIdenticalToSingleThread(OpenSensitivityAnalysisParameters.NetworkPerThreadMode mode) {
        Network network = NodeBreakerNetworkFactory.create();
        List<Contingency> contingencies = List.of(
                new Contingency("L1", new BranchContingency("L1")),
                new Contingency("L2", new BranchContingency("L2")),
                new Contingency("LD", new LoadContingency("LD")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getLineStream().collect(Collectors.toList()));

        SensitivityAnalysisResult singleThread = run(network, factors, contingencies, 1, mode);
        SensitivityAnalysisResult multiThread = run(network, factors, contingencies, 2, mode);

        assertEquals(singleThread.getValues().size(), multiThread.getValues().size());
        for (String contingencyId : Arrays.asList(null, "L1", "L2", "LD")) {
            for (String functionId : List.of("L1", "L2")) {
                double expectedSensi = singleThread.getBranchFlow1SensitivityValue(contingencyId, "G", functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER);
                double expectedRef = singleThread.getBranchFlow1FunctionReferenceValue(contingencyId, functionId);
                double tolerance = mode == OpenSensitivityAnalysisParameters.NetworkPerThreadMode.COPY
                        ? 0 // same network as single thread: bit identical values
                        : 1e-6; // legacy rebuild: per-partition networks may differ slightly
                assertEquals(expectedSensi, multiThread.getBranchFlow1SensitivityValue(contingencyId, "G", functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER),
                        tolerance, "sensitivity mismatch for contingency " + contingencyId + " function " + functionId);
                assertEquals(expectedRef, multiThread.getBranchFlow1FunctionReferenceValue(contingencyId, functionId),
                        tolerance, "function reference mismatch for contingency " + contingencyId + " function " + functionId);
            }
        }
    }

    private SensitivityAnalysisResult run(Network network, List<SensitivityFactor> factors, List<Contingency> contingencies,
                                          int threadCount, OpenSensitivityAnalysisParameters.NetworkPerThreadMode mode) {
        SensitivityAnalysisParameters sensiParameters = createParameters(false, "VL2_0");
        OpenSensitivityAnalysisParameters sensiParametersExt = OpenSensitivityAnalysisParameters.getOrDefault(sensiParameters)
                .setThreadCount(threadCount)
                .setNetworkPerThreadMode(mode);
        sensiParameters.addExtension(OpenSensitivityAnalysisParameters.class, sensiParametersExt);
        SensitivityAnalysisRunParameters runParameters = new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies);
        return sensiRunner.run(network, factors, runParameters);
    }
}
