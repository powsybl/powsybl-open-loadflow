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
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisProvider;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The AC Jacobian solved on the fixed structure or on the matrix of the equation index: the same results, with the
 * indexed structure keeping the behaviour of OLF before the fixed structure.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class AcJacobianStructureTest {

    private static final class CountingMatrixFactory extends SparseMatrixFactory {

        private int matrixCount;

        @Override
        public SparseMatrix create(int rowCount, int columnCount, int estimatedValueCount) {
            matrixCount++;
            return super.create(rowCount, columnCount, estimatedValueCount);
        }
    }

    private record BusState(double[] v, double[] angle) {
    }

    private static BusState runLoadFlow(AcJacobianStructure structure) {
        Network network = IeeeCdfNetworkFactory.create118();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters).setAcJacobianStructure(structure);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        assertEquals(structure, acParameters.getJacobianStructure());
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            AcLoadFlowResult result = new AcloadFlowEngine(context).run();
            assertEquals(AcSolverStatus.CONVERGED, result.getSolverStatus());
            assertEquals(structure == AcJacobianStructure.FIXED, context.getJacobianMatrix() instanceof AcFixedJacobianMatrix);
            List<LfBus> buses = lfNetwork.getBuses();
            return new BusState(buses.stream().mapToDouble(LfBus::getV).toArray(),
                                buses.stream().mapToDouble(LfBus::getAngle).toArray());
        }
    }

    @Test
    void sameLoadFlowResults() {
        BusState fixed = runLoadFlow(AcJacobianStructure.FIXED);
        BusState indexed = runLoadFlow(AcJacobianStructure.INDEXED);
        assertArrayEquals(fixed.v(), indexed.v(), 1e-9);
        assertArrayEquals(fixed.angle(), indexed.angle(), 1e-9);
    }

    private static SecurityAnalysisResult runSecurityAnalysis(Network network, AcJacobianStructure structure,
                                                              CountingMatrixFactory matrixFactory) {
        List<Contingency> contingencies = network.getLineStream().map(Line::getId)
                .map(lineId -> new Contingency(lineId, new BranchContingency(lineId)))
                .toList();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters).setAcJacobianStructure(structure);
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
                .allMatch(r -> r.getStatus() == PostContingencyComputationStatus.CONVERGED));
        return result;
    }

    @Test
    void sameSecurityAnalysisResults() {
        Network network = IeeeCdfNetworkFactory.create118();
        CountingMatrixFactory fixedMatrices = new CountingMatrixFactory();
        CountingMatrixFactory indexedMatrices = new CountingMatrixFactory();
        SecurityAnalysisResult fixed = runSecurityAnalysis(network, AcJacobianStructure.FIXED, fixedMatrices);
        SecurityAnalysisResult indexed = runSecurityAnalysis(network, AcJacobianStructure.INDEXED, indexedMatrices);

        // the fixed structure analyses one matrix; the indexed one builds a matrix each time the equations change
        assertEquals(1, fixedMatrices.matrixCount);
        assertTrue(indexedMatrices.matrixCount > 1);

        for (int i = 0; i < fixed.getPostContingencyResults().size(); i++) {
            PostContingencyResult f = fixed.getPostContingencyResults().get(i);
            PostContingencyResult x = indexed.getPostContingencyResults().get(i);
            assertEquals(f.getContingency().getId(), x.getContingency().getId());
            var fBranches = f.getNetworkResult().getBranchResults();
            var xBranches = x.getNetworkResult().getBranchResults();
            assertEquals(fBranches.size(), xBranches.size());
            for (int k = 0; k < fBranches.size(); k++) {
                assertEquals(fBranches.get(k).getBranchId(), xBranches.get(k).getBranchId());
                assertEquals(fBranches.get(k).getP1(), xBranches.get(k).getP1(), 1e-6);
                assertEquals(fBranches.get(k).getQ1(), xBranches.get(k).getQ1(), 1e-6);
            }
            assertEquals(f.getLimitViolationsResult().getLimitViolations().size(),
                         x.getLimitViolationsResult().getLimitViolations().size());
        }
    }

    @Test
    void indexedStructureSolvesOnTheMatrixOfTheIndex() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters).setAcJacobianStructure(AcJacobianStructure.INDEXED);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false);
        LfNetwork lfNetwork = Networks.load(network, acParameters.getNetworkParameters()).get(0);
        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context).run();
            assertFalse(context.getJacobianMatrix() instanceof AcFixedJacobianMatrix);
        }
    }

    @Test
    void parameter() {
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters();
        assertEquals(AcJacobianStructure.FIXED, parametersExt.getAcJacobianStructure());
        parametersExt.update(Map.of(OpenLoadFlowParameters.AC_JACOBIAN_STRUCTURE_PARAM_NAME, "INDEXED"));
        assertEquals(AcJacobianStructure.INDEXED, parametersExt.getAcJacobianStructure());
        assertEquals(AcJacobianStructure.INDEXED, OpenLoadFlowParameters.clone(parametersExt).getAcJacobianStructure());
        LoadFlowParameters indexed = new LoadFlowParameters();
        indexed.addExtension(OpenLoadFlowParameters.class, parametersExt);
        LoadFlowParameters fixed = new LoadFlowParameters();
        fixed.addExtension(OpenLoadFlowParameters.class, new OpenLoadFlowParameters());
        assertFalse(OpenLoadFlowParameters.equals(fixed, indexed));
        assertThrows(NullPointerException.class, () -> parametersExt.setAcJacobianStructure(null));
    }
}
