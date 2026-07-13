/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.matpower.model.MBranch;
import com.powsybl.matpower.model.MBus;
import com.powsybl.matpower.model.MGen;
import com.powsybl.matpower.model.MatpowerFormatVersion;
import com.powsybl.matpower.model.MatpowerModel;
import com.powsybl.matpower.model.MatpowerWriter;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Manual benchmark of the alternative equations modeling
 * ({@link OpenLoadFlowParameters#setAlternativeEquations(boolean)}) on MATPOWER Pegase networks. Not run with the
 * default build (class name does not match surefire test patterns), run it with:
 * <pre>./mvnw test -Dtest=AlternativeEquationsBenchmark</pre>
 * The MATPOWER case files are downloaded (and cached in {@code target/matpower-cases}) from the MATPOWER github
 * repository; the benchmark is skipped when they are not available.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
class AlternativeEquationsBenchmark extends AbstractOpenSecurityAnalysisTest {

    private MatrixFactory matrixFactory;

    AlternativeEquationsBenchmark(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    private static final Path CACHE_DIR = Path.of("target", "matpower-cases");

    @BeforeEach
    void setUpBenchmark() {
        // the default test setup uses a dense matrix factory, way too slow for Pegase size networks, and debug
        // logging that would distort timings
        matrixFactory = new SparseMatrixFactory();
        GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();
        securityAnalysisProvider = new OpenSecurityAnalysisProvider(matrixFactory, connectivityFactory);
        loadFlowProvider = new OpenLoadFlowProvider(matrixFactory, connectivityFactory);
        ((Logger) LoggerFactory.getLogger("com.powsybl")).setLevel(Level.INFO);
        // keep the matrix lifecycle visible to count structural rebuilds (symbolic factorizations) per mode
        ((Logger) LoggerFactory.getLogger("com.powsybl.openloadflow.equations.JacobianMatrix")).setLevel(Level.DEBUG);
        ((Logger) LoggerFactory.getLogger("com.powsybl.openloadflow.ac.AcloadFlowEngine")).setLevel(Level.DEBUG);
    }

    private static Network loadPegaseNetwork(String caseName) {
        Path caseFile = CACHE_DIR.resolve(caseName + ".m");
        try {
            if (!Files.exists(caseFile)) {
                Files.createDirectories(CACHE_DIR);
                HttpResponse<Path> response = HttpClient.newHttpClient()
                        .send(HttpRequest.newBuilder(URI.create("https://raw.githubusercontent.com/MATPOWER/matpower/master/data/" + caseName + ".m")).build(),
                              HttpResponse.BodyHandlers.ofFile(caseFile));
                assumeTrue(response.statusCode() == 200, "Cannot download " + caseName);
            }
            MatpowerModel model = parseMatpowerFile(caseName, Files.readString(caseFile));
            Path matFile = CACHE_DIR.resolve(caseName + ".mat");
            MatpowerWriter.write(model, matFile, false);
            return Network.read(matFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static MatpowerModel parseMatpowerFile(String caseName, String content) {
        MatpowerModel model = new MatpowerModel(caseName);
        model.setVersion(MatpowerFormatVersion.V2);
        model.setBaseMva(Double.parseDouble(content.replaceAll("(?s).*mpc\\.baseMVA\\s*=\\s*([0-9.]+);.*", "$1")));
        for (double[] row : parseMatrix(content, "bus")) {
            MBus bus = new MBus();
            bus.setNumber((int) row[0]);
            bus.setType(MBus.Type.fromInt((int) row[1]));
            bus.setRealPowerDemand(row[2]);
            bus.setReactivePowerDemand(row[3]);
            bus.setShuntConductance(row[4]);
            bus.setShuntSusceptance(row[5]);
            bus.setAreaNumber((int) row[6]);
            bus.setVoltageMagnitude(row[7]);
            bus.setVoltageAngle(row[8]);
            bus.setBaseVoltage(row[9]);
            bus.setLossZone((int) row[10]);
            bus.setMaximumVoltageMagnitude(row[11]);
            bus.setMinimumVoltageMagnitude(row[12]);
            model.addBus(bus);
        }
        for (double[] row : parseMatrix(content, "gen")) {
            MGen gen = new MGen();
            gen.setNumber((int) row[0]);
            gen.setRealPowerOutput(row[1]);
            gen.setReactivePowerOutput(row[2]);
            gen.setMaximumReactivePowerOutput(row[3]);
            gen.setMinimumReactivePowerOutput(row[4]);
            gen.setVoltageMagnitudeSetpoint(row[5]);
            gen.setTotalMbase(row[6]);
            gen.setStatus((int) row[7]);
            gen.setMaximumRealPowerOutput(row[8]);
            gen.setMinimumRealPowerOutput(row[9]);
            model.addGenerator(gen);
        }
        for (double[] row : parseMatrix(content, "branch")) {
            MBranch branch = new MBranch();
            branch.setFrom((int) row[0]);
            branch.setTo((int) row[1]);
            branch.setR(row[2]);
            branch.setX(row[3]);
            branch.setB(row[4]);
            branch.setRateA(row[5]);
            branch.setRateB(row[6]);
            branch.setRateC(row[7]);
            branch.setRatio(row[8]);
            branch.setPhaseShiftAngle(row[9]);
            branch.setStatus((int) row[10]);
            branch.setAngMin(row[11]);
            branch.setAngMax(row[12]);
            model.addBranch(branch);
        }
        return model;
    }

    private static List<double[]> parseMatrix(String content, String name) {
        int start = content.indexOf("mpc." + name + " = [");
        int end = content.indexOf("];", start);
        List<double[]> rows = new ArrayList<>();
        for (String line : content.substring(content.indexOf('\n', start) + 1, end).split("\n")) {
            String trimmed = line.replace(";", "").trim();
            if (trimmed.isEmpty() || trimmed.startsWith("%")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            double[] row = new double[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                row[i] = switch (tokens[i]) { // matlab infinity, typically unlimited generator bounds
                    case "Inf" -> 9999;
                    case "-Inf" -> -9999;
                    default -> Double.parseDouble(tokens[i]);
                };
            }
            rows.add(row);
        }
        return rows;
    }

    private static LoadFlowParameters createLoadFlowParameters(boolean alternativeEquations) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setAlternativeEquations(alternativeEquations);
        return parameters;
    }

    private void benchmarkLoadFlow(String caseName) {
        Network network = loadPegaseNetwork(caseName);
        LoadFlow.Runner runner = new LoadFlow.Runner(loadFlowProvider);
        for (boolean alternativeEquations : new boolean[] {false, true}) {
            LoadFlowParameters parameters = createLoadFlowParameters(alternativeEquations);
            long best = Long.MAX_VALUE;
            LoadFlowResult result = null;
            for (int i = 0; i < 7; i++) {
                long t0 = System.nanoTime();
                result = runner.run(network, parameters);
                long dt = (System.nanoTime() - t0) / 1_000_000;
                if (i >= 2) { // warmup
                    best = Math.min(best, dt);
                }
            }
            assertTrue(result.isFullyConverged());
            System.out.printf("LF  %-16s alternativeEquations=%-5s best=%5d ms iterations=%d%n",
                    caseName, alternativeEquations, best, result.getComponentResults().get(0).getIterationCount());
        }
    }

    private void benchmarkSecurityAnalysis(String caseName, int contingencyCount) {
        benchmarkSecurityAnalysis(caseName, contingencyCount, 1);
    }

    private void benchmarkSecurityAnalysis(String caseName, int contingencyCount, int threadCount) {
        Network network = loadPegaseNetwork(caseName);
        List<Contingency> contingencies = network.getLineStream()
                .limit(contingencyCount)
                .map(line -> Contingency.line(line.getId()))
                .collect(Collectors.toList());
        long[] times = new long[2];
        long[] converged = new long[2];
        for (boolean alternativeEquations : new boolean[] {false, true}) {
            SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
            parameters.setLoadFlowParameters(createLoadFlowParameters(alternativeEquations));
            parameters.addExtension(OpenSecurityAnalysisParameters.class,
                    new OpenSecurityAnalysisParameters().setThreadCount(threadCount));
            long best = Long.MAX_VALUE;
            SecurityAnalysisResult result = null;
            for (int i = 0; i < 2; i++) {
                System.out.printf("SA-START %s alternativeEquations=%s threadCount=%d run %d%n", caseName, alternativeEquations, threadCount, i);
                long t0 = System.nanoTime();
                result = runSecurityAnalysis(network, contingencies, Collections.emptyList(), parameters);
                long dt = (System.nanoTime() - t0) / 1_000_000;
                System.out.printf("SA  %-16s threadCount=%d alternativeEquations=%-5s run %d: %d ms%n", caseName, threadCount, alternativeEquations, i, dt);
                if (i >= 1) { // warmup
                    best = Math.min(best, dt);
                }
            }
            int index = alternativeEquations ? 1 : 0;
            times[index] = best;
            converged[index] = result.getPostContingencyResults().stream()
                    .filter(postContingencyResult -> postContingencyResult.getStatus() == PostContingencyComputationStatus.CONVERGED)
                    .count();
            System.out.printf("SA  %-16s threadCount=%d alternativeEquations=%-5s best=%5d ms contingencies=%d converged=%d%n",
                    caseName, threadCount, alternativeEquations, best, contingencies.size(), converged[index]);
        }
        System.out.printf("SA  %-16s threadCount=%d speedup=x%.2f%n", caseName, threadCount, (double) times[0] / times[1]);
        assertTrue(converged[0] == converged[1], "converged contingency count mismatch between modelings");
    }

    private void benchmarkMatrixFill(String caseName) {
        Network network = loadPegaseNetwork(caseName);
        long[] nnz = new long[2];
        for (boolean alternativeEquations : new boolean[] {false, true}) {
            LoadFlowParameters parameters = createLoadFlowParameters(alternativeEquations);
            OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
            var acParameters = OpenLoadFlowParameters.createAcParameters(parameters, parametersExt,
                    matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
            LfNetwork lfNetwork = Networks.load(network, new MostMeshedSlackBusSelector()).get(0);
            try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
                var result = new AcloadFlowEngine(context).run();
                var jacobianMatrix = context.getJacobianMatrix();
                SparseMatrix matrix = (SparseMatrix) jacobianMatrix.getMatrix();
                int index = alternativeEquations ? 1 : 0;
                // the used element count of the CSC structure (getValues() returns the raw array with spare capacity)
                nnz[index] = matrix.getColumnStart()[matrix.getColumnCount()];
                System.out.println("FILL-STATUS " + caseName + " " + alternativeEquations + " " + result.getSolverStatus());

                // a branch flow term to toggle off/on, escalating the matrix status to VALUES_AND_ZEROS_INVALID
                // (values unchanged) to force a der update + full numerical factorization (klu_factor) per cycle
                var term = (com.powsybl.openloadflow.equations.EquationTerm<?, ?>) lfNetwork.getBranches().stream()
                        .filter(b -> !b.isDisabled() && b.getBus1() != null && b.getBus2() != null)
                        .findFirst().orElseThrow().getClosedP1();
                int n = context.getEquationSystem().getIndex().getColumnCount();
                for (int i = 0; i < 5; i++) { // warmup
                    term.setActive(false);
                    term.setActive(true);
                    jacobianMatrix.solve(new double[n]);
                    context.getEquationSystem().getStateVector().set(context.getEquationSystem().getStateVector().get());
                    jacobianMatrix.solve(new double[n]);
                }
                System.out.println("FILL-PHASE " + caseName + " " + alternativeEquations + " factor");
                long bestFactor = Long.MAX_VALUE;
                for (int i = 0; i < 20; i++) {
                    term.setActive(false);
                    term.setActive(true);
                    long t0 = System.nanoTime();
                    jacobianMatrix.solve(new double[n]);
                    bestFactor = Math.min(bestFactor, (System.nanoTime() - t0) / 1_000);
                }
                System.out.println("FILL-PHASE " + caseName + " " + alternativeEquations + " refactor");
                // state vector re-set escalates to VALUES_INVALID: der update + incremental refactorization
                // (klu_refactor) per cycle
                long bestRefactor = Long.MAX_VALUE;
                for (int i = 0; i < 20; i++) {
                    context.getEquationSystem().getStateVector().set(context.getEquationSystem().getStateVector().get());
                    long t0 = System.nanoTime();
                    jacobianMatrix.solve(new double[n]);
                    bestRefactor = Math.min(bestRefactor, (System.nanoTime() - t0) / 1_000);
                }
                System.out.printf("FILL %-16s alternativeEquations=%-5s n=%6d nnz=%8d der+klu_factor+solve=%6d us der+klu_refactor+solve=%6d us%n",
                        caseName, alternativeEquations, matrix.getRowCount(), nnz[index], bestFactor, bestRefactor);
            }
        }
        System.out.printf("FILL %-16s extra fill: +%d elements (+%.2f%%)%n", caseName, nnz[1] - nnz[0], 100d * (nnz[1] - nnz[0]) / nnz[0]);
    }

    @Test
    void benchmarkMatrixFills() {
        benchmarkMatrixFill("case1354pegase");
        benchmarkMatrixFill("case2869pegase");
        benchmarkMatrixFill("case9241pegase");
    }

    @Test
    void benchmarkLoadFlows() {
        benchmarkLoadFlow("case1354pegase");
        benchmarkLoadFlow("case2869pegase");
        benchmarkLoadFlow("case9241pegase");
    }

    private void benchmarkLoadFlowWithParameters(String caseName, Path parametersFile) {
        Network network = loadPegaseNetwork(caseName);
        LoadFlow.Runner runner = new LoadFlow.Runner(loadFlowProvider);
        // interleave the two modelings so that JIT/thermal drift affects both equally
        LoadFlowParameters[] parameters = new LoadFlowParameters[2];
        for (int m = 0; m < 2; m++) {
            parameters[m] = JsonLoadFlowParameters.read(parametersFile);
            parameters[m].getExtension(OpenLoadFlowParameters.class).setAlternativeEquations(m == 1);
        }
        List<List<Long>> times = List.of(new ArrayList<>(), new ArrayList<>());
        LoadFlowResult[] results = new LoadFlowResult[2];
        for (int i = 0; i < 30; i++) {
            int m = i % 2;
            long t0 = System.nanoTime();
            results[m] = runner.run(network, parameters[m]);
            long dt = (System.nanoTime() - t0) / 1_000_000;
            if (i >= 6) { // warmup
                times.get(m).add(dt);
            }
        }
        for (int m = 0; m < 2; m++) {
            List<Long> t = times.get(m).stream().sorted().collect(Collectors.toList());
            var componentResult = results[m].getComponentResults().get(0);
            System.out.printf("LF-RTE %-16s alternativeEquations=%-5s best=%5d ms median=%5d ms status=%s iterations=%d%n",
                    caseName, m == 1, t.get(0), t.get(t.size() / 2), componentResult.getStatus(), componentResult.getIterationCount());
        }
    }

    @Test
    void benchmarkEquationSystemCreation() {
        Network network = loadPegaseNetwork("case9241pegase");
        for (boolean alternativeEquations : new boolean[] {false, true}) {
            var creationParameters = new com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters(false, alternativeEquations);
            long bestCreate = Long.MAX_VALUE;
            long bestBuild = Long.MAX_VALUE;
            long bestEval = Long.MAX_VALUE;
            for (int i = 0; i < 10; i++) {
                LfNetwork lfNetwork = Networks.load(network, new MostMeshedSlackBusSelector()).get(0);
                long t0 = System.nanoTime();
                var equationSystem = new com.powsybl.openloadflow.ac.equations.vector.AcVectorizedEquationSystemCreator(lfNetwork, creationParameters).create();
                long t1 = System.nanoTime();
                try (var jacobianMatrix = new com.powsybl.openloadflow.equations.JacobianMatrix<>(equationSystem, matrixFactory)) {
                    com.powsybl.openloadflow.ac.solver.AcSolverUtil.initStateVector(lfNetwork, equationSystem,
                            new com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer());
                    jacobianMatrix.getMatrix();
                }
                long t2 = System.nanoTime();
                if (i >= 2) { // warmup
                    bestCreate = Math.min(bestCreate, (t1 - t0) / 1_000);
                    bestBuild = Math.min(bestBuild, (t2 - t1) / 1_000);
                }
                if (i >= 5) {
                    // f(x) evaluation cost (every NR iteration and outer loop check pays this)
                    try (var equationVector = new com.powsybl.openloadflow.equations.EquationVector<>(equationSystem)) {
                        for (int k = 0; k < 50; k++) {
                            equationSystem.getStateVector().set(equationSystem.getStateVector().get());
                            long e0 = System.nanoTime();
                            equationVector.getArray();
                            bestEval = Math.min(bestEval, (System.nanoTime() - e0) / 1_000);
                        }
                    }
                }
            }
            System.out.printf("CREATE case9241pegase alternativeEquations=%-5s evalFx=%6d us%n",
                    alternativeEquations, bestEval);
            System.out.printf("CREATE case9241pegase alternativeEquations=%-5s create=%6d us firstIndexAndBuild=%6d us%n",
                    alternativeEquations, bestCreate, bestBuild);
        }
    }

    @Test
    void benchmarkLoadFlowsWithRteParameters() {
        Path parametersFile = Path.of("rte-loadflow-default-parameters.json");
        assumeTrue(Files.exists(parametersFile), "rte-loadflow-default-parameters.json not found");
        benchmarkLoadFlowWithParameters("case9241pegase", parametersFile);
        benchmarkLoadFlowWithParameters("case13659pegase", parametersFile);
    }

    @Test
    void benchmarkLargeSecurityAnalysis() {
        benchmarkSecurityAnalysis("case9241pegase", 5000);
    }

    @Test
    void benchmarkSecurityAnalyses() {
        benchmarkSecurityAnalysis("case1354pegase", 300);
        benchmarkSecurityAnalysis("case2869pegase", 200);
    }

    @Test
    void benchmarkBigSecurityAnalyses() {
        benchmarkSecurityAnalysis("case9241pegase", 1000);
        benchmarkSecurityAnalysis("case13659pegase", 1000);
    }

    @Test
    void benchmarkSecurityAnalysesMultiThread() {
        int threads = Integer.getInteger("bench.threads", 4);
        benchmarkSecurityAnalysis("case1354pegase", 300, threads);
        benchmarkSecurityAnalysis("case2869pegase", 200, threads);
    }

    @Test
    void benchmarkBigSecurityAnalysesMultiThread() {
        int threads = Integer.getInteger("bench.threads", 4);
        benchmarkSecurityAnalysis("case9241pegase", 1000, threads);
        benchmarkSecurityAnalysis("case13659pegase", 1000, threads);
    }
}
