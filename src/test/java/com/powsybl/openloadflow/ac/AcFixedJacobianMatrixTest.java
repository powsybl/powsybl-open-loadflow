/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolationFilter;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisProvider;
import com.powsybl.openloadflow.test.networks.ZeroImpedanceNetworkFactory;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AcFixedJacobianMatrix}: the AC Jacobian solved on a sparse matrix whose matrix structure does not change, with the same
 * solutions as the matrix of the index, and one matrix and one analysis of its LU decomposition for a whole computation.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class AcFixedJacobianMatrixTest {

    /** Counts the sparse matrices that are created: one each time a Jacobian matrix takes another matrix structure. */
    private static final class CountingMatrixFactory extends SparseMatrixFactory {

        private int matrixCount;

        @Override
        public SparseMatrix create(int rowCount, int columnCount, int estimatedValueCount) {
            matrixCount++;
            return super.create(rowCount, columnCount, estimatedValueCount);
        }
    }

    private static long countVoltageControllerBuses(LfNetwork lfNetwork) {
        return lfNetwork.getBuses().stream().filter(LfBus::isGeneratorVoltageControlEnabled).count();
    }

    /** Runs a load flow, then hands over its context: the state is the solution, some buses have reached a limit. */
    private static void runLoadFlow(Network network, MatrixFactory matrixFactory, Consumer<AcLoadFlowContext> afterwards) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters,
                OpenLoadFlowParameters.create(parameters).setAcJacobianStructure(AcJacobianStructure.FIXED), matrixFactory,
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        long voltageControllerBusCount = countVoltageControllerBuses(lfNetwork);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context).run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertTrue(countVoltageControllerBuses(lfNetwork) < voltageControllerBusCount);
            afterwards.accept(context);
        }
    }

    @Test
    void analysedOnceWhileBusesReleaseTheirVoltage() {
        runLoadFlow(IeeeCdfNetworkFactory.create118(), new SparseMatrixFactory(), context ->
                assertEquals(1, ((AcFixedJacobianMatrix) context.getJacobianMatrix()).getFixedJacobian().getAnalysisCount()));
    }

    private static DenseMatrix randomColumns(int rowCount, int columnCount, Random random) {
        DenseMatrix columns = new DenseMatrix(rowCount, columnCount);
        for (int j = 0; j < columnCount; j++) {
            for (int i = 0; i < rowCount; i++) {
                columns.set(i, j, random.nextDouble());
            }
        }
        return columns;
    }

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, 1e-8 * Math.max(1, Math.abs(expected)));
    }

    /** Right-hand sides one by one and as a block of columns, enough of them to be solved in two groups. */
    private static void assertSameSolutions(AcLoadFlowContext context, boolean transposed) {
        JacobianMatrix<AcVariableType, AcEquationType> jacobian = context.getJacobianMatrix();
        int n = context.getEquationSystem().getIndex().getRowCount();
        int columnCount = 2000;
        Random random = new Random(0);
        double[] b = random.doubles(n).toArray();
        double[] expected = b.clone();
        DenseMatrix columns = randomColumns(n, columnCount, random);
        DenseMatrix expectedColumns = new DenseMatrix(n, columnCount);
        expectedColumns.copyValuesFrom(columns);
        // the reference: the matrix in the numbering of the index, with a decomposition of its own
        try (LUDecomposition reference = jacobian.getMatrix().decomposeLU()) {
            if (transposed) {
                jacobian.solveTransposed(b);
                reference.solveTransposed(expected);
                jacobian.solveTransposed(columns);
                reference.solveTransposed(expectedColumns);
            } else {
                jacobian.solve(b);
                reference.solve(expected);
                jacobian.solve(columns);
                reference.solve(expectedColumns);
            }
        }
        for (int i = 0; i < n; i++) {
            assertClose(expected[i], b[i]);
            for (int j = 0; j < columnCount; j++) {
                assertClose(expectedColumns.get(i, j), columns.get(i, j));
            }
        }
    }

    @Test
    void sameSolutionsAsTheMatrixOfTheIndex() {
        runLoadFlow(IeeeCdfNetworkFactory.create300(), new SparseMatrixFactory(), context -> {
            assertSameSolutions(context, false);
            assertSameSolutions(context, true);
        });
    }

    /** Runs a security analysis on the loss of each given line, and returns how many matrices it created. */
    private static int countMatrices(Network network, LoadFlowParameters parameters, List<String> lineIds) {
        List<Contingency> contingencies = lineIds.stream()
                .map(lineId -> new Contingency(lineId, new BranchContingency(lineId)))
                .toList();
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        (parametersExt != null ? parametersExt : OpenLoadFlowParameters.create(parameters)).setAcJacobianStructure(AcJacobianStructure.FIXED);
        CountingMatrixFactory matrixFactory = new CountingMatrixFactory();
        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .setFilter(new LimitViolationFilter())
                .setComputationManager(LocalComputationManager.getDefault())
                .setSecurityAnalysisParameters(new SecurityAnalysisParameters().setLoadFlowParameters(parameters))
                .setReportNode(ReportNode.NO_OP);
        SecurityAnalysisResult result = new OpenSecurityAnalysisProvider(matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>())
                .run(network, network.getVariantManager().getWorkingVariantId(), n -> contingencies, runParameters)
                .join().getResult();
        assertEquals(contingencies.size(), result.getPostContingencyResults().size());
        assertTrue(result.getPostContingencyResults().stream()
                .allMatch(postContingencyResult -> postContingencyResult.getStatus() == PostContingencyComputationStatus.CONVERGED));
        return matrixFactory.matrixCount;
    }

    @Test
    void securityAnalysisOnOneMatrix() {
        Network network = IeeeCdfNetworkFactory.create118();
        assertEquals(1, countMatrices(network, new LoadFlowParameters(), network.getLineStream().map(Line::getId).toList()));
    }

    /**
     * The loss of a zero impedance line, l12 or l23, splits the zero impedance network: OLF removes the distribution
     * equations of the voltage controls it holds and creates them again, with other terms. They keep their positions, and
     * the matrix its matrix structure.
     */
    @Test
    void securityAnalysisOnOneMatrixWhenAZeroImpedanceNetworkSplits() {
        Network network = ZeroImpedanceNetworkFactory.createWithVoltageControl();
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        assertEquals(1, countMatrices(network, parameters, List.of("l01", "l23", "l12")));
    }

    /**
     * A transformer holds the voltage of a bus, from the first load flow or once the generators have settled. The
     * reactive balance of that bus then goes to the position of the tap: a controller position, in the matrix structure from the start.
     */
    @ParameterizedTest
    @EnumSource(value = OpenLoadFlowParameters.TransformerVoltageControlMode.class, names = {"WITH_GENERATOR_VOLTAGE_CONTROL", "AFTER_GENERATOR_VOLTAGE_CONTROL"})
    void securityAnalysisOnOneMatrixWithTransformerVoltageControl(OpenLoadFlowParameters.TransformerVoltageControlMode mode) {
        Network network = ZeroImpedanceNetworkFactory.createWithVoltageControl();
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false).setTransformerVoltageControlOn(true);
        OpenLoadFlowParameters.create(parameters).setTransformerVoltageControlMode(mode);
        assertEquals(1, countMatrices(network, parameters, List.of("l01", "l23")));
    }
}
