/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.math.matrix.*;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.equations.fastdecoupled.*;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator;
import com.powsybl.openloadflow.ac.equations.vector.ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.ac.equations.AcEquationType.*;
import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class JacobianMatrixFastDecoupled
        extends JacobianMatrix<AcVariableType, AcEquationType> {

    private final int rangeIndex;

    private final boolean isPhiSystem;

    public JacobianMatrixFastDecoupled(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                       MatrixFactory matrixFactory,
                                       int rangeIndex,
                                       boolean isPhiSystem) {
        super(equationSystem, matrixFactory);
        this.rangeIndex = rangeIndex;
        this.isPhiSystem = isPhiSystem;
    }

    @Override
    public void onStateUpdate() {
        // Nothing to do
    }

    // List of Equation that require a dedicated derivative for Fast-Decoupled
    private static final Set<AcEquationType> EQUATIONS_WITH_DEDICATED_DERIVATE = Set.of(
            BUS_TARGET_P,
            BUS_TARGET_Q,
            BRANCH_TARGET_P,
            BRANCH_TARGET_Q,
            DISTR_Q,
            BUS_DISTR_SLACK_P
    );

    // Checks if equation has a dedicated derivative for Fast-Decoupled
    public static boolean equationHasDedicatedDerivative(Equation<AcVariableType, AcEquationType> equation) {
        return EQUATIONS_WITH_DEDICATED_DERIVATE.contains(equation.getType());
    }

    // List of EquationTerms that require a dedicated derivative for Fast-Decoupled
    private static final Set<String> TERMS_WITH_DEDICATED_DERIVATIVE = Set.of(
            ClosedBranchSide1ActiveFlowEquationTerm.class.getName(),
            ClosedBranchSide1ReactiveFlowEquationTerm.class.getName(),
            ClosedBranchSide2ActiveFlowEquationTerm.class.getName(),
            ClosedBranchSide2ReactiveFlowEquationTerm.class.getName(),
            OpenBranchSide1ReactiveFlowEquationTerm.class.getName(),
            OpenBranchSide2ReactiveFlowEquationTerm.class.getName(),
            ShuntCompensatorReactiveFlowEquationTerm.class.getName(),
            LoadModelReactiveFlowEquationTerm.class.getName()
    );

    // List of EquationTermArrayEvaluators that require a dedicated derivative for Fast-Decoupled
    private static final Set<String> TERMS_ARRAY_WITH_DEDICATED_DERIVATIVE = Set.of(
            ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator.class.getName(),
            ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator.class.getName(),
            ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator.class.getName(),
            ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator.class.getName()
    );

    // Checks if the term provided has a dedicated derivative
    private static boolean termHasDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term instanceof EquationTermArray.EquationTermArrayElementImpl<AcVariableType, AcEquationType> equationTermArrayElement) {
            return TERMS_ARRAY_WITH_DEDICATED_DERIVATIVE.contains(equationTermArrayElement.getEvaluator().getClass().getName());
        }
        if (term instanceof SingleEquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType> multiplyByTerm) {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(multiplyByTerm.getTerm().getClass().getName());
        } else {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(term.getClass().getName());
        }
    }

    private FastDecoupledEquationTerm buildFastDecoupledTerm(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term instanceof EquationTermArray.EquationTermArrayElementImpl<AcVariableType, AcEquationType> termArrayElement) {
            // Converting EquationTermArrayElement to corresponding fast decoupled term
            return switch (termArrayElement.getEvaluator()) {
                case ClosedBranchSide1ActiveFlowEquationTermArrayEvaluator closedP1Evaluator -> new ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm(closedP1Evaluator, termArrayElement.termElementNum);
                case ClosedBranchSide2ActiveFlowEquationTermArrayEvaluator closedP2Evaluator -> new ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(closedP2Evaluator, termArrayElement.termElementNum);
                case ClosedBranchSide1ReactiveFlowEquationTermArrayEvaluator closedQ1Evaluator -> new ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(closedQ1Evaluator, termArrayElement.termElementNum);
                case ClosedBranchSide2ReactiveFlowEquationTermArrayEvaluator closedQ2Evaluator -> new ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm(closedQ2Evaluator, termArrayElement.termElementNum);
                case null, default -> throw new IllegalStateException("Unexpected term array class: " + term.getClass());
            };
        }
        return switch (term) {
            // Converting SingleEquationTerm to corresponding fast decoupled term
            case ClosedBranchSide1ActiveFlowEquationTerm typedTerm ->
                new ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm(typedTerm);
            case ClosedBranchSide2ActiveFlowEquationTerm typedTerm ->
                new ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(typedTerm);
            case ClosedBranchSide1ReactiveFlowEquationTerm typedTerm ->
                new ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case ClosedBranchSide2ReactiveFlowEquationTerm typedTerm ->
                new ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case OpenBranchSide1ReactiveFlowEquationTerm typedTerm ->
                new OpenBranchSide1ReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case OpenBranchSide2ReactiveFlowEquationTerm typedTerm ->
                new OpenBranchSide2ReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case ShuntCompensatorReactiveFlowEquationTerm typedTerm ->
                new ShuntCompensatorReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case LoadModelReactiveFlowEquationTerm typedTerm ->
                new LoadModelReactiveFlowFastDecoupledEquationTerm(typedTerm);
            case null, default -> throw new IllegalStateException("Unexpected term class: " + term.getClass());
        };
    }

    // Build Fast-Decoupled version of a term, if it has dedicated derivative
    private double computeDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term, Variable<AcVariableType> variable) {
        if (term instanceof SingleEquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType> multiplyByTerm) {
            FastDecoupledEquationTerm fastDecoupledEquationTerm = buildFastDecoupledTerm(multiplyByTerm.getTerm());
            return new MultiplyByScalarFastDecoupledEquationTerm(multiplyByTerm.getScalar(), fastDecoupledEquationTerm)
                    .derFastDecoupled(variable);
        } else {
            return buildFastDecoupledTerm(term).derFastDecoupled(variable);
        }
    }

    public void computeDerivative(List<EquationTerm<AcVariableType, AcEquationType>> equationTerms,
                                                 Variable<AcVariableType> variable, Equation.DerHandler<AcVariableType> handler) {
        double value = 0;

        for (EquationTerm<AcVariableType, AcEquationType> term : equationTerms) {
            if (term.isActive()) {
                if (termHasDedicatedDerivative(term)) {
                    value += computeDedicatedDerivative(term, variable);
                } else {
                    // if term does not have a dedicated derivative, compute standard derivative
                    value += term.der(variable);
                }
            }
        }
        // init matrix
        handler.onDer(variable, value, -1);
    }

    private void derFastDecoupled(Equation<AcVariableType, AcEquationType> equation, Equation.DerHandler<AcVariableType> handler, int rangeIndex, boolean isPhiSystem) {
        Objects.requireNonNull(handler);
        for (Map.Entry<Variable<AcVariableType>, List<EquationTerm<AcVariableType, AcEquationType>>> e : equation.getTermsByVariable().entrySet()) {
            Variable<AcVariableType> variable = e.getKey();
            int row = variable.getRow();
            if (row != -1) {
                if (isPhiSystem) {
                    // for Phi equations, we only consider the (rangeIndex-1) first variables
                    if (row < rangeIndex) {
                        computeDerivative(e.getValue(), variable, handler);
                    }
                } else {
                    if (row >= rangeIndex) {
                        computeDerivative(e.getValue(), variable, handler);
                    }
                }
            }
        }
    }

    private void derEquationFastDecoupled(Equation<AcVariableType, AcEquationType> eq) {
        int column = eq.getColumn();
        if (isPhiSystem) {
            derFastDecoupled(eq, (variable, value, matrixElementIndex) -> {
                int row = variable.getRow();
                return matrix.addAndGetIndex(row, column, value);
            }, rangeIndex, true);
        } else {
            derFastDecoupled(eq, (variable, value, matrixElementIndex) -> {
                int row = variable.getRow();
                return matrix.addAndGetIndex(row - rangeIndex, column - rangeIndex, value);
            }, rangeIndex, false);
        }
    }

    @Override
    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        int singleEquationsRangeIndex = equationSystem.getEquationArrays().isEmpty() ? rangeIndex : equationSystem.getEquationArrays().stream().findFirst().orElseThrow().getFirstColumn();

        List<SingleEquation<AcVariableType, AcEquationType>> subsetSingleEquationsToSolve = isPhiSystem ? equationSystem.getIndex().getSortedSingleEquationsToSolve().subList(0, singleEquationsRangeIndex)
                : equationSystem.getIndex().getSortedSingleEquationsToSolve().subList(singleEquationsRangeIndex, equationSystem.getIndex().getSortedSingleEquationsToSolve().size());
        List<EquationArray<AcVariableType, AcEquationType>> subsetEquationArrays = isPhiSystem ? equationSystem.getEquationArrays().stream().filter(e -> e.getFirstColumn() < rangeIndex).toList()
                : equationSystem.getEquationArrays().stream().filter(e -> e.getFirstColumn() > rangeIndex).toList();

        int rowColumnCount = isPhiSystem ? rangeIndex : equationSystem.getIndex().getRowCount() - rangeIndex;

        int estimatedNonZeroValueCount = rowColumnCount * 3;
        matrix = matrixFactory.create(rowColumnCount, rowColumnCount, estimatedNonZeroValueCount);

        for (Equation<AcVariableType, AcEquationType> eq : subsetSingleEquationsToSolve) {
            derEquationFastDecoupled(eq);
        }

        for (EquationArray<AcVariableType, AcEquationType> eqArray : subsetEquationArrays) {
            for (int elementNum = 0; elementNum < eqArray.getElementCount(); elementNum++) {
                Equation<AcVariableType, AcEquationType> eq = eqArray.getElement(elementNum);
                if (!eq.isActive()) {
                    continue;
                }
                derEquationFastDecoupled(eq);
            }
        }

        if (isPhiSystem) {
            LOGGER.debug(PERFORMANCE_MARKER, "Fast-Decoupled Phi system Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        } else {
            LOGGER.debug(PERFORMANCE_MARKER, "Fast-Decoupled V system Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }
    }

    @Override
    protected void updateDer() {
        throw new IllegalStateException("Fast-Decoupled solver does not need to update its Jacobian matrices during a load flow calculation");
    }

}
