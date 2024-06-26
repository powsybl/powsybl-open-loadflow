/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.dc.equations.AbstractClosedBranchDcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;

import java.util.*;
import java.util.function.ObjDoubleConsumer;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class WoodburyEngine {

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private static void setAlphas(DcLoadFlowContext loadFlowContext, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                                  DenseMatrix contingenciesStates, int columnState, ObjDoubleConsumer<ComputedContingencyElement> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(loadFlowContext, lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = 1d / calculatePower(loadFlowContext, lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
        }
    }

    private static double calculatePower(DcLoadFlowContext loadFlowContext, LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        DcEquationSystemCreationParameters creationParameters = loadFlowContext.getParameters().getEquationSystemCreationParameters();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Calculate post-contingency states values using pre-contingency states values and some flow transfer factors (alphas).
     */
    private DenseMatrix computePostContingencyStates(DcLoadFlowContext loadFlowContext, DenseMatrix preContingencyStates, DenseMatrix contingenciesStates, Collection<ComputedContingencyElement> contingencyElements) {
        // fill the post contingency matrices
        DenseMatrix postContingencyStates = new DenseMatrix(preContingencyStates.getRowCount(), preContingencyStates.getColumnCount());
        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(loadFlowContext, contingencyElements, preContingencyStates, contingenciesStates, columnIndex, ComputedContingencyElement::setAlphaForPostContingencyState);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForPostContingencyState() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
                }
                postContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }
        return postContingencyStates;
    }

    /**
     * Compute post-contingency states values for each contingency of a list.
     */
    private List<DenseMatrix> computeStatesForContingencyList(DcLoadFlowContext loadFlowContext, DenseMatrix contingenciesStates,
                                                                                    Map<String, ComputedContingencyElement> contingencyElementByBranch, WoodburyEngineRhsReader reader) {

        List<DenseMatrix> postContingencyStatesByContingency = new ArrayList<>();
        reader.process((PropagatedContingency contingency, DenseMatrix preContingencyStates, Set<String> elementsToReconnect) -> {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .toList();
            DenseMatrix postContingencyStates = computePostContingencyStates(loadFlowContext, preContingencyStates, contingenciesStates, contingencyElements);
            postContingencyStatesByContingency.add(contingency.getIndex(), postContingencyStates);
        });
        return postContingencyStatesByContingency;
    }

    /**
     * Compute pre- and post-contingency angle states of a network, using Woodbury formula, and for a given connectivity break analysis.
     * Right hand side overrides should be provided when a contingency or a connectivity analysis result (group of contingencies
     * breaking connectivity) changes it (for example, in the case of a lost GLSK member).
     *
     * @param loadFlowContext the dc load flow context in which is the network.
     * @param connectivityBreakAnalysisResults the results of a connectivity break analysis (with groups of contingencies breaking connectivity identified).
     * @return pre- and post-contingency angle states.
     */
    public List<DenseMatrix> run(DcLoadFlowContext loadFlowContext, WoodburyEngineRhsReader reader,
                                    ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults) {
        Objects.requireNonNull(loadFlowContext);
        Objects.requireNonNull(reader);
        Objects.requireNonNull(connectivityBreakAnalysisResults);

        // get contingency elements indexed by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityBreakAnalysisResults.contingencyElementByBranch();

        // get states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = connectivityBreakAnalysisResults.contingenciesStates();

        return computeStatesForContingencyList(loadFlowContext,
                contingenciesStates, contingencyElementByBranch, reader);
    }
}
