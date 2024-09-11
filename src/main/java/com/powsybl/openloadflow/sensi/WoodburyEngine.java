/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.openloadflow.dc.equations.AbstractClosedBranchDcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.network.LfAction;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PiModel;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class WoodburyEngine {

    private final DcEquationSystemCreationParameters creationParameters;

    private final List<ComputedContingencyElement> contingencyElements;

    private final DenseMatrix contingenciesStates;

    private List<ComputedActionElement> actionElements;

    private DenseMatrix actionsStates;

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
    }

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates, List<ComputedActionElement> actionElements, DenseMatrix actionsStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
        this.actionElements = Objects.requireNonNull(actionElements);
        this.actionsStates = Objects.requireNonNull(actionsStates);
    }

    private double calculatePower(LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    // TODO : for now, only works if there is a contingency to be computed in woodbury engine
    private void setAlphas(DenseMatrix states, int columnState) {
        if (contingencyElements.size() == 1 && actionElements.isEmpty()) { // TODO : modify to use same logic when there is only 1 action
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getComputedElementIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getComputedElementIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            element.setAlphaForWoodburyComputation(b / a);
        } else {
            // set local indexes of computed elements to use them in small matrix computation
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            ComputedActionElement.setLocalIndexes(actionElements);
            int size = contingencyElements.size() + actionElements.size();
            DenseMatrix rhs = new DenseMatrix(size, 1);
            DenseMatrix matrix = new DenseMatrix(size, size);

            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                LfBranch lfBranch = contingencyElement.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = contingencyElement.getLfBranchEquation();
                rhs.set(contingencyElement.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                // loop on contingencies to fill up-left part of the small matrix
                for (ComputedContingencyElement contingencyElement2 : contingencyElements) {
                    double value = 0d;
                    if (contingencyElement.equals(contingencyElement2)) {
                        value = 1d / calculatePower(lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), contingencyElement2.getComputedElementIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), contingencyElement2.getComputedElementIndex()));
                    matrix.set(contingencyElement.getLocalIndex(), contingencyElement2.getLocalIndex(), value);
                }

                // loop on actions to fill up-right part of the matrix
                for (ComputedActionElement actionElement : actionElements) {
                    double value = -(actionsStates.get(p1.getPh1Var().getRow(), actionElement.getComputedElementIndex())
                            - actionsStates.get(p1.getPh2Var().getRow(), actionElement.getComputedElementIndex()));
                    matrix.set(contingencyElement.getLocalIndex(), actionElement.getLocalIndex() + contingencyElements.size(), value);
                }
            }

            for (ComputedActionElement actionElement : actionElements) {
                LfBranch lfBranch = actionElement.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = actionElement.getLfBranchEquation();
                int oldTapPosition = lfBranch.getPiModel().getTapPosition();
                LfAction.TapPositionChange tapPositionChange = actionElement.getAction().getTapPositionChange();
                int newTapPosition = tapPositionChange.isRelative() ? oldTapPosition + tapPositionChange.value() : tapPositionChange.value();
                tapPositionChange.branch().getPiModel().setTapPosition(newTapPosition);
                double newAlpha = lfBranch.getPiModel().getA1();
                tapPositionChange.branch().getPiModel().setTapPosition(oldTapPosition);
                rhs.set(contingencyElements.size() + actionElement.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState) + newAlpha
                );

                // loop on contingencies to fill down-left part of the small matrix
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    double value = -(contingenciesStates.get(p1.getPh1Var().getRow(), contingencyElement.getComputedElementIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), contingencyElement.getComputedElementIndex()));
                    matrix.set(actionElement.getLocalIndex() + contingencyElements.size(), contingencyElement.getLocalIndex(), value);
                }

                // loop on actions to fill down-right part of the matrix
                for (ComputedActionElement actionElement2 : actionElements) {
                    double value = 0d;
                    if (actionElement.equals(actionElement2)) {
//                        int oldTapPosition = lfBranch.getPiModel().getTapPosition();
//                        LfAction.TapPositionChange tapPositionChange = actionElement.getAction().getTapPositionChange();
//                        int newTapPosition = tapPositionChange.isRelative() ? oldTapPosition + tapPositionChange.value() : tapPositionChange.value();
                        tapPositionChange.branch().getPiModel().setTapPosition(newTapPosition);
                        double powerAfterModif = calculatePower(lfBranch);
                        tapPositionChange.branch().getPiModel().setTapPosition(oldTapPosition);
                        double powerBeforeModif = calculatePower(lfBranch);
                        value = 1d / (powerBeforeModif - powerAfterModif); // TODO : update 0 for the new value of Y...
                    }
                    value = value - (actionsStates.get(p1.getPh1Var().getRow(), actionElement2.getComputedElementIndex())
                            - actionsStates.get(p1.getPh2Var().getRow(), actionElement2.getComputedElementIndex()));
                    matrix.set(actionElement.getLocalIndex() + contingencyElements.size(), actionElement2.getLocalIndex() + contingencyElements.size(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> element.setAlphaForWoodburyComputation(rhs.get(element.getLocalIndex(), 0)));
            actionElements.forEach(element -> element.setAlphaForWoodburyComputation(rhs.get(element.getLocalIndex() + contingencyElements.size(), 0)));
        }
    }

    /**
     * Calculate post-contingency states values using pre-contingency states values and some flow transfer factors (alphas).
     * @return a matrix of post-contingency voltage angle states.
     */
    public DenseMatrix run(DenseMatrix preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        // fill the post contingency matrices
        DenseMatrix postContingencyStates = new DenseMatrix(preContingencyStates.getRowCount(), preContingencyStates.getColumnCount());
        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(preContingencyStates, columnIndex);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyValue = preContingencyStates.get(rowIndex, columnIndex);
                for (ComputedContingencyElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForWoodburyComputation()
                            * contingenciesStates.get(rowIndex, contingencyElement.getComputedElementIndex());
                }
                postContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }
        return postContingencyStates;
    }

    /**
     * Calculate post-contingency states values using pre-contingency states values and some flow transfer factors (alphas).
     * @return an array of post-contingency voltage angle states. // TODO : update
     */
    public double[] run(double[] preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        double[] postContingencyStates = new double[preContingencyStates.length];
        setAlphas(new DenseMatrix(preContingencyStates.length, 1, preContingencyStates), 0);
        for (int rowIndex = 0; rowIndex < preContingencyStates.length; rowIndex++) {
            double postContingencyValue = preContingencyStates[rowIndex];
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyValue += contingencyElement.getAlphaForWoodburyComputation()
                        * contingenciesStates.get(rowIndex, contingencyElement.getComputedElementIndex());
            }
            for (ComputedActionElement actionElement : actionElements) {
                postContingencyValue += actionElement.getAlphaForWoodburyComputation()
                        * actionsStates.get(rowIndex, actionElement.getComputedElementIndex());
            }
            postContingencyStates[rowIndex] = postContingencyValue;
        }
        return postContingencyStates;
    }
}

