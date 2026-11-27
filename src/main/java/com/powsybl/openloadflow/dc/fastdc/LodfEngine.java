/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.SparseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Sparse DC LODF (Line Outage Distribution Factor) matrix: {@code LODF(l, k) = PTDF(l, k) / (1 - PTDF(k, k))}, where
 * {@code PTDF(l, k)} is the flow of {@code l} for a +1/-1 injection at the terminals of {@code k}. Which factors are
 * stored is set by {@link LodfParameters}.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
public final class LodfEngine {

    private final DcLoadFlowContext loadFlowContext;

    private final List<LfBranch> monitoredBranches;

    private final List<ClosedBranchSide1DcFlowEquationTerm> monitoredBranchEquations; // null if not closed

    private final LodfParameters parameters;

    // compressed sparse column arrays, filled column by column
    private final int[] columnStart;
    private final TIntArrayList rowIndices = new TIntArrayList();
    private final TDoubleArrayList values = new TDoubleArrayList();
    private final List<Integer> undefinedOutageIndices = new ArrayList<>();

    private LodfEngine(DcLoadFlowContext loadFlowContext, List<LfBranch> monitoredBranches, int outageCount, LodfParameters parameters) {
        this.loadFlowContext = loadFlowContext;
        this.monitoredBranches = monitoredBranches;
        EquationSystem<DcVariableType, DcEquationType> equationSystem = loadFlowContext.getEquationSystem();
        this.monitoredBranchEquations = monitoredBranches.stream()
                .map(branch -> isClosed(branch)
                        ? equationSystem.getEquationTerm(ElementType.BRANCH, branch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class)
                        : null)
                .toList();
        this.parameters = parameters;
        this.columnStart = new int[outageCount + 1];
    }

    /**
     * The context must have phase control off and minimal impedance on, as set by
     * {@link com.powsybl.openloadflow.sensi.LodfAnalysis}. A null or not closed branch gets an empty row as monitored
     * branch and is undefined as outage.
     *
     * @param monitoredBranches the rows
     * @param outagedBranches the columns
     */
    public static LodfResult run(DcLoadFlowContext loadFlowContext, List<LfBranch> monitoredBranches,
                                 List<LfBranch> outagedBranches, LodfParameters parameters) {
        if (monitoredBranches.isEmpty() || outagedBranches.isEmpty()) {
            throw new PowsyblException("LODF computation needs at least one monitored branch and one outaged branch");
        }
        // capped to the largest dense states matrix ComputedElement.initRhs accepts
        int equationCount = loadFlowContext.getEquationSystem().getIndex().getColumnCount();
        int outageBatchSize = Math.min(parameters.getOutageBatchSize(), Integer.MAX_VALUE / (equationCount * Double.BYTES));
        LodfEngine engine = new LodfEngine(loadFlowContext, monitoredBranches, outagedBranches.size(), parameters);
        for (int batchStart = 0; batchStart < outagedBranches.size(); batchStart += outageBatchSize) {
            int batchEnd = Math.min(batchStart + outageBatchSize, outagedBranches.size());
            engine.addColumns(outagedBranches.subList(batchStart, batchEnd), batchStart);
        }
        return engine.createResult();
    }

    private void addColumns(List<LfBranch> outagedBranches, int firstOutageIndex) {
        // one +1/-1 right-hand side per closed outaged branch, null otherwise
        List<ComputedContingencyElement> outageElements = outagedBranches.stream()
                .map(branch -> isClosed(branch)
                        ? new ComputedContingencyElement(new BranchContingency(branch.getId()), loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem())
                        : null)
                .toList();
        List<ComputedContingencyElement> elementsToSolve = outageElements.stream().filter(Objects::nonNull).toList();
        ComputedElement.setComputedElementIndexes(elementsToSolve);
        DenseMatrix states = elementsToSolve.isEmpty() ? DenseMatrix.EMPTY
                : ComputedElement.calculateElementsStates(loadFlowContext, elementsToSolve);
        for (int i = 0; i < outageElements.size(); i++) {
            int outageIndex = firstOutageIndex + i;
            columnStart[outageIndex] = values.size();
            ComputedContingencyElement outageElement = outageElements.get(i);
            if (outageElement == null) {
                undefinedOutageIndices.add(outageIndex);
            } else {
                addColumn(outageIndex, outageElement, states);
            }
        }
    }

    private void addColumn(int outageIndex, ComputedContingencyElement outageElement, DenseMatrix states) {
        // calculateSensi on the +1/-1 states column of k gives PTDF(., k)
        int stateColumn = outageElement.getComputedElementIndex();
        double ptdfKk = outageElement.getLfBranchEquation().calculateSensi(states, stateColumn);
        double denominator = 1d - ptdfKk;
        if (denominator < ConnectivityBreakAnalysis.CONNECTIVITY_LOSS_THRESHOLD) {
            // connectivity break or zero impedance branch
            undefinedOutageIndices.add(outageIndex);
            return;
        }
        LfBranch outagedBranch = outageElement.getLfBranch();
        int start = values.size();
        int diagonalCount = 0;
        for (int row = 0; row < monitoredBranches.size(); row++) {
            ClosedBranchSide1DcFlowEquationTerm monitoredBranchEquation = monitoredBranchEquations.get(row);
            if (monitoredBranchEquation != null) {
                boolean diagonal = monitoredBranches.get(row) == outagedBranch;
                double lodf = diagonal ? -1d : monitoredBranchEquation.calculateSensi(states, stateColumn) / denominator;
                if (Math.abs(lodf) >= parameters.getThreshold()) {
                    rowIndices.add(row);
                    values.add(lodf);
                    diagonalCount += diagonal ? 1 : 0;
                }
            }
        }
        if (values.size() - start - diagonalCount > parameters.getMaxFactorsPerOutage()) {
            keepLargestFactors(start, outagedBranch, parameters.getMaxFactorsPerOutage() + diagonalCount);
        }
    }

    /**
     * Keeps the {@code count} largest factors of the last column, the -1 of the outaged branch first, in row order.
     */
    private void keepLargestFactors(int start, LfBranch outagedBranch, int count) {
        int[] kept = IntStream.range(start, values.size()).boxed()
                .sorted(Comparator.comparingDouble(i -> monitoredBranches.get(rowIndices.get(i)) == outagedBranch
                        ? Double.NEGATIVE_INFINITY : -Math.abs(values.get(i))))
                .limit(count)
                .sorted()
                .mapToInt(Integer::intValue)
                .toArray();
        int[] keptRows = Arrays.stream(kept).map(rowIndices::get).toArray();
        double[] keptValues = Arrays.stream(kept).mapToDouble(values::get).toArray();
        rowIndices.remove(start, rowIndices.size() - start);
        values.remove(start, values.size() - start);
        rowIndices.add(keptRows);
        values.add(keptValues);
    }

    private LodfResult createResult() {
        int outageCount = columnStart.length - 1;
        columnStart[outageCount] = values.size();
        SparseMatrix lodfMatrix = new SparseMatrix(monitoredBranches.size(), outageCount, columnStart, rowIndices.toArray(), values.toArray());
        return new LodfResult(lodfMatrix, undefinedOutageIndices);
    }

    private static boolean isClosed(LfBranch branch) {
        return branch != null && branch.isConnectedAtBothSides();
    }
}
