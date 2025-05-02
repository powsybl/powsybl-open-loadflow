/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.AbstractClosedBranchDcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfTapChangerAction;
import com.powsybl.openloadflow.network.action.LfAction;

import java.util.*;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.distributeSlack;
import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solve;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 * @author Pierre Arvy {@literal <pierre.arvy@artelys.com}
 */
public class WoodburyEngine {

    private final DcEquationSystemCreationParameters creationParameters;

    private final List<ComputedContingencyElement> contingencyElements;

    private final DenseMatrix contingenciesStates;

    private final List<ComputedTapPositionChangeElement> tapPositionChangeElements;

    private final DenseMatrix tapPositionChangeStates;

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
        this.tapPositionChangeElements = Collections.emptyList();
        this.tapPositionChangeStates = DenseMatrix.EMPTY;
    }

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates, List<ComputedTapPositionChangeElement> tapPositionChangeElements, DenseMatrix tapPositionChangeStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
        this.tapPositionChangeElements = Objects.requireNonNull(tapPositionChangeElements);
        this.tapPositionChangeStates = Objects.requireNonNull(tapPositionChangeStates);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling.
     * Note that it does not update the state vector and the network at the end (because we don't need it to just evaluate a few equations).
     */
    public static double[] runDcLoadFlowWithModifiedTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, ReportNode reportNode) {
        return runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, reportNode, Collections.emptyList());
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling, and pst actions.
     * Note that it does not update the state vector and the network at the end (because we don't need it to just evaluate a few equations).
     */
    public static double[] runDcLoadFlowWithModifiedTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, ReportNode reportNode, List<LfAction> pstActions) {
        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            distributeSlack(loadFlowContext.getNetwork(), remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwritten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();

        if (!disabledNetwork.getBuses().isEmpty()) {
            // set buses injections and transformers to 0
            disabledNetwork.getBuses().stream()
                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!pstActions.isEmpty()) {
            // set transformer phase shift to new shifting value
            pstActions.stream()
                    .filter(AbstractLfTapChangerAction.class::isInstance)
                    .map(action -> ((AbstractLfTapChangerAction<?>) action).getChange())
                    .filter(Objects::nonNull)
                    .forEach(tapPositionChange -> {
                        LfBranch lfBranch = tapPositionChange.getBranch();
                        loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).ifPresent(
                                dcVariableTypeDcEquationTypeEquation -> {
                                    int column = dcVariableTypeDcEquationTypeEquation.getColumn();
                                    targetVectorArray[column] = tapPositionChange.getNewPiModel().getA1();
                                }
                        );
                    });
        }

        boolean succeeded = solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reportNode);
        if (!succeeded) {
            throw new PowsyblException("DC solver failed");
        }

        return targetVectorArray; // now contains dx
    }

    private double calculatePower(LfBranch lfBranch) {
        return calculatePower(lfBranch.getPiModel());
    }

    private double calculatePower(PiModel piModel) {
        return AbstractClosedBranchDcFlowEquationTerm.computePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private void setAlphas(DenseMatrix states, int columnState) {
        if (contingencyElements.size() == 1 && tapPositionChangeElements.isEmpty()) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();

            // we solve a*alpha = b
            double a = 1d / calculatePower(lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getComputedElementIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getComputedElementIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            element.setAlphaForWoodburyComputation(b / a);
        } else if (contingencyElements.isEmpty() && tapPositionChangeElements.size() == 1) {
            ComputedTapPositionChangeElement element = tapPositionChangeElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();

            // we solve a*alpha = b
            PiModel newPiModel = element.getTapPositionChange().getNewPiModel();
            double deltaX = 1d / (calculatePower(lfBranch) - calculatePower(newPiModel));
            double a = deltaX - (tapPositionChangeStates.get(p1.getPh1Var().getRow(), element.getComputedElementIndex())
                    - tapPositionChangeStates.get(p1.getPh2Var().getRow(), element.getComputedElementIndex()));

            double newAlpha = newPiModel.getA1();
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState) + newAlpha;
            element.setAlphaForWoodburyComputation(b / a);
        } else {
            // set local indexes of computed elements to use them in small matrix computation
            ComputedElement.setLocalIndexes(contingencyElements);
            ComputedElement.setLocalIndexes(tapPositionChangeElements);
            int size = contingencyElements.size() + tapPositionChangeElements.size();
            DenseMatrix rhs = new DenseMatrix(size, 1);
            DenseMatrix matrix = new DenseMatrix(size, size);

            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                int i = contingencyElement.getLocalIndex();
                LfBranch lfBranch = contingencyElement.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = contingencyElement.getLfBranchEquation();
                rhs.set(i, 0, states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState));

                // loop on contingencies to fill top-left quadrant of the matrix
                for (ComputedContingencyElement contingencyElement2 : contingencyElements) {
                    int j = contingencyElement2.getLocalIndex();
                    // if on the diagonal of the matrix, add variation of reactance
                    double deltaX = (i == j) ? 1d / calculatePower(lfBranch) : 0d;
                    double value = deltaX - (contingenciesStates.get(p1.getPh1Var().getRow(), contingencyElement2.getComputedElementIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), contingencyElement2.getComputedElementIndex()));
                    matrix.set(i, j, value);
                }

                // loop on actions to fill top-right quadrant of the matrix
                for (ComputedTapPositionChangeElement tapPositionChangeElement : tapPositionChangeElements) {
                    int j = contingencyElements.size() + tapPositionChangeElement.getLocalIndex();
                    double value = -(tapPositionChangeStates.get(p1.getPh1Var().getRow(), tapPositionChangeElement.getComputedElementIndex())
                            - tapPositionChangeStates.get(p1.getPh2Var().getRow(), tapPositionChangeElement.getComputedElementIndex()));
                    matrix.set(i, j, value);
                }
            }

            for (ComputedTapPositionChangeElement tapPositionChangeElement : tapPositionChangeElements) {
                int i = contingencyElements.size() + tapPositionChangeElement.getLocalIndex();
                LfBranch lfBranch = tapPositionChangeElement.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = tapPositionChangeElement.getLfBranchEquation();

                PiModel newPiModel = tapPositionChangeElement.getTapPositionChange().getNewPiModel();
                double newAlpha = newPiModel.getA1();
                rhs.set(i, 0, states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState) + newAlpha);

                // loop on contingencies to fill bottom-left quadrant of the matrix
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    int j = contingencyElement.getLocalIndex();
                    double value = -(contingenciesStates.get(p1.getPh1Var().getRow(), contingencyElement.getComputedElementIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), contingencyElement.getComputedElementIndex()));
                    matrix.set(i, j, value);
                }

                // loop on actions to fill bottom-right quadrant of the matrix
                for (ComputedTapPositionChangeElement tapPositionChangeElement2 : tapPositionChangeElements) {
                    int j = contingencyElements.size() + tapPositionChangeElement2.getLocalIndex();
                    // if on the diagonal of the matrix, add variation of reactance
                    double deltaX = (i == j) ? 1d / (calculatePower(lfBranch) - calculatePower(newPiModel)) : 0d;
                    double value = deltaX - (tapPositionChangeStates.get(p1.getPh1Var().getRow(), tapPositionChangeElement2.getComputedElementIndex())
                            - tapPositionChangeStates.get(p1.getPh2Var().getRow(), tapPositionChangeElement2.getComputedElementIndex()));
                    matrix.set(i, j, value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> element.setAlphaForWoodburyComputation(rhs.get(element.getLocalIndex(), 0)));
            tapPositionChangeElements.forEach(element -> element.setAlphaForWoodburyComputation(rhs.get(contingencyElements.size() + element.getLocalIndex(), 0)));
        }
    }

    /**
     * Calculate post-contingency states values by modifying pre-contingency states values, using some flow transfer factors (alphas).
     */
    public void toPostContingencyStates(DenseMatrix preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);

        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(preContingencyStates, columnIndex);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForWoodburyComputation()
                            * contingenciesStates.get(rowIndex, contingencyElement.getComputedElementIndex());
                }
                preContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }
    }

    /**
     * Calculate post-contingency and post-actions states values by modifying pre-contingency states values, using some flow transfer factors (alphas).
     */
    public void toPostContingencyAndOperatorStrategyStates(double[] preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        setAlphas(new DenseMatrix(preContingencyStates.length, 1, preContingencyStates), 0);
        for (int rowIndex = 0; rowIndex < preContingencyStates.length; rowIndex++) {
            double postContingencyAndOperatorStrategyValue = preContingencyStates[rowIndex];
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyAndOperatorStrategyValue += contingencyElement.getAlphaForWoodburyComputation()
                        * contingenciesStates.get(rowIndex, contingencyElement.getComputedElementIndex());
            }
            for (ComputedTapPositionChangeElement tapPositionChangeElement : tapPositionChangeElements) {
                postContingencyAndOperatorStrategyValue += tapPositionChangeElement.getAlphaForWoodburyComputation()
                        * tapPositionChangeStates.get(rowIndex, tapPositionChangeElement.getComputedElementIndex());
            }
            preContingencyStates[rowIndex] = postContingencyAndOperatorStrategyValue;
        }
    }
}

