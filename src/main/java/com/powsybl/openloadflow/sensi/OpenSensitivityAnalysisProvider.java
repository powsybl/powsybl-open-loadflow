/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.auto.service.AutoService;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionJsonSerializer;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.contingency.list.ContingencyList;
import com.powsybl.contingency.contingency.list.DefaultContingencyList;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.DebugUtil;
import com.powsybl.openloadflow.util.ProviderConstants;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.json.SensitivityJsonModule;
import com.powsybl.tools.PowsyblCoreVersion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.powsybl.openloadflow.util.DebugUtil.DATE_TIME_FORMAT;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@AutoService(SensitivityAnalysisProvider.class)
public class OpenSensitivityAnalysisProvider implements SensitivityAnalysisProvider {

    private final MatrixFactory matrixFactory;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    private static final String JSON_EXTENSION = ".json";

    public OpenSensitivityAnalysisProvider() {
        this(new SparseMatrixFactory());
    }

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory) {
        this(matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.matrixFactory = matrixFactory;
        this.connectivityFactory = connectivityFactory;
    }

    @Override
    public String getName() {
        return ProviderConstants.NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    @Override
    public Optional<String> getLoadFlowProviderName() {
        return Optional.of(ProviderConstants.NAME);
    }

    @Override
    public Optional<ExtensionJsonSerializer> getSpecificParametersSerializer() {
        return Optional.of(new OpenSensitivityAnalysisParameterJsonSerializer());
    }

    @Override
    public Optional<Extension<SensitivityAnalysisParameters>> loadSpecificParameters(PlatformConfig platformConfig) {
        return Optional.of(OpenSensitivityAnalysisParameters.load(platformConfig));
    }

    @Override
    public Optional<Extension<SensitivityAnalysisParameters>> loadSpecificParameters(Map<String, String> properties) {
        return Optional.of(OpenSensitivityAnalysisParameters.load(properties));
    }

    @Override
    public List<String> getSpecificParametersNames() {
        return OpenSensitivityAnalysisParameters.SPECIFIC_PARAMETERS_NAMES;
    }

    private static OpenSensitivityAnalysisParameters getSensitivityAnalysisParametersExtension(SensitivityAnalysisParameters sensitivityAnalysisParameters) {
        OpenSensitivityAnalysisParameters sensiParametersExt = sensitivityAnalysisParameters.getExtension(OpenSensitivityAnalysisParameters.class);
        if (sensiParametersExt == null) {
            sensiParametersExt = new OpenSensitivityAnalysisParameters();
        }
        return sensiParametersExt;
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new ContingencyJsonModule())
                .registerModule(new LoadFlowParametersJsonModule())
                .registerModule(new SensitivityJsonModule());
    }

    Void runSync(Network network,
                        String workingVariantId,
                        SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter,
                        List<Contingency> contingencies,
                        List<SensitivityVariableSet> variableSets,
                        SensitivityAnalysisParameters sensitivityAnalysisParameters,
                        ReportNode reportNode) {
        network.getVariantManager().setWorkingVariant(workingVariantId);
        ReportNode sensiReportNode = Reports.createSensitivityAnalysis(reportNode, network.getId());

        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt = getSensitivityAnalysisParametersExtension(sensitivityAnalysisParameters);

        // We only support switch contingency for the moment. Contingency propagation is not supported yet.
        // Contingency propagation leads to numerous zero impedance branches, that are managed as min impedance
        // branches in sensitivity analysis. It could lead to issues with voltage controls in AC analysis.
        LfTopoConfig topoConfig = new LfTopoConfig();
        LoadFlowParameters loadFlowParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
        PropagatedContingencyCreationParameters creationParameters = new PropagatedContingencyCreationParameters()
            .setContingencyPropagation(false)
            .setShuntCompensatorVoltageControlOn(!loadFlowParameters.isDc() && loadFlowParameters.isShuntCompensatorVoltageControlOn())
            .setSlackDistributionOnConformLoad(loadFlowParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
            .setHvdcAcEmulation(!loadFlowParameters.isDc() && loadFlowParameters.isHvdcAcEmulation());
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);

        SensitivityFactorReader decoratedFactorReader = factorReader;

        // debugging
        if (sensitivityAnalysisParametersExt.getDebugDir() != null && !sensitivityAnalysisParametersExt.getDebugDir().isEmpty()) {
            Path debugDir = DebugUtil.getDebugDir(sensitivityAnalysisParametersExt.getDebugDir());
            String dateStr = ZonedDateTime.now().format(DATE_TIME_FORMAT);

            NetworkSerDe.write(network, debugDir.resolve("network-" + dateStr + ".xiidm"));

            ObjectWriter objectWriter = createObjectMapper()
                .writerWithDefaultPrettyPrinter();
            try {
                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("contingencies-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                    ContingencyList contingencyList = new DefaultContingencyList("default", contingencies);
                    objectWriter.writeValue(writer, contingencyList);
                }

                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("variable-sets-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                    objectWriter.writeValue(writer, variableSets);
                }

                try (BufferedWriter writer = Files.newBufferedWriter(debugDir.resolve("parameters-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                    objectWriter.writeValue(writer, sensitivityAnalysisParameters);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            decoratedFactorReader = new SensitivityFactoryJsonRecorder(factorReader, debugDir.resolve("factors-" + dateStr + JSON_EXTENSION));
        }

        AbstractSensitivityAnalysis<?, ?> analysis;
        if (loadFlowParameters.isDc()) {
            analysis = new DcSensitivityAnalysis(matrixFactory, connectivityFactory, sensitivityAnalysisParameters);
        } else {
            analysis = new AcSensitivityAnalysis(matrixFactory, connectivityFactory, sensitivityAnalysisParameters);
        }
        analysis.analyse(network, propagatedContingencies, variableSets, decoratedFactorReader, resultWriter, sensiReportNode, topoConfig, sensitivityAnalysisParametersExt.isStartWithFrozenACEmulation());
        return null;
    }

    public CompletableFuture<Void> run(Network network,
                                       String workingVariantId,
                                       SensitivityFactorReader factorReader,
                                       SensitivityResultWriter resultWriter,
                                       SensitivityAnalysisRunParameters runParameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);
        Objects.requireNonNull(runParameters);
        List<Contingency> contingencies = Objects.requireNonNull(runParameters.getContingencies());
        List<SensitivityVariableSet> variableSets = Objects.requireNonNull(runParameters.getVariableSets());
        SensitivityAnalysisParameters sensitivityAnalysisParameters = Objects.requireNonNull(runParameters.getSensitivityAnalysisParameters());
        ComputationManager computationManager = Objects.requireNonNull(runParameters.getComputationManager());
        ReportNode reportNode = Objects.requireNonNull(runParameters.getReportNode());

        return CompletableFutureTask.runAsync(() -> runSync(network,
            workingVariantId,
            factorReader,
            resultWriter,
            contingencies,
            variableSets,
            sensitivityAnalysisParameters,
            reportNode), computationManager.getExecutor());
    }

    public record ReplayResult<T extends SensitivityResultWriter>(T resultWriter, List<SensitivityFactor> factors, List<Contingency> contingencies) {
    }

    public <T extends SensitivityResultWriter> ReplayResult<T> replay(ZonedDateTime date, Path debugDir, Function<List<Contingency>, T> resultWriterProvider, ReportNode reportNode) {
        Objects.requireNonNull(date);
        Objects.requireNonNull(debugDir);
        Objects.requireNonNull(resultWriterProvider);
        Objects.requireNonNull(reportNode);

        String dateStr = date.format(DATE_TIME_FORMAT);

        Network network = NetworkSerDe.read(debugDir.resolve("network-" + dateStr + ".xiidm"));

        ObjectMapper objectMapper = createObjectMapper();
        List<SensitivityFactor> factors;
        List<Contingency> contingencies;
        List<SensitivityVariableSet> variableSets;
        SensitivityAnalysisParameters sensitivityAnalysisParameters;
        try {
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("factors-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                factors = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("contingencies-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                ContingencyList contingencyList = objectMapper.readValue(reader, DefaultContingencyList.class);
                contingencies = contingencyList.getContingencies(network);
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("variable-sets-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
                variableSets = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("parameters-" + dateStr + JSON_EXTENSION), StandardCharsets.UTF_8)) {
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

        var resultWriter = Objects.requireNonNull(resultWriterProvider.apply(contingencies));

        run(network, VariantManagerConstants.INITIAL_VARIANT_ID, new SensitivityFactorModelReader(factors, network), resultWriter,
                new SensitivityAnalysisRunParameters()
                        .setContingencies(contingencies)
                        .setVariableSets(variableSets)
                        .setParameters(sensitivityAnalysisParameters)
                        .setReportNode(reportNode))
                .join();

        return new ReplayResult<>(resultWriter, factors, contingencies);
    }

    public <T extends SensitivityResultWriter> ReplayResult<T> replay(ZonedDateTime date, Path debugDir, Function<List<Contingency>, T> resultWriterProvider) {
        return replay(date, debugDir, resultWriterProvider, ReportNode.NO_OP);
    }

    public ReplayResult<SensitivityResultModelWriter> replay(ZonedDateTime date, Path debugDir) {
        return replay(date, debugDir, SensitivityResultModelWriter::new);
    }
}
