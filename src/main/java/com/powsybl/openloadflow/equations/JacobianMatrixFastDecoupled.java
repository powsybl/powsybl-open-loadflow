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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class JacobianMatrixFastDecoupled
        extends JacobianMatrix<AcVariableType, AcEquationType> {

    private final int rangeIndex;

    private final boolean isPhySystem;

    public JacobianMatrixFastDecoupled(EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                       MatrixFactory matrixFactory,
                                       int rangeIndex,
                                       boolean isPhySystem) {
        super(equationSystem, matrixFactory);
        this.rangeIndex = rangeIndex;
        this.isPhySystem = isPhySystem;
    }

    // List of EquationTerms that require a specific derivative for Fast Decoupled
    private static final Set<String> TERMS_WITH_DEDICATED_DERIVATIVE = Set.of(
            ClosedBranchSide1ActiveFlowEquationTerm.class.getName(),
            ClosedBranchSide1ReactiveFlowEquationTerm.class.getName(),
            ClosedBranchSide2ActiveFlowEquationTerm.class.getName(),
            ClosedBranchSide2ReactiveFlowEquationTerm.class.getName(),
            ShuntCompensatorReactiveFlowEquationTerm.class.getName()
    );

    // checks if the term provided has a dedicated derivative
    public static boolean termHasDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term.getClass().getName().equals(EquationTerm.MultiplyByScalarEquationTerm.class.getName())) {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) term).getTerm().getClass().getName());
        } else {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(term.getClass().getName());
        }
    }

    private AbstractFastDecoupledEquationTerm buildFastDecoupledTerm(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term instanceof ClosedBranchSide1ActiveFlowEquationTerm) {
            return new ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm((ClosedBranchSide1ActiveFlowEquationTerm) term);
        } else if (term instanceof ClosedBranchSide2ActiveFlowEquationTerm) {
            return new ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm((ClosedBranchSide2ActiveFlowEquationTerm) term);
        } else if (term instanceof ClosedBranchSide1ReactiveFlowEquationTerm) {
            return new ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm((ClosedBranchSide1ReactiveFlowEquationTerm) term);
        } else if (term instanceof ClosedBranchSide2ReactiveFlowEquationTerm) {
            return new ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm((ClosedBranchSide2ReactiveFlowEquationTerm) term);
        } else if (term instanceof ShuntCompensatorReactiveFlowEquationTerm) {
            return new ShuntCompensatorReactiveFlowFastDecoupledEquationTerm((ShuntCompensatorReactiveFlowEquationTerm) term);
        } else {
            throw new IllegalStateException("Unexpected term class: " + term.getClass());
        }
    }

    // Build fast decoupled version of a term, if it has dedicated derivative
    private double computeDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term, Variable<AcVariableType> variable) {
        if (term instanceof EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) {
            AbstractFastDecoupledEquationTerm fastDecoupledEquationTerm = buildFastDecoupledTerm(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) term).getTerm());
            return new MultiplyByScalarFastDecoupledEquationTerm(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) term).getScalar(), fastDecoupledEquationTerm)
                    .derFastDecoupled(variable);
        } else {
            return buildFastDecoupledTerm(term).derFastDecoupled(variable);
        }
    }

    public void computeDerivativeAndUpdateMatrix(Equation<AcVariableType, AcEquationType> equation, Map.Entry<Variable<AcVariableType>, List<EquationTerm<AcVariableType, AcEquationType>>> e,
                                                 Variable<AcVariableType> variable, int variableIndex, Equation.DerHandler<AcVariableType> handler) {

        double value = 0;

        for (EquationTerm<AcVariableType, AcEquationType> term : e.getValue()) {
            if (term.isActive()) {
                if (termHasDedicatedDerivative(term)) {
                    value += computeDedicatedDerivative(term, variable);
                } else {
                    // if term does not have a dedicated derivative, compute standard derivative
                    value += term.der(variable);
                }
            }
        }

        // update matrix
        int oldMatrixElementIndex = equation.getMatrixElementIndexes() == null ? -1 : equation.getMatrixElementIndexes()[variableIndex];
        int matrixElementIndex = handler.onDer(variable, value, oldMatrixElementIndex);
        if (equation.getMatrixElementIndexes() == null) {
            equation.setMatrixElementIndexes(new int[equation.getTermsByVariable().size()]);
        }
        equation.getMatrixElementIndexes()[variableIndex] = matrixElementIndex;
    }

    public void derFastDecoupled(Equation<AcVariableType, AcEquationType> equation, Equation.DerHandler<AcVariableType> handler, int rangeIndex, boolean isPhySystem) {
        Objects.requireNonNull(handler);
        int variableIndex = 0;
        for (Map.Entry<Variable<AcVariableType>, List<EquationTerm<AcVariableType, AcEquationType>>> e : equation.getTermsByVariable().entrySet()) {
            Variable<AcVariableType> variable = e.getKey();
            int row = variable.getRow();
            if (row != -1) {
                if (isPhySystem) {
                    // for Phi equations, we only consider the (rangeIndex-1) first variables
                    if (row < rangeIndex) {
                        computeDerivativeAndUpdateMatrix(equation, e, variable, variableIndex, handler);
                    }
                } else {
                    if (row >= rangeIndex) {
                        computeDerivativeAndUpdateMatrix(equation, e, variable, variableIndex, handler);
                    }
                }
                variableIndex++;
            }
        }
    }

    @Override
    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<AcVariableType, AcEquationType>> subsetEquationsToSolve = isPhySystem ? equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex)
                : equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());

        int rowColumnCount = subsetEquationsToSolve.size();

        int estimatedNonZeroValueCount = rowColumnCount * 3;
        matrix = matrixFactory.create(rowColumnCount, rowColumnCount, estimatedNonZeroValueCount);

        for (Equation<AcVariableType, AcEquationType> eq : subsetEquationsToSolve) {
            int column = eq.getColumn();
            if (isPhySystem) {
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

        LOGGER.debug(PERFORMANCE_MARKER, "Fast Decoupled Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    @Override
    protected void updateDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<AcVariableType, AcEquationType>> subsetEquationsToSolve = isPhySystem ? equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex)
                : equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());

        matrix.reset();
        for (Equation<AcVariableType, AcEquationType> eq : subsetEquationsToSolve) {
            derFastDecoupled(eq, (variable, value, matrixElementIndex) -> {
                matrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            }, rangeIndex, isPhySystem);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Fast Decoupled Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

}
