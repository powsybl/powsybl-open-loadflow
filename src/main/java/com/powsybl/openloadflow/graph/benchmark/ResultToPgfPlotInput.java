/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.graph.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author Valentin Carrez {@literal <valentin.carrez at rte-france.com>}
 */
@CommandLine.Command(version = "1.0")
public final class ResultToPgfPlotInput implements Callable<Integer> {

    private static final class PlotInput {
        @CommandLine.Option(names = {"-p", "--plot"}, arity = "3", description = "plot", required = true)
        private List<String> plots;

        private Map<String, Map<String, Plot>> getPlots() throws IOException {
            Map<String, Map<String, Plot>> plots = new HashMap<>();

            for (int i = 0; i < this.plots.size(); i += 3) {
                String alg = this.plots.get(i);
                String sa = this.plots.get(i + 1);
                Path json = Path.of(this.plots.get(i + 2));

                Map<String, Plot> algToPlot = plots.computeIfAbsent(sa, a -> new HashMap<>());

                if (algToPlot.containsKey(alg)) {
                    throw new IllegalStateException("Duplicate plot: " + alg + " - " + sa);
                }

                algToPlot.put(alg, Plot.read(alg, sa, json));
            }

            return plots;
        }
    }

    private static final class DirectoryInput {
        @CommandLine.Option(names = {"-d", "--directory"}, required = true)
        private Path directory;

        @CommandLine.Option(names = {"-n", "--name"})
        private Map<String, String> namer;

        private Map<String, Map<String, Plot>> getPlots() throws IOException {
            Map<String, Map<String, Plot>> plots = new HashMap<>();

            try (Stream<Path> saStream = Files.list(directory)) {
                for (Iterator<Path> it = saStream.iterator(); it.hasNext();) {
                    Path saPath = it.next();
                    if (!Files.isDirectory(saPath)) {
                        continue;
                    }

                    String sa = rename(saPath.getFileName().toString());
                    plots.put(sa, getAlgToPlots(saPath, sa));
                }
            }

            return plots;
        }

        private Map<String, Plot> getAlgToPlots(Path folder, String sa) throws IOException {
            Map<String, Plot> algToPlot = new HashMap<>();
            try (Stream<Path> plotsStream = Files.walk(folder)) {
                for (Iterator<Path> it = plotsStream.iterator(); it.hasNext();) {
                    Path plotPath = it.next();
                    if (!Files.isRegularFile(plotPath) || !plotPath.getFileName().toString().endsWith(".json")) {
                        continue;
                    }

                    String alg = rename(plotPath.getFileName().toString());

                    algToPlot.put(alg, Plot.read(alg, sa, plotPath));
                }
            }

            return algToPlot;
        }

        private String rename(String name) {
            String n = namer.get(name);
            if (n != null) {
                return n;
            }

            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String withoutExt = name.substring(0, dot);
                n = namer.get(withoutExt);
                if (n != null) {
                    return n;
                }
            }

            return name;
        }
    }

    private static final class Input {

        @CommandLine.ArgGroup(exclusive = false)
        private PlotInput plotInput;

        @CommandLine.ArgGroup(exclusive = false)
        private DirectoryInput directoryInput;

        public Map<String, Map<String, Plot>> getPlots() throws IOException {
            if (plotInput != null) {
                return plotInput.getPlots();
            } else {
                return directoryInput.getPlots();
            }
        }
    }

    @CommandLine.Option(names = {"-o", "--output"}, description = "output", required = true)
    private Path output;

    @CommandLine.ArgGroup
    private Input input;

    @CommandLine.Option(names = {"--average-per-workload"})
    private boolean averagePerWorkload;

    @CommandLine.Option(names = {"--average-per-op"})
    private boolean averagePerOperation;

    @CommandLine.Option(names = {"--prefix"}, required = true)
    private String prefix;

    @CommandLine.Option(names = {"--filter"})
    private List<BenchmarkMethod> filter;

    private ResultToPgfPlotInput() { }

    public static void main(String[] args) throws IOException {
        new CommandLine(new ResultToPgfPlotInput())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
    }

    @Override
    public Integer call() throws IOException {
        // sa then alg then plot
        Map<String, Map<String, Plot>> saToAlgToPlots = input.getPlots();

        if (averagePerOperation) {
            var transpose = transpose(saToAlgToPlots);

            BenchmarkMethod[] methods = filter == null ? BenchmarkMethod.values() : filter.toArray(new BenchmarkMethod[0]);
            for (BenchmarkMethod method : methods) {
                var saToAlgToResult = transpose.get(method);
                if (saToAlgToResult == null) {
                    continue;
                }

                Path out = getOutputPath("total_" + method.shortName() + ".txt");
                System.out.println("Writing to " + out);

                try (BufferedWriter bw = Files.newBufferedWriter(out)) {
                    writeSAToAlgToData(bw, transpose.get(method), ap -> String.valueOf(ap.aggregator.getSum() / 1e6 / ap.plot().iterations() / ap.plot().threads()));
                }
            }
        } else if (averagePerWorkload) {
            for (Map.Entry<String, Map<String, Plot>> entry : saToAlgToPlots.entrySet()) {
                Path out = getOutputPath("mean_" + entry.getKey() + ".txt");
                System.out.println("Writing to " + out);

                try (BufferedWriter bw = Files.newBufferedWriter(out)) {
                    Collection<Plot> plots = entry.getValue().values();
                    writeDataPerMethodPerAlgForSomePlots(bw, plots, filter, (p, agg) -> String.valueOf(agg.getMean(TimeUnit.MICROSECONDS)));
                }
            }
        } else {
            Path out = getOutputPath("total_time.txt");
            System.out.println("Writing to " + out);

            try (BufferedWriter bw = Files.newBufferedWriter(out)) {
                writeSAToAlgToData(bw, saToAlgToPlots, p -> String.valueOf(p.total(TimeUnit.SECONDS)));
            }
        }

        return 0;
    }

    private Path getOutputPath(String file) {
        Path output = this.output.resolve(prefix + Objects.requireNonNullElse(filter, ""))
                .resolve(file);

        try {
            Files.createDirectories(output.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return output;
    }

    private static void writeDataPerMethodPerAlgForSomePlots(BufferedWriter bw,
                                                             Collection<Plot> plots,
                                                             List<BenchmarkMethod> filter,
                                                             BiFunction<Plot, Aggregator, String> aggToString) throws IOException {
        // write header
        bw.write("method");
        for (Plot plot : plots) {
            bw.write("   ");
            bw.write(plot.algorithm);
        }
        bw.newLine();

        // write rows
        List<BenchmarkMethod> allMethods = getAllMethods(plots);
        for (BenchmarkMethod method : allMethods) {
            if (filter == null || filter.contains(method)) {
                bw.write(method.shortNameCamelCase());
                for (Plot plot : plots) {
                    bw.write("   ");
                    Aggregator agg = plot.measures.get(method);

                    if (agg == null) {
                        bw.write("nan");
                    } else {
                        bw.write(aggToString.apply(plot, agg));
                    }
                }
                bw.newLine();
            }
        }
    }

    private static <T> void writeSAToAlgToData(BufferedWriter bw,
                                               Map<String, Map<String, T>> saToAlgToData,
                                               Function<T, String> toString) throws IOException {
        // write header
        bw.write("dataset");
        List<String> algorithms = getAllAlgorithms(saToAlgToData);
        for (String alg : algorithms) {
            bw.write("   ");
            bw.write(alg);
        }
        bw.newLine();

        // write rows
        for (Map.Entry<String, Map<String, T>> entry : saToAlgToData.entrySet()) {
            bw.write(entry.getKey());

            for (String alg : algorithms) {
                T data = entry.getValue().get(alg);

                bw.write("   ");
                if (data == null) {
                    bw.write("nan");
                } else {
                    bw.write(toString.apply(data));
                }
            }
            bw.newLine();
        }
    }

    private static List<BenchmarkMethod> getAllMethods(Collection<Plot> plots) {
        Set<BenchmarkMethod> methods = new HashSet<>();

        for (Plot plot : plots) {
            methods.addAll(plot.measures.keySet());
        }

        return new ArrayList<>(methods);
    }

    private static <T> List<String> getAllAlgorithms(Map<String, Map<String, T>> plots) {
        return plots.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .toList();
    }

    private static Map<BenchmarkMethod, Map<String, Map<String, AggregatorWithPlot>>> transpose(Map<String, Map<String, Plot>> plots) {
        Map<BenchmarkMethod, Map<String, Map<String, AggregatorWithPlot>>> transpose = new HashMap<>();

        for (Map.Entry<String, Map<String, Plot>> saToAlg : plots.entrySet()) {
            String sa = saToAlg.getKey();

            for (Map.Entry<String, Plot> algToPlot : saToAlg.getValue().entrySet()) {
                String alg = algToPlot.getKey();

                for (Map.Entry<BenchmarkMethod, Aggregator> methodToResult : algToPlot.getValue().measures().entrySet()) {
                    BenchmarkMethod method = methodToResult.getKey();
                    Map<String, Map<String, AggregatorWithPlot>> methodResult = transpose.computeIfAbsent(method, k -> new HashMap<>());
                    Map<String, AggregatorWithPlot> saResult = methodResult.computeIfAbsent(sa, k -> new HashMap<>());
                    AggregatorWithPlot oldValue = saResult.put(alg, new AggregatorWithPlot(methodToResult.getValue(), algToPlot.getValue()));

                    if (oldValue != null) {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        return transpose;
    }

    private record Plot(String algorithm, String sa, double initialGraphBuild, double total, Map<BenchmarkMethod, Aggregator> measures, int iterations, int threads) {

        public static Plot read(String algorithm, String sa, Path input) throws IOException {
            ObjectMapper mapper = new ObjectMapper();

            try (BufferedReader br = Files.newBufferedReader(input)) {
                JsonNode root = mapper.readTree(br);
                int threads = root.get("operations").size();
                JsonNode merged = root.get("merged");
                JsonNode initialGraphBuild = merged.get("initialGraphBuild");
                JsonNode temporaryChanges = merged.get("temporaryChanges");

                int iterations = root.get("timePerIteration").get("count").asInt();

                return new Plot(algorithm, sa,
                        getInitialGraphBuild(root),
                        getTotal(root),
                        merge(getMeasures(initialGraphBuild), getMeasures(temporaryChanges)),
                        iterations,
                        threads);
            }
        }

        public double total(TimeUnit unit) {
            return total / unit.toNanos(1);
        }

        public double initialGraphBuild(TimeUnit unit) {
            return initialGraphBuild / unit.toNanos(1);
        }

        private static double getTotal(JsonNode root) {
            JsonNode operations = root.get("operations");
            long max = 0;
            for (JsonNode sub : operations) {
                max = Math.max(sub.get("totalRuntime").longValue(), max);
            }

            int iterations = root.get("timePerIteration").get("count").asInt();
            return (double) max / iterations;
        }

        public static double getInitialGraphBuild(JsonNode root) {
            JsonNode operations = root.get("operations");
            long max = 0;

            for (JsonNode op : operations) {
                long sum = 0;
                for (JsonNode sub : op.get("initialGraphBuild")) {
                    sum += sub.get("sum").longValue();
                }

                max = Math.max(max, sum);
            }

            int iterations = root.get("timePerIteration").get("count").asInt();
            return (double) max / iterations;
        }

        private static Map<BenchmarkMethod, Aggregator> getMeasures(JsonNode node) {
            Map<BenchmarkMethod, Aggregator> measures = new HashMap<>();

            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                BenchmarkMethod method = BenchmarkMethod.valueOf(entry.getKey());
                JsonNode measure = entry.getValue();

                measures.put(method, Aggregator.fromJson(measure));
            }

            return measures;
        }

        private static Map<BenchmarkMethod, Aggregator> merge(Map<BenchmarkMethod, Aggregator> initialGraphBuild, Map<BenchmarkMethod, Aggregator> temporaryChanges) {
            Map<BenchmarkMethod, Aggregator> merged = new HashMap<>(initialGraphBuild);

            for (Map.Entry<BenchmarkMethod, Aggregator> entry : temporaryChanges.entrySet()) {
                merged.compute(entry.getKey(), (k, agg) -> {
                    if (agg == null) {
                        return entry.getValue();
                    } else {
                        Aggregator res = new Aggregator(agg);
                        res.merge(entry.getValue());
                        return res;
                    }
                });
            }

            return merged;
        }
    }

    private record AggregatorWithPlot(Aggregator aggregator, Plot plot) { }
}
