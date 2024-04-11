/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.ObjDoubleConsumer;

/**
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
public class WoodburyEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(WoodburyEngine.class);
    private final WoodburyEngineResult woodburyEngineResult;

    public WoodburyEngine() {
        this.woodburyEngineResult = new WoodburyEngineResult();
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
     * Fills the right hand side with +1/-1 to model a branch contingency.
     */
    private static void fillRhsContingency(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                           Collection<ComputedContingencyElement> contingencyElements, Matrix rhs) {
        for (ComputedContingencyElement element : contingencyElements) {
            LfBranch lfBranch = lfNetwork.getBranchById(element.getElement().getId());
            if (lfBranch.getBus1() == null || lfBranch.getBus2() == null) {
                continue;
            }
            LfBus bus1 = lfBranch.getBus1();
            LfBus bus2 = lfBranch.getBus2();
            if (bus1.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), -1);
            } else if (bus2.isSlack()) {
                Equation<DcVariableType, DcEquationType> p = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p.getColumn(), element.getContingencyIndex(), 1);
            } else {
                Equation<DcVariableType, DcEquationType> p1 = equationSystem.getEquation(bus1.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                Equation<DcVariableType, DcEquationType> p2 = equationSystem.getEquation(bus2.getNum(), DcEquationType.BUS_TARGET_P).orElseThrow(IllegalStateException::new);
                rhs.set(p1.getColumn(), element.getContingencyIndex(), 1);
                rhs.set(p2.getColumn(), element.getContingencyIndex(), -1);
            }
        }
    }

    public static DenseMatrix initContingencyRhs(LfNetwork lfNetwork, EquationSystem<DcVariableType, DcEquationType> equationSystem, Collection<ComputedContingencyElement> contingencyElements) {
        // otherwise, defining the rhs matrix will result in integer overflow
        int equationCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        int maxContingencyElements = Integer.MAX_VALUE / (equationCount * Double.BYTES);
        if (contingencyElements.size() > maxContingencyElements) {
            throw new PowsyblException("Too many contingency elements " + contingencyElements.size()
                    + ", maximum is " + maxContingencyElements + " for a system with " + equationCount + " equations");
        }

        DenseMatrix rhs = new DenseMatrix(equationCount, contingencyElements.size());
        fillRhsContingency(lfNetwork, equationSystem, contingencyElements, rhs);
        return rhs;
    }

    // TODO : remove from this class ?
    static DenseMatrix calculateContingenciesStates(DcLoadFlowContext loadFlowContext, Map<String, ComputedContingencyElement> contingencyElementByBranch) {
        DenseMatrix contingenciesStates = initContingencyRhs(loadFlowContext.getNetwork(), loadFlowContext.getEquationSystem(), contingencyElementByBranch.values()); // rhs with +1 -1 on contingency elements
        loadFlowContext.getJacobianMatrix().solveTransposed(contingenciesStates);
        return contingenciesStates;
    }

    private void processContingenciesBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext, DenseMatrix preContingencyStates,
                                                          WoodburyEngineRhsModifications rhsModification, DenseMatrix contingenciesStates, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                          ReportNode reporter) {

        // null and unused if slack bus is not distributed
        DenseMatrix newPreContingencyStatesForThisConnectivity = preContingencyStates;
        if (rhsModification.getNewInjectionRhsForAConnectivity().containsKey(connectivityAnalysisResult)) {
            newPreContingencyStatesForThisConnectivity = rhsModification.getNewInjectionRhsForAConnectivity().get(connectivityAnalysisResult);
            loadFlowContext.getJacobianMatrix().solveTransposed(newPreContingencyStatesForThisConnectivity);
        }

        double[] newFlowRhsForThisConnectivity = rhsModification.getNewFlowRhsForAConnectivity().get(connectivityAnalysisResult);
        DenseMatrix newFlowStatesForThisConnectivity = runDcLoadFlowOnTargetVector(loadFlowContext, newFlowRhsForThisConnectivity, reporter);

        calculateStateValuesForContingencyList(loadFlowContext, newFlowStatesForThisConnectivity, newPreContingencyStatesForThisConnectivity, rhsModification, contingenciesStates,
                connectivityAnalysisResult.getContingencies(), contingencyElementByBranch, connectivityAnalysisResult.getElementsToReconnect(), reporter);
    }

    private void calculateStateValuesForContingencyList(DcLoadFlowContext loadFlowContext, DenseMatrix flowStates, DenseMatrix preContingencyStates, WoodburyEngineRhsModifications rhsModifications,
                                                        DenseMatrix contingenciesStates, Collection<PropagatedContingency> contingencies, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                        Set<String> elementsToReconnect, ReportNode reporter) {
        for (PropagatedContingency contingency : contingencies) {
            Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .toList();

            DenseMatrix newFlowStates = flowStates;
            if (rhsModifications.getNewFlowRhsByPropagatedContingency().containsKey(contingency)) {
                double[] newFlowRhs = rhsModifications.getNewFlowRhsByPropagatedContingency().get(contingency);
                newFlowStates = runDcLoadFlowOnTargetVector(loadFlowContext, newFlowRhs, reporter);
            }

            DenseMatrix newPreContingencyStates = preContingencyStates;
            if (rhsModifications.getNewInjectionRhsByPropagatedContingency().containsKey(contingency)) {
                newPreContingencyStates = rhsModifications.getNewInjectionRhsByPropagatedContingency().get(contingency);
                loadFlowContext.getJacobianMatrix().solveTransposed(newPreContingencyStates);
            }

            calculateStateValuesForAContingency(loadFlowContext, newFlowStates, newPreContingencyStates, contingenciesStates,
                    contingency, contingencyElements);
        }
    }

    /**
     * Calculate values for post-contingency state using the pre-contingency state value and some flow transfer factors (alphas).
     */
    private void calculateStateValuesForAContingency(DcLoadFlowContext loadFlowContext, DenseMatrix flowStates, DenseMatrix preContingencyStates, DenseMatrix contingenciesStates,
                                                     PropagatedContingency contingency, Collection<ComputedContingencyElement> contingencyElements) {

        // add the post contingency matrices of flow states
        DenseMatrix postContingencyFlowStates = new DenseMatrix(flowStates.getRowCount(), 1);
        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
        for (int rowIndex = 0; rowIndex < flowStates.getRowCount(); rowIndex++) {
            double postContingencyFlowValue = flowStates.get(rowIndex, 0);
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyFlowValue += contingencyElement.getAlphaForFunctionReference() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
            }
            postContingencyFlowStates.set(rowIndex, 0, postContingencyFlowValue);
        }

        // add the post contingency matrices of the states
        DenseMatrix postContingencyInjectionStates = new DenseMatrix(preContingencyStates.getRowCount(), preContingencyStates.getColumnCount());
        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(loadFlowContext, contingencyElements, preContingencyStates, contingenciesStates, columnIndex, ComputedContingencyElement::setAlphaForStateValue);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForStateValue() * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
                }
                postContingencyInjectionStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }

        WoodburyEngineResult.WoodburyStates postContingencyStates = new WoodburyEngineResult.WoodburyStates(postContingencyFlowStates, postContingencyInjectionStates);
        woodburyEngineResult.addPostContingencyWoodburyStates(contingency, postContingencyStates);
    }

    private DenseMatrix runDcLoadFlowOnTargetVector(DcLoadFlowContext loadFlowContext, double[] targetVectorArray, ReportNode reporter) {
        boolean succeeded = DcLoadFlowEngine.solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reporter);
        if (!succeeded) {
            throw new PowsyblException("DC solver failed");
        }
        return new DenseMatrix(targetVectorArray.length, 1, targetVectorArray);
    }

    public WoodburyEngineResult run(DcLoadFlowContext loadFlowContext, double[] flowRhs, DenseMatrix injectionRhs, WoodburyEngineRhsModifications rhsModifications,
                                    ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityDataResult, ReportNode reporter) {

        // compute pre-contingency injection/flow states
        loadFlowContext.getJacobianMatrix().solveTransposed(injectionRhs);
        woodburyEngineResult.setPreContingencyInjectionStates(injectionRhs);

        DenseMatrix flowStates = runDcLoadFlowOnTargetVector(loadFlowContext, flowRhs, reporter);
        woodburyEngineResult.setPreContingencyFlowStates(flowRhs);

        // get contingency elements indexed by branch id
        Map<String, ComputedContingencyElement> contingencyElementByBranch = connectivityDataResult.contingencyElementByBranch();

        // get states with +1 -1 to model the contingencies
        DenseMatrix contingenciesStates = connectivityDataResult.contingenciesStates();

        LOGGER.info("Processing contingencies with no connectivity break");

        // calculate state values for contingencies with no connectivity break
        calculateStateValuesForContingencyList(loadFlowContext, flowStates, injectionRhs, rhsModifications, contingenciesStates, connectivityDataResult.nonBreakingConnectivityContingencies(), contingencyElementByBranch,
                Collections.emptySet(), reporter);

        LOGGER.info("Processing contingencies with connectivity break");

        // process contingencies with connectivity break
        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityDataResult.connectivityAnalysisResults()) {
            processContingenciesBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, injectionRhs, rhsModifications, contingenciesStates, contingencyElementByBranch, reporter);
        }

        return woodburyEngineResult;
    }
}
