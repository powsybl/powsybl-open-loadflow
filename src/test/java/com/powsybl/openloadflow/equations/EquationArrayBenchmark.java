/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Micro benchmark of the two hot loops of the equation array machinery, on a real size network:
 * <ul>
 *     <li>{@link EquationVector#updateArray(double[])}, which ends up in {@link EquationArray#eval(double[])}</li>
 *     <li>{@link JacobianMatrix#updateDer()}, which ends up in {@link EquationArray#der(EquationArray.DerHandler)}</li>
 * </ul>
 * Both are called once per Newton-Raphson iteration, so they are directly on the load flow critical path.
 *
 * <p>Usage: {@code EquationArrayBenchmark <network file> [<network file> ...]}. Any format that a powsybl importer
 * present on the class path can read is fine (XIIDM, MATPOWER, UCTE, ...). MATPOWER cases such as
 * {@code case9241pegase}, {@code case13659pegase} or {@code case6515rte} are good candidates: they are big enough
 * for the memory access pattern to matter, and PEGASE cases have a bus numbering which is uncorrelated with the
 * branch numbering, which is the worst case for the equation to term index mapping.
 *
 * <p>MATPOWER distributes those cases in the {@code .m} text form, while the powsybl MATPOWER importer reads the
 * binary {@code .mat} form. Octave converts one to the other:
 * <pre>
 *     octave --eval "mpc = case9241pegase; save('-v6', 'case9241pegase.mat', 'mpc');"
 * </pre>
 *
 * <p>A checksum of the evaluated values and of the Jacobian values is printed so that two implementations can be
 * checked to give exactly the same results.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class EquationArrayBenchmark {

    private static final long WARMUP_NS = 2_000_000_000L;
    private static final long ROUND_NS = 200_000_000L;
    private static final int ROUNDS = 7;

    private EquationArrayBenchmark() {
    }

    private interface Task {

        void run();
    }

    private record Stats(double minUs, double medianUs, double meanUs) {

        @Override
        public String toString() {
            return String.format(Locale.US, "min=%8.1f us  median=%8.1f us  mean=%8.1f us", minUs, medianUs, meanUs);
        }
    }

    /**
     * Warms up for a fixed duration, then times {@link #ROUNDS} rounds, each of them long enough to be measured
     * accurately whatever the cost of a single call.
     */
    private static Stats measure(Task task) {
        long warmupStart = System.nanoTime();
        long iterationNs;
        int warmupIterations = 0;
        do {
            task.run();
            warmupIterations++;
            iterationNs = (System.nanoTime() - warmupStart) / warmupIterations;
        } while (System.nanoTime() - warmupStart < WARMUP_NS);

        int iterationsPerRound = (int) Math.max(1, Math.min(100_000, ROUND_NS / Math.max(1, iterationNs)));
        double[] roundUs = new double[ROUNDS];
        for (int round = 0; round < ROUNDS; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterationsPerRound; i++) {
                task.run();
            }
            roundUs[round] = (System.nanoTime() - start) / 1000d / iterationsPerRound;
        }
        double[] sorted = roundUs.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(roundUs).average().orElseThrow();
        return new Stats(sorted[0], sorted[sorted.length / 2], mean);
    }

    private static double checksum(double[] values) {
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += (i + 1) * values[i];
        }
        return sum;
    }

    private static double jacobianChecksum(JacobianMatrix<AcVariableType, AcEquationType> j) {
        double[] sum = new double[1];
        int[] count = new int[1];
        j.getMatrix().iterateNonZeroValue((row, column, value) -> {
            count[0]++;
            sum[0] += (row + 1) * 31L * value + (column + 1) * 17L * value;
        });
        System.out.printf(Locale.US, "  jacobian non zero values: %d%n", count[0]);
        return sum[0];
    }

    private static void run(Path file) {
        System.out.println("=== " + file.getFileName() + " ===");
        Network network = Network.read(file);

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>());

        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        System.out.printf(Locale.US, "  network: %d buses, %d branches%n",
                lfNetwork.getBuses().size(), lfNetwork.getBranches().size());

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            // converge first so that the benchmark runs on a realistic state vector
            var result = new AcloadFlowEngine(context).run();
            System.out.printf(Locale.US, "  load flow: %s in %d iterations%n",
                    result.getSolverStatus(), result.getSolverIterations());

            EquationSystem<AcVariableType, AcEquationType> equationSystem = context.getEquationSystem();
            System.out.printf(Locale.US, "  equation system: %d columns, %d rows, %d single equations, %d equation arrays%n",
                    equationSystem.getIndex().getColumnCount(),
                    equationSystem.getIndex().getRowCount(),
                    equationSystem.getIndex().getSortedSingleEquationsToSolve().size(),
                    equationSystem.getEquationArrays().size());

            for (EquationArray<AcVariableType, AcEquationType> equationArray : equationSystem.getEquationArrays()) {
                int termCount = 0;
                for (EquationTermArray<AcVariableType, AcEquationType> termArray : equationArray.getTermArrays()) {
                    termCount += termArray.getTermNumsConcatenated().size();
                }
                int singleTermCount = 0;
                for (int elementNum = 0; elementNum < equationArray.getElementCount(); elementNum++) {
                    singleTermCount += equationArray.getSingleEquationTerms(elementNum).size();
                }
                System.out.printf(Locale.US, "  equation array %s: %d elements, %d term arrays, %d array terms, %d single terms%n",
                        equationArray.getType(), equationArray.getElementCount(), equationArray.getTermArrays().size(),
                        termCount, singleTermCount);
            }

            EquationVector<AcVariableType, AcEquationType> equationVector = context.getEquationVector();
            double[] values = new double[equationSystem.getIndex().getColumnCount()];
            equationVector.updateArray(values);
            System.out.printf(Locale.US, "  eval checksum: %.12e%n", checksum(values));

            JacobianMatrix<AcVariableType, AcEquationType> j = context.getJacobianMatrix();
            j.forceUpdate();
            System.out.printf(Locale.US, "  der checksum: %.12e%n", jacobianChecksum(j));

            List<EquationArray<AcVariableType, AcEquationType>> equationArrays = equationSystem.getIndex().getSortedEquationArraysToSolve();
            Matrix matrix = j.getMatrix();
            EquationArray.DerHandler derHandler = (column, row, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex;
            };

            // array only part, which is what the gather/scatter change is about
            System.out.println("  eval (arrays only) : " + measure(() -> {
                Arrays.fill(values, 0);
                for (EquationArray<AcVariableType, AcEquationType> equationArray : equationArrays) {
                    equationArray.eval(values);
                }
            }));
            System.out.println("  der  (arrays only) : " + measure(() -> {
                matrix.reset();
                for (EquationArray<AcVariableType, AcEquationType> equationArray : equationArrays) {
                    equationArray.der(derHandler);
                }
            }));

            // full update, including single equations
            System.out.println("  eval (full)        : " + measure(() -> equationVector.updateArray(values)));
            System.out.println("  der  (full)        : " + measure(j::updateDer));
        }

        // end to end, to see what it gives on a complete load flow run (LfNetwork loading included)
        System.out.println("  load flow (full)   : " + measure(() -> LoadFlow.run(network, parameters)));
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: EquationArrayBenchmark <network file> [<network file> ...]");
            System.exit(1);
        }
        for (String arg : args) {
            run(Path.of(arg));
        }
    }
}
