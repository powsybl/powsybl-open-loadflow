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
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.json.ContingencyJsonModule;
import com.powsybl.iidm.network.Network;
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
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.json.SensitivityAnalysisParametersJsonModule;
import com.powsybl.tools.PowsyblCoreVersion;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
                                                            List<Contingency> contingencies,
                                                            SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                                            ComputationManager computationManager) {
        return run(network, workingStateId, sensitivityFactorsProvider, contingencies, sensitivityAnalysisParameters, computationManager, Reporter.NO_OP);
    }

    private static void addVariableSet(SensitivityFactor factor, Map<String, SensitivityVariableSet> variableSetsById) {
        if (factor instanceof BranchFlowPerLinearGlsk) {
            BranchFlowPerLinearGlsk glskFactor = (BranchFlowPerLinearGlsk) factor;
            List<WeightedSensitivityVariable> weightedVariables = glskFactor.getVariable().getGLSKs()
                    .entrySet().stream()
                    .map(e -> new WeightedSensitivityVariable(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            if (!variableSetsById.containsKey(glskFactor.getVariable().getId())) {
                variableSetsById.put(glskFactor.getVariable().getId(), new SensitivityVariableSet(glskFactor.getVariable().getId(), weightedVariables));
            }
        }
    }

    private static List<SensitivityVariableSet> getVariableSets(Network network, SensitivityFactorsProvider sensitivityFactorsProvider,
                                                                List<Contingency> contingencies) {
        Map<String, SensitivityVariableSet> variableSetsById = new HashMap<>();
        for (SensitivityFactor factor : sensitivityFactorsProvider.getCommonFactors(network)) {
            addVariableSet(factor, variableSetsById);
        }
        for (SensitivityFactor factor : sensitivityFactorsProvider.getAdditionalFactors(network)) {
            addVariableSet(factor, variableSetsById);
        }
        for (Contingency contingency : contingencies) {
            for (SensitivityFactor factor : sensitivityFactorsProvider.getAdditionalFactors(network, contingency.getId())) {
                addVariableSet(factor, variableSetsById);
            }
        }
        return new ArrayList<>(variableSetsById.values());
    }

    @Override
    public CompletableFuture<SensitivityAnalysisResult> run(Network network, String workingStateId,
                                                            SensitivityFactorsProvider sensitivityFactorsProvider,
                                                            List<Contingency> contingencies,
                                                            SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                                            ComputationManager computationManager,
                                                            Reporter reporter) {

        Reporter sensiReporter = reporter.createSubReporter("sensitivityAnalysis",
                "Sensitivity analysis on network ${networkId}", "networkId", network.getId());
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            List<SensitivityVariableSet> variableSets = getVariableSets(network, sensitivityFactorsProvider, contingencies);
            SensitivityFactorReader factorReader = new SensitivityFactorReaderAdapter(network, sensitivityFactorsProvider, contingencies, variableSets);
            SensitivityValueWriterAdapter valueWriter = new SensitivityValueWriterAdapter();
            run(network, contingencies, variableSets, sensitivityAnalysisParameters, factorReader, valueWriter, sensiReporter);

            boolean ok = true;
            Map<String, String> metrics = new HashMap<>();
            String logs = "";
            return new SensitivityAnalysisResult(ok, metrics, logs, valueWriter.getSensitivityValues(), valueWriter.getSensitivityValuesByContingency());
        });
    }

    public void run(Network network, List<Contingency> contingencies, List<SensitivityVariableSet> variableSets,
                    SensitivityAnalysisParameters sensitivityAnalysisParameters, SensitivityFactorReader factorReader,
                    SensitivityValueWriter valueWriter) {
        run(network, contingencies, variableSets, sensitivityAnalysisParameters, factorReader, valueWriter, Reporter.NO_OP);
    }

    public void run(Network network, List<Contingency> contingencies, List<SensitivityVariableSet> variableSets,
                    SensitivityAnalysisParameters sensitivityAnalysisParameters, List<SensitivityFactor2> factors,
                    SensitivityValueWriter valueWriter, Reporter reporter) {
        run(network, contingencies, variableSets, sensitivityAnalysisParameters, new SensitivityFactorModelReader(factors), valueWriter, reporter);
    }

    public void run(Network network, List<Contingency> contingencies, List<SensitivityVariableSet> variableSets,
                    SensitivityAnalysisParameters sensitivityAnalysisParameters, List<SensitivityFactor2> factors,
                    SensitivityValueWriter valueWriter) {
        run(network, contingencies, variableSets, sensitivityAnalysisParameters, factors, valueWriter, Reporter.NO_OP);
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .registerModule(new ContingencyJsonModule())
                .registerModule(new LoadFlowParametersJsonModule())
                .registerModule(new SensitivityAnalysisParametersJsonModule())
                .registerModule(new SensitivityJsonModule());
    }

    public void run(Network network, List<Contingency> contingencies, List<SensitivityVariableSet> variableSets,
                    SensitivityAnalysisParameters sensitivityAnalysisParameters, SensitivityFactorReader factorReader,
                    SensitivityValueWriter valueWriter, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(variableSets);
        Objects.requireNonNull(sensitivityAnalysisParameters);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(valueWriter);
        Objects.requireNonNull(reporter);

        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.create(network, contingencies, new HashSet<>());

        LoadFlowParameters lfParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = getLoadFlowParametersExtension(lfParameters);
        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt = getSensitivityAnalysisParametersExtension(sensitivityAnalysisParameters);

        SensitivityFactorReader decoratedFactorReader = factorReader;

        // debugging
        Path debugDir = sensitivityAnalysisParametersExt.getDebugDir();
        if (debugDir != null) {
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
            dcSensitivityAnalysis.analyse(network, propagatedContingencies, variableSets, lfParameters, lfParametersExt, decoratedFactorReader, valueWriter, reporter);
        } else {
            acSensitivityAnalysis.analyse(network, propagatedContingencies, variableSets, lfParameters, lfParametersExt, decoratedFactorReader, valueWriter, reporter);
        }
    }

    public void replay(DateTime date, SensitivityValueWriter valueWriter, Reporter reporter) {
        Objects.requireNonNull(date);
        Objects.requireNonNull(valueWriter);
        Objects.requireNonNull(reporter);

        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt = OpenSensitivityAnalysisParameters.load();
        Path debugDir = sensitivityAnalysisParametersExt.getDebugDir();
        if (debugDir == null) {
            throw new IllegalArgumentException("Debug mode is not activated");
        }
        String dateStr = date.toString(DATE_TIME_FORMAT);

        List<SensitivityFactor2> factors = SensitivityFactor2.parseJson(debugDir.resolve("factors-" + dateStr + ".json"));

        ObjectMapper objectMapper = createObjectMapper();
        List<Contingency> contingencies;
        List<SensitivityVariableSet> variableSets;
        SensitivityAnalysisParameters sensitivityAnalysisParameters;
        try {
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("contingencies-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
                contingencies = objectMapper.readValue(reader, new TypeReference<>() {
                });
            }
            try (BufferedReader reader = Files.newBufferedReader(debugDir.resolve("variables-sets-" + dateStr + ".json"), StandardCharsets.UTF_8)) {
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

        Network network = NetworkXml.read(debugDir.resolve("network-" + dateStr + ".xiidm"));

        run(network, contingencies, variableSets, sensitivityAnalysisParameters, new SensitivityFactorModelReader(factors), valueWriter, reporter);
    }

    public void replay(DateTime date, SensitivityValueWriter valueWriter) {
        replay(date, valueWriter, Reporter.NO_OP);
    }
}
