/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.ac.equations.*;

import java.util.*;

import static com.powsybl.openloadflow.equations.JacobianMatrixFastDecoupled.termHasDedicatedDerivative;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class EquationFastDecoupled<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    private Equation equation;

    protected Map<Variable<V>, List<EquationTerm<V, E>>> termsByVariable;

    public EquationFastDecoupled(Equation equation) {
        this.equation = equation;
        this.termsByVariable = equation.getTermsByVariable();
    }

    public Equation getEquation() {
        return equation;
    }

    public void derFastDecoupled(Equation.DerHandler<V> handler, int rangeIndex, boolean isPhySystem) {
        Objects.requireNonNull(handler);
        int variableIndex = 0;
        for (Map.Entry<Variable<V>, List<EquationTerm<V, E>>> e : termsByVariable.entrySet()) {
            Variable variable = e.getKey();
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

    public void computeDerivativeAndUpdateMatrix(
            Equation equation,
            Map.Entry<Variable<V>, List<EquationTerm<V, E>>> e,
            Variable variable,
            int variableIndex,
            Equation.DerHandler<V> handler) {

        double value = 0;

        for (EquationTerm term : e.getValue()) {
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
        int oldMatrixElementIndex = equation.matrixElementIndexes == null ? -1 : equation.matrixElementIndexes[variableIndex];
        int matrixElementIndex = handler.onDer(variable, value, oldMatrixElementIndex);
        if (equation.matrixElementIndexes == null) {
            equation.matrixElementIndexes = new int[equation.termsByVariable.size()];
        }
        equation.matrixElementIndexes[variableIndex] = matrixElementIndex;
    }

    // Build fast decoupled version of a term, if it has dedicated derivative
    private double computeDedicatedDerivative(EquationTerm term, Variable variable) {
        if (term instanceof ClosedBranchSide1ActiveFlowEquationTerm) {
            return new ClosedBranchSide1ActiveFlowFastDecoupledEquationTerm((ClosedBranchSide1ActiveFlowEquationTerm) term)
                    .derFastDecoupled(variable);
        } else if (term instanceof ClosedBranchSide2ActiveFlowEquationTerm) {
            return new ClosedBranchSide2ActiveFlowFastDecoupledEquationTerm((ClosedBranchSide2ActiveFlowEquationTerm) term)
                    .derFastDecoupled(variable);
        } else if (term instanceof ClosedBranchSide1ReactiveFlowEquationTerm) {
            return new ClosedBranchSide1ReactiveFlowFastDecoupledEquationTerm((ClosedBranchSide1ReactiveFlowEquationTerm) term)
                    .derFastDecoupled(variable);
        } else if (term instanceof ClosedBranchSide2ReactiveFlowEquationTerm) {
            return new ClosedBranchSide2ReactiveFlowFastDecoupledEquationTerm((ClosedBranchSide2ReactiveFlowEquationTerm) term)
                    .derFastDecoupled(variable);
        } else if (term instanceof ShuntCompensatorReactiveFlowEquationTerm) {
            return new ShuntCompensatorReactiveFlowFastDecoupledEquationTerm((ShuntCompensatorReactiveFlowEquationTerm) term)
                    .derFastDecoupled(variable);
        } else {
            throw new IllegalStateException("Unexpected term class: " + term.getClass());
        }
    }
}
