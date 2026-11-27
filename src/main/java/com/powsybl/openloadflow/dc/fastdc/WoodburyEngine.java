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
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.AbstractLfBranchAction;
import com.powsybl.openloadflow.network.action.AbstractLfTapChangerAction;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfGeneratorAction;
import com.powsybl.openloadflow.network.action.LfLoadAction;

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

    // contingency elements: lost branches (ComputedContingencyElement) and lost HVDC AC emulation droop couplings
    // (ComputedHvdcAcEmulationElement), all indexed in the same contingenciesStates matrix
    private final List<? extends ComputedElement> contingencyElements;

    private final DenseMatrix contingenciesStates;

    // +1 -1 states of the HVDC AC emulation elements (they are computed separately from the branch contingency elements)
    private final DenseMatrix hvdcStates;

    private final List<ComputedElement> actionElements;

    private final DenseMatrix actionsStates;

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<? extends ComputedElement> contingencyElements,
                          DenseMatrix contingenciesStates) {
        this(creationParameters, contingencyElements, contingenciesStates, DenseMatrix.EMPTY, Collections.emptyList(), DenseMatrix.EMPTY);
    }

    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<? extends ComputedElement> contingencyElements,
                          DenseMatrix contingenciesStates, List<ComputedElement> actionElements, DenseMatrix actionsStates) {
        this(creationParameters, contingencyElements, contingenciesStates, DenseMatrix.EMPTY, actionElements, actionsStates);
    }

    /**
     * @param contingencyElements lost branches ({@link ComputedContingencyElement}, states in {@code contingenciesStates})
     *                            and lost HVDC AC emulation droop couplings ({@link ComputedHvdcAcEmulationElement}, states
     *                            in {@code hvdcStates})
     */
    public WoodburyEngine(DcEquationSystemCreationParameters creationParameters, List<? extends ComputedElement> contingencyElements,
                          DenseMatrix contingenciesStates, DenseMatrix hvdcStates, List<ComputedElement> actionElements, DenseMatrix actionsStates) {
        this.creationParameters = Objects.requireNonNull(creationParameters);
        this.contingencyElements = Objects.requireNonNull(contingencyElements);
        this.contingenciesStates = Objects.requireNonNull(contingenciesStates);
        this.hvdcStates = Objects.requireNonNull(hvdcStates);
        this.actionElements = Objects.requireNonNull(actionElements);
        this.actionsStates = Objects.requireNonNull(actionsStates);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling.
     * Note that it does not update the state vector and the network at the end (because we don't need it to just evaluate a few equations).
     */
    public static double[] runDcLoadFlowWithModifiedTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, ReportNode reportNode) {
        return runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, Collections.emptyList(), reportNode);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling, and pst actions.
     * Note that it does not update the state vector and the network at the end (because we don't need it to just evaluate a few equations).
     */
    public static double[] runDcLoadFlowWithModifiedTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork, List<LfAction> lfActions, ReportNode reportNode) {
        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();

        // apply generator/load (injection) actions on the network before distributing the slack, so that the slack
        // induced by the injection change is distributed over the participating elements exactly as in a full DC load
        // flow (the target vector listens to these network changes). As for the slack distribution itself, restoring the
        // network state modified here is the caller's responsibility.
        LfNetworkParameters networkParameters = parameters.getNetworkParameters();
        lfActions.stream().filter(LfGeneratorAction.class::isInstance).map(LfGeneratorAction.class::cast)
                .forEach(action -> action.apply(loadFlowContext.getNetwork(), null, networkParameters));
        lfActions.stream().filter(LfLoadAction.class::isInstance).map(LfLoadAction.class::cast)
                .forEach(action -> action.apply(loadFlowContext.getNetwork(), null, networkParameters));

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

        if (!disabledNetwork.getHvdcs().isEmpty()) {
            // an HVDC line in AC emulation (linear mode) injects the constant term p0 of its droop law in the target vector
            // through the right-hand side of its flow equation terms: when the line is lost, this injection is removed
            // (the droop coupling itself is removed through the Woodbury formula, see ComputedHvdcAcEmulationElement)
            for (LfHvdc hvdc : disabledNetwork.getHvdcs()) {
                if (!hvdc.isAcEmulation()) {
                    continue;
                }
                for (var term : loadFlowContext.getEquationSystem().getEquationTerms(ElementType.HVDC, hvdc.getNum())) {
                    var equation = term.getEquation();
                    LfBus bus = loadFlowContext.getNetwork().getBus(equation.getElementNum());
                    if (term.isActive() && term.hasRhs() && equation.isActive() && !disabledNetwork.getBuses().contains(bus)) {
                        targetVectorArray[equation.getColumn()] += term.rhs();
                    }
                }
            }
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!lfActions.isEmpty()) {
            // set transformer phase shift to new shifting value
            lfActions.stream()
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

            // set transformer phase shift to 0 for disabled phase tap changers by actions
            lfActions.stream()
                    .filter(AbstractLfBranchAction.class::isInstance)
                    .map(lfAction -> ((AbstractLfBranchAction<?>) lfAction).getDisabledBranches())
                    .filter(Objects::nonNull)
                    .flatMap(lfBranches -> lfBranches.stream().flatMap(b -> loadFlowContext.getEquationSystem().getEquation(b.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream()))
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
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
     * Returns the value of the right-hand side member, associated with the linear system to be solved in order to compute
     * the flow transfer factors.
     */
    private double getAlphaRhsValue(DenseMatrix states, ComputedElement element, int columnState) {
        double newAlpha = 0;
        if (element instanceof ComputedTapPositionChangeElement computedTapPositionChangeElement) {
            TapPositionChange tapPositionChange = computedTapPositionChangeElement.getTapPositionChange();
            PiModel newPiModel = tapPositionChange.getNewPiModel();
            newAlpha = newPiModel.getA1();
        } else if (element instanceof ComputedSwitchBranchElement switchElement && switchElement.isEnabled()
                && switchElement.getLfBranch().isDisabled()) {
            // when closing a branch that is currently disabled (absent from the base flow states), and if it is a phase
            // shifting transformer, its phase shift must be injected the same way a tap position change injects the new
            // phase shift. When the branch is already enabled in the base flow states (e.g. in fast DC sensitivity
            // analysis), its phase shift is already accounted for and must not be added again.
            newAlpha = switchElement.getLfBranch().getPiModel().getA1();
        }
        return states.get(element.getPh1VarRow(), columnState) - states.get(element.getPh2VarRow(), columnState) + newAlpha;
    }

    /**
     * Returns the value on the diagonal of the alpha matrix: the inverse of the power change of the element.
     * For a lost branch or a lost HVDC AC emulation droop coupling (virtual branch of power k), it is the inverse of the
     * power. For an action it depends on the type of change.
     */
    private double computeDeltaXForDiagonalValues(ComputedElement element) {
        if (element instanceof ComputedHvdcAcEmulationElement hvdcElement) {
            return 1d / hvdcElement.getPower();
        } else if (element instanceof ComputedContingencyElement contingencyElement) {
            return 1d / calculatePower(contingencyElement.getLfBranch());
        }
        double oldPower = 0;
        double newPower = 0;
        // if tap position change, the power transiting on the branch might have changed
        if (element instanceof ComputedTapPositionChangeElement tapChangeElement) {
            TapPositionChange tapPositionChange = tapChangeElement.getTapPositionChange();
            newPower = calculatePower(tapPositionChange.getNewPiModel());
            oldPower = calculatePower(tapChangeElement.getLfBranch());
        } else if (element instanceof ComputedSwitchBranchElement switchElement) {
            // if enabled, power is now transiting on the branch
            if (switchElement.isEnabled()) {
                newPower = calculatePower(switchElement.getLfBranch());
                // if disabled, no power is transiting on the branch anymore
            } else {
                oldPower = calculatePower(switchElement.getLfBranch());
            }
        } else {
            throw new IllegalStateException("Unexpected computed element type: " + element.getClass().getSimpleName());
        }
        return 1d / (oldPower - newPower);
    }

    private DenseMatrix getStates(ComputedElement element) {
        if (element instanceof ComputedContingencyElement) {
            return contingenciesStates;
        } else if (element instanceof ComputedHvdcAcEmulationElement) {
            return hvdcStates;
        }
        return actionsStates;
    }

    /**
     * Returns the value of the matrix associated with the linear system to be solved in order to compute the flow transfer factors.
     * The element of the row gives the angle variables (and the diagonal power change), the element of the column gives
     * the +1/-1 states to read.
     */
    private double getAlphaMatrixValue(ComputedElement rowElement, ComputedElement columnElement, boolean onDiagonal) {
        double deltaX = onDiagonal ? computeDeltaXForDiagonalValues(rowElement) : 0d;
        DenseMatrix states = getStates(columnElement);
        return deltaX - (states.get(rowElement.getPh1VarRow(), columnElement.getComputedElementIndex())
                - states.get(rowElement.getPh2VarRow(), columnElement.getComputedElementIndex()));
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency state values.
     */
    private void setAlphas(DenseMatrix states, int columnState) {
        if (contingencyElements.size() + actionElements.size() == 1) {
            ComputedElement element = actionElements.isEmpty() ? contingencyElements.iterator().next()
                    : actionElements.iterator().next();

            // we solve a*alpha = b
            double a = getAlphaMatrixValue(element, element, true);
            double b = getAlphaRhsValue(states, element, columnState);
            element.setAlphaForWoodburyComputation(b / a);
        } else {
            // set local indexes of computed elements to use them in small matrix computation
            // contingency elements first, then action elements
            List<ComputedElement> allElements = new ArrayList<>(contingencyElements.size() + actionElements.size());
            allElements.addAll(contingencyElements);
            allElements.addAll(actionElements);
            ComputedElement.setLocalIndexes(allElements);
            int size = allElements.size();
            DenseMatrix rhs = new DenseMatrix(size, 1);
            DenseMatrix matrix = new DenseMatrix(size, size);

            for (ComputedElement rowElement : allElements) {
                int i = rowElement.getLocalIndex();
                rhs.set(i, 0, getAlphaRhsValue(states, rowElement, columnState));
                for (ComputedElement columnElement : allElements) {
                    int j = columnElement.getLocalIndex();
                    matrix.set(i, j, getAlphaMatrixValue(rowElement, columnElement, i == j));
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            allElements.forEach(element -> element.setAlphaForWoodburyComputation(rhs.get(element.getLocalIndex(), 0)));
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
                for (ComputedElement contingencyElement : contingencyElements) {
                    postContingencyValue += contingencyElement.getAlphaForWoodburyComputation()
                            * getStates(contingencyElement).get(rowIndex, contingencyElement.getComputedElementIndex());
                }
                preContingencyStates.set(rowIndex, columnIndex, postContingencyValue);
            }
        }
    }

    /**
     * Calculate post-contingency and post-actions states values by modifying pre-contingency states values, using some flow transfer factors (alphas).
     */
    public void toPostContingencyAndOperatorStrategyStates(DenseMatrix preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        for (int columnIndex = 0; columnIndex < preContingencyStates.getColumnCount(); columnIndex++) {
            setAlphas(preContingencyStates, columnIndex);
            for (int rowIndex = 0; rowIndex < preContingencyStates.getRowCount(); rowIndex++) {
                double postContingencyAndOperatorStrategyValue = preContingencyStates.get(rowIndex, columnIndex);
                postContingencyAndOperatorStrategyValue = addToPostContingencyAndOperatorStrategyValue(postContingencyAndOperatorStrategyValue, rowIndex);
                preContingencyStates.set(rowIndex, columnIndex, postContingencyAndOperatorStrategyValue);
            }
        }
    }

    public void toPostContingencyAndOperatorStrategyStates(double[] preContingencyStates) {
        Objects.requireNonNull(preContingencyStates);
        setAlphas(new DenseMatrix(preContingencyStates.length, 1, preContingencyStates), 0);
        for (int rowIndex = 0; rowIndex < preContingencyStates.length; rowIndex++) {
            double postContingencyAndOperatorStrategyValue = preContingencyStates[rowIndex];
            postContingencyAndOperatorStrategyValue = addToPostContingencyAndOperatorStrategyValue(postContingencyAndOperatorStrategyValue, rowIndex);
            preContingencyStates[rowIndex] = postContingencyAndOperatorStrategyValue;
        }
    }

    private double addToPostContingencyAndOperatorStrategyValue(double postContingencyAndOperatorStrategyValue, int rowIndex) {
        double updatedPostContingencyAndOperatorStrategyValue = postContingencyAndOperatorStrategyValue;
        for (ComputedElement contingencyElement : contingencyElements) {
            updatedPostContingencyAndOperatorStrategyValue += contingencyElement.getAlphaForWoodburyComputation()
                    * getStates(contingencyElement).get(rowIndex, contingencyElement.getComputedElementIndex());
        }
        for (ComputedElement actionElement : actionElements) {
            updatedPostContingencyAndOperatorStrategyValue += actionElement.getAlphaForWoodburyComputation()
                    * actionsStates.get(rowIndex, actionElement.getComputedElementIndex());
        }
        return updatedPostContingencyAndOperatorStrategyValue;
    }
}
