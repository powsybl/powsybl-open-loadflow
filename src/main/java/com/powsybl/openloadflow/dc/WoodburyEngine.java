/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixException;
import com.powsybl.openloadflow.dc.equations.AbstractClosedBranchDcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ObjDoubleConsumer;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class WoodburyEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(WoodburyEngine.class);

    private void solveRhs(DcLoadFlowContext loadFlowContext, DenseMatrix rhs, ReportNode reporter) {
        try {
            loadFlowContext.getJacobianMatrix().solveTransposed(rhs);
        } catch (MatrixException e) {
            Reports.reportWoodburyEngineFailure(reporter, e.getMessage());
            LOGGER.error("Failed to solve linear system for Woodbury engine", e);
        }
    }

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

    private Map<PropagatedContingency, DenseMatrix> computeStatesForContingencyList(DcLoadFlowContext loadFlowContext, DenseMatrix preContingencyStates, WoodburyEngineRhsModifications rhsModifications,
                                                                                                            DenseMatrix contingenciesStates, Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                                                            Set<String> elementsToReconnect, ReportNode reporter) {

        HashMap<PropagatedContingency, DenseMatrix> postContingencyStatesByContingency = new HashMap<>();
        for (PropagatedContingency contingency : contingencies) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .toList();

            DenseMatrix preContingencyStatesOverride = preContingencyStates;
            if (rhsModifications.getRhsOverrideByPropagatedContingency(contingency).isPresent()) {
                preContingencyStatesOverride = rhsModifications.getRhsOverrideByPropagatedContingency(contingency).orElseThrow();
                solveRhs(loadFlowContext, preContingencyStatesOverride, reporter);
            }

            DenseMatrix postContingencyStates = computePostContingencyStates(loadFlowContext, preContingencyStatesOverride,
                    contingenciesStates, contingencyElements);
            postContingencyStatesByContingency.put(contingency, postContingencyStates);
        }
        return postContingencyStatesByContingency;
    }

    private Map<PropagatedContingency, DenseMatrix> processContingenciesBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext, DenseMatrix preContingencyStates,
                                                                                                                     WoodburyEngineRhsModifications rhsModification, DenseMatrix contingenciesStates, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                                                                                     ReportNode reporter) {

        // null and unused if slack bus is not distributed
        DenseMatrix preContingencyStatesOverrideForThisConnectivity = preContingencyStates;
        if (rhsModification.getRhsOverrideForAConnectivity(connectivityAnalysisResult).isPresent()) {
            preContingencyStatesOverrideForThisConnectivity = rhsModification.getRhsOverrideForAConnectivity(connectivityAnalysisResult).orElseThrow();
            solveRhs(loadFlowContext, preContingencyStatesOverrideForThisConnectivity, reporter);
        }

        return computeStatesForContingencyList(loadFlowContext, preContingencyStatesOverrideForThisConnectivity, rhsModification, contingenciesStates,
                connectivityAnalysisResult.getContingencies(), contingencyElementByBranch, connectivityAnalysisResult.getElementsToReconnect(), reporter);
    }

    public WoodburyEngineResult run(DcLoadFlowContext loadFlowContext, DenseMatrix rhs, WoodburyEngineRhsModifications rhsModifications,
                                    ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults, ReportNode reporter) {

        // compute pre-contingency states
        solveRhs(loadFlowContext, rhs, reporter); // states are now in rhs

        // get contingency elements indexed by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityBreakAnalysisResults.contingencyElementByBranch();

        // get states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = connectivityBreakAnalysisResults.contingenciesStates();

        LOGGER.info("Processing contingencies with no connectivity break");

        // calculate state values for contingencies with no connectivity break
        Map<PropagatedContingency, DenseMatrix> postContingencyStates = computeStatesForContingencyList(loadFlowContext, rhs, rhsModifications,
                contingenciesStates, connectivityBreakAnalysisResults.nonBreakingConnectivityContingencies(), contingencyElementByBranch,
                Collections.emptySet(), reporter);

        LOGGER.info("Processing contingencies with connectivity break");

        // process contingencies with connectivity break
        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityBreakAnalysisResults.connectivityAnalysisResults()) {
            Map<PropagatedContingency, DenseMatrix> postContingencyBreakingConnectivityStates = processContingenciesBreakingConnectivity(connectivityAnalysisResult,
                    loadFlowContext, rhs, rhsModifications, contingenciesStates, contingencyElementByBranch, reporter);
            postContingencyStates.putAll(postContingencyBreakingConnectivityStates);
        }

        return new WoodburyEngineResult(rhs, postContingencyStates);
    }
}
