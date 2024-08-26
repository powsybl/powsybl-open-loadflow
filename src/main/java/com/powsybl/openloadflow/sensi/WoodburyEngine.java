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

    private final DenseMatrix actionsStates;

    private final List<ComputedActionElement> actionElements;

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates) {
        this(creationParameters, contingencyElements, contingenciesStates, List.of(), new DenseMatrix(0, 0));
    }

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<ComputedContingencyElement> contingencyElements,
                          DenseMatrix contingenciesStates, List<ComputedActionElement> actionELements, DenseMatrix actionsStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
        this.actionElements = Objects.requireNonNull(actionELements);
        this.actionsStates = Objects.requireNonNull(actionsStates);
    }

    private double calculatePower(LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private void setAlphas(DenseMatrix states, int columnState) {
        if (contingencyElements.size() ==  1 && actionElements.isEmpty()) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            element.setAlphaForPostContingencyState(b / a);
        } else {
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            ComputedActionElement.setLocalIndexes(actionElements);
            int size = contingencyElements.size() + actionElements.size();
            DenseMatrix rhs = new DenseMatrix(size, 1);
            DenseMatrix matrix = new DenseMatrix(size, size);
            // fill first part of the matrix
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                // go on contingencies to fill part 1 of the matrix
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = 1d / calculatePower(lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }

                // go on lf actions to fill part 2 of the matrix
                for (ComputedActionElement actionElement : actionElements) {
                    double value = - (actionsStates.get(p1.getPh1Var().getRow(), actionElement.getActionIndex())
                            - actionsStates.get(p1.getPh2Var().getRow(), actionElement.getActionIndex()));
                    matrix.set(element.getLocalIndex(), actionElement.getLocalIndex() + contingencyElements.size(), value);
                }
            }
            // fill second part of the matrix
            for (ComputedActionElement actionElement : actionElements) {
                LfBranch lfBranch = actionElement.getLfBranch(); // TODO : with is this unused ?
                ClosedBranchSide1DcFlowEquationTerm p1 = actionElement.getLfBranchEquation();
                rhs.set(contingencyElements.size() + actionElement.getLocalIndex(), 0,
                        states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState));

                // go on contingencies to fill part 3 of the matrix
                for (ComputedContingencyElement element : contingencyElements) {
                    double value = - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
                    matrix.set(actionElement.getLocalIndex() + contingencyElements.size(), element.getLocalIndex(), value);
                }

                // go on lf actions to fill part 4 of the matrix
                for (ComputedActionElement actionElement2 : actionElements) {
                    double value = 0d;
                    if (actionElement.equals(actionElement2)) {
                        int oldTapPosition = lfBranch.getPiModel().getTapPosition();
                        LfAction.TapPositionChange tapPositionChange = actionElement.getAction().getTapPositionChange();
                        int newTapPosition = tapPositionChange.isRelative() ? oldTapPosition + tapPositionChange.value() : tapPositionChange.value();
                        tapPositionChange.branch().getPiModel().setTapPosition(newTapPosition);
                        double powerAfterModif = calculatePower(lfBranch);
                        tapPositionChange.branch().getPiModel().setTapPosition(oldTapPosition);
                        double powerBeforeModif = calculatePower(lfBranch);
                        value = 1d / (powerBeforeModif - powerAfterModif); // TODO : update 0 for the new value of Y...
                    }
                    value = value - (actionsStates.get(p1.getPh1Var().getRow(), actionElement2.getActionIndex())
                            - actionsStates.get(p1.getPh2Var().getRow(), actionElement2.getActionIndex()));
                    matrix.set(actionElement.getLocalIndex() + contingencyElements.size(), actionElement2.getLocalIndex() + contingencyElements.size(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> element.setAlphaForPostContingencyState(rhs.get(element.getLocalIndex(), 0)));
            actionElements.forEach(element -> element.setAlphaForPostContingencyAndActionState(rhs.get(element.getLocalIndex() + contingencyElements.size(), 0)));
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
                    postContingencyValue += contingencyElement.getAlphaForPostContingencyState()
                            * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
                }
                postContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }
        return postContingencyStates;
    }

    /**
     * Calculate post-contingency states values using pre-contingency states values and some flow transfer factors (alphas).
     * @return an array of post-contingency voltage angle states.
     */
    // TODO : add an action as parameter, the engine is defined for a set of contingencies, and then we can call on with actions
    public double[] run(double[] preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        double[] postContingencyStates = new double[preContingencyStates.length];
        setAlphas(new DenseMatrix(preContingencyStates.length, 1, preContingencyStates), 0);
        for (int rowIndex = 0; rowIndex < preContingencyStates.length; rowIndex++) {
            double postContingencyValue = preContingencyStates[rowIndex];
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                postContingencyValue += contingencyElement.getAlphaForPostContingencyState()
                        * contingenciesStates.get(rowIndex, contingencyElement.getContingencyIndex());
            }
            for (ComputedActionElement actionElement : actionElements) {
                postContingencyValue += actionElement.getAlphaForPostContingencyAndActionState()
                        * actionsStates.get(rowIndex, actionElement.getActionIndex());
            }
            postContingencyStates[rowIndex] = postContingencyValue;
        }
        return postContingencyStates;
    }
}

