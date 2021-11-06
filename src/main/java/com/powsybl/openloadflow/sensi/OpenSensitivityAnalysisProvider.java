/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.auto.service.AutoService;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.xml.NetworkXml;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.json.SensitivityJsonModule;
import com.powsybl.tools.PowsyblCoreVersion;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(SensitivityAnalysisProvider.class)
public class OpenSensitivityAnalysisProvider implements SensitivityAnalysisProvider {

    private static final String NAME = "OpenSensitivityAnalysis";

    public static final String DATE_TIME_FORMAT = "yyyy-dd-M--HH-mm-ss-SSS";

    private final DcSensitivityAnalysis dcSensitivityAnalysis;

    private final AcSensitivityAnalysis acSensitivityAnalysis;

    public OpenSensitivityAnalysisProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory) {
        this(matrixFactory, EvenShiloachGraphDecrementalConnectivity::new);
    }

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        dcSensitivityAnalysis = new DcSensitivityAnalysis(matrixFactory, connectivityProvider);
        acSensitivityAnalysis = new AcSensitivityAnalysis(matrixFactory, connectivityProvider);
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
            sensiParametersExt = OpenSensitivityAnalysisParameters.load();
        }
        return sensiParametersExt;
    }

    private static OpenLoadFlowParameters getLoadFlowParametersExtension(LoadFlowParameters lfParameters) {
        OpenLoadFlowParameters lfParametersExt = lfParameters.getExtension(OpenLoadFlowParameters.class);
        if (lfParametersExt == null) {
            lfParametersExt = OpenLoadFlowParameters.load();
        }
        return lfParametersExt;
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new ContingencyJsonModule())
                .registerModule(new LoadFlowParametersJsonModule())
                .registerModule(new SensitivityJsonModule());
    }

    public CompletableFuture<Void> run(Network network,
                                       String workingStateId,
                                       SensitivityFactorReader factorReader,
                                       SensitivityValueWriter valueWriter,
                                       List<Contingency> contingencies,
                                       List<SensitivityVariableSet> variableSets,
                                       SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                       ComputationManager computationManager,
                                       Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(variableSets);
        Objects.requireNonNull(sensitivityAnalysisParameters);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(valueWriter);
        Objects.requireNonNull(reporter);

        Reporter sensiReporter = reporter.createSubReporter("sensitivityAnalysis",
                "Sensitivity analysis on network ${networkId}", "networkId", network.getId());

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createListForSensitivityAnalysis(network, contingencies);

        LoadFlowParameters lfParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = getLoadFlowParametersExtension(lfParameters);
        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt = getSensitivityAnalysisParametersExtension(sensitivityAnalysisParameters);

        SensitivityFactorReader decoratedFactorReader = factorReader;

        // debugging
        if (sensitivityAnalysisParametersExt.getDebugDir() != null) {
            Path debugDir = PlatformConfig.defaultConfig().getConfigDir().getFileSystem().getPath(sensitivityAnalysisParametersExt.getDebugDir());
            String dateStr = DateTime.now().toString(DATE_TIME_FORMAT);

            NetworkXml.write(network, debugDir.resolve("network-" + dateStr + ".xiidm"));

            ObjectWriter objectWriter = createObjectMapper()
                    .writerWithDefaultPrettyPrinter();
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("contingencies-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                    objectWriter.writeValue(writer, contingencies);
                }

                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("variable-sets-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                    objectWriter.writeValue(writer, variableSets);
                }

                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("parameters-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                    objectWriter.writeValue(writer, sensitivityAnalysisParameters);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            decoratedFactorReader = new SensitivityFactoryJsonRecorder(factorReader, debugDir.resolve("factors-" + dateStr + ".json"));
        }

        if (lfParameters.isDc()) {
            dcSensitivityAnalysis.analyse(network, propagatedContingencies, variableSets, lfParameters, lfParametersExt, decoratedFactorReader, valueWriter, sensiReporter);
        } else {
            acSensitivityAnalysis.analyse(network, propagatedContingencies, variableSets, lfParameters, lfParametersExt, decoratedFactorReader, valueWriter, sensiReporter);
        }

        return null;
    }

    public <T extends SensitivityValueWriter> T replay(DateTime date, Path debugDir, T valueWriter, Reporter reporter) {
        Objects.requireNonNull(date);
        Objects.requireNonNull(debugDir);
        Objects.requireNonNull(valueWriter);
        Objects.requireNonNull(reporter);

        String dateStr = date.toString(DATE_TIME_FORMAT);

        ObjectMapper objectMapper = createObjectMapper();
        List<SensitivityFactor> factors;
        List<Contingency> contingencies;
        List<SensitivityVariableSet> variableSets;
        SensitivityAnalysisParameters sensitivityAnalysisParameters;
        try {
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("factors-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                factors = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("contingencies-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                contingencies = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("variable-sets-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                variableSets = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("parameters-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                sensitivityAnalysisParameters = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // to avoid regenerating debug file during replay
        OpenSensitivityAnalysisParameters sensiParametersExt = sensitivityAnalysisParameters.getExtension(OpenSensitivityAnalysisParameters.class);
        if (sensiParametersExt != null) {
            sensiParametersExt.setDebugDir(null);
        }

        Network network = NetworkXml.read(debugDir.resolve("network-" + dateStr + ".xiidm"));

        run(network, VariantManagerConstants.INITIAL_VARIANT_ID, new SensitivityFactorModelReader(factors, network), valueWriter,
                contingencies, variableSets, sensitivityAnalysisParameters, LocalComputationManager.getDefault(), reporter);

        return valueWriter;
    }

    public <T extends SensitivityValueWriter> T replay(DateTime date, Path debugDir, T valueWriter) {
        return replay(date, debugDir, valueWriter, Reporter.NO_OP);
    }

    public List<SensitivityValue> replay(DateTime date, Path debugDir) {
        SensitivityValueModelWriter valueWriter = replay(date, debugDir, new SensitivityValueModelWriter());
        return valueWriter.getValues();
    }
}
