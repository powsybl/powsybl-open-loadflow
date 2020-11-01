/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.google.auto.service.AutoService;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.sensitivity.*;
import com.powsybl.tools.PowsyblCoreVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(SensitivityAnalysisProvider.class)
public class OpenSensitivityAnalysisProvider implements SensitivityAnalysisProvider {

    private static final String NAME = "OpenSensitivityAnalysis";

    private final DcSensitivityAnalysis dcSensitivityAnalysis;

    private final AcSensitivityAnalysis acSensitivityAnalysis;

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory) {
        dcSensitivityAnalysis = new DcSensitivityAnalysis(matrixFactory);
        acSensitivityAnalysis = new AcSensitivityAnalysis(matrixFactory);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    private static OpenSensitivityAnalysisParameters getSensitivityAnalysisParametersExtension(SensitivityAnalysisParameters sensitivityAnalysisParameters) {
        OpenSensitivityAnalysisParameters sensiParametersExt = sensitivityAnalysisParameters.getExtension(OpenSensitivityAnalysisParameters.class);
        if (sensiParametersExt == null) {
            sensiParametersExt = new OpenSensitivityAnalysisParameters();
        }
        return sensiParametersExt;
    }

    private static OpenLoadFlowParameters getLoadFlowParametersExtension(LoadFlowParameters lfParameters) {
        OpenLoadFlowParameters lfParametersExt = lfParameters.getExtension(OpenLoadFlowParameters.class);
        if (lfParametersExt == null) {
            lfParametersExt = new OpenLoadFlowParameters();
        }
        return lfParametersExt;
    }

    @Override
    public CompletableFuture<SensitivityAnalysisResult> run(Network network, String workingStateId,
                                                            SensitivityFactorsProvider sensitivityFactorsProvider,
                                                            ContingenciesProvider contingenciesProvider,
                                                            SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                                            ComputationManager computationManager) {
        if (!contingenciesProvider.getContingencies(network).isEmpty()) {
            throw new UnsupportedOperationException("Contingencies not yet supported");
        }
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            List<SensitivityFactor> factors = sensitivityFactorsProvider.getFactors(network);

            LoadFlowParameters lfParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
            OpenLoadFlowParameters lfParametersExt = getLoadFlowParametersExtension(lfParameters);
            OpenSensitivityAnalysisParameters sensiParametersExt = getSensitivityAnalysisParametersExtension(sensitivityAnalysisParameters);

            List<SensitivityValue> sensitivityValues;
            if (lfParameters.isDc()) {
                sensitivityValues = dcSensitivityAnalysis.analyse(network, factors, lfParametersExt, sensiParametersExt);
            } else {
                sensitivityValues = acSensitivityAnalysis.analyse(network, factors, lfParametersExt);
            }

            boolean ok = true;
            Map<String, String> metrics = new HashMap<>();
            String logs = "";
            return new SensitivityAnalysisResult(ok, metrics, logs, sensitivityValues);
        });
    }
}
