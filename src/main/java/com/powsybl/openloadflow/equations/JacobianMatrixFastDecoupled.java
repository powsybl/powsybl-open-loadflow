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

    // Checks if the term provided has a dedicated derivative
    private static boolean termHasDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term instanceof EquationTerm.MultiplyByScalarEquationTerm) {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(((EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType>) term).getTerm().getClass().getName());
        } else {
            return TERMS_WITH_DEDICATED_DERIVATIVE.contains(term.getClass().getName());
        }
    }

    private AbstractFastDecoupledEquationTerm buildFastDecoupledTerm(EquationTerm<AcVariableType, AcEquationType> term) {
        if (term instanceof ClosedBranchSide1ActiveFlowEquationTerm typedTerm) {
            return new ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof ClosedBranchSide2ActiveFlowEquationTerm typedTerm) {
            return new ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof ClosedBranchSide1ReactiveFlowEquationTerm typedTerm) {
            return new ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof ClosedBranchSide2ReactiveFlowEquationTerm typedTerm) {
            return new ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof OpenBranchSide1ReactiveFlowEquationTerm typedTerm) {
            return new OpenBranchSide1ReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof OpenBranchSide2ReactiveFlowEquationTerm typedTerm) {
            return new OpenBranchSide2ReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof ShuntCompensatorReactiveFlowEquationTerm typedTerm) {
            return new ShuntCompensatorReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else if (term instanceof LoadModelReactiveFlowEquationTerm typedTerm) {
            return new LoadModelReactiveFlowFastDecoupledEquationTerm(typedTerm);
        } else {
            throw new IllegalStateException("Unexpected term class: " + term.getClass());
        }
    }

    // Build Fast-Decoupled version of a term, if it has dedicated derivative
    private double computeDedicatedDerivative(EquationTerm<AcVariableType, AcEquationType> term, Variable<AcVariableType> variable) {
        if (term instanceof EquationTerm.MultiplyByScalarEquationTerm<AcVariableType, AcEquationType> multiplyByTerm) {
            AbstractFastDecoupledEquationTerm fastDecoupledEquationTerm = buildFastDecoupledTerm(multiplyByTerm.getTerm());
            return new MultiplyByScalarFastDecoupledEquationTerm(multiplyByTerm.getScalar(), fastDecoupledEquationTerm)
                    .derFastDecoupled(variable);
        } else {
            return buildFastDecoupledTerm(term).derFastDecoupled(variable);
        }
    }

    public void computeDerivative(Map.Entry<Variable<AcVariableType>, List<EquationTerm<AcVariableType, AcEquationType>>> e,
                                                 Variable<AcVariableType> variable, Equation.DerHandler<AcVariableType> handler) {
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
                        computeDerivative(e, variable, handler);
                    }
                } else {
                    if (row >= rangeIndex) {
                        computeDerivative(e, variable, handler);
                    }
                }
            }
        }
    }

    @Override
    protected void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        List<Equation<AcVariableType, AcEquationType>> subsetEquationsToSolve = isPhiSystem ? equationSystem.getIndex().getSortedEquationsToSolve().subList(0, rangeIndex)
                : equationSystem.getIndex().getSortedEquationsToSolve().subList(rangeIndex, equationSystem.getIndex().getSortedEquationsToSolve().size());

        int rowColumnCount = subsetEquationsToSolve.size();

        int estimatedNonZeroValueCount = rowColumnCount * 3;
        matrix = matrixFactory.create(rowColumnCount, rowColumnCount, estimatedNonZeroValueCount);

        for (Equation<AcVariableType, AcEquationType> eq : subsetEquationsToSolve) {
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
