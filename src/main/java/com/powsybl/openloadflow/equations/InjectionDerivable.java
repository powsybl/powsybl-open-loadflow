/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.util.Derivable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public class InjectionDerivable<V extends Enum<V> & Quantity> implements Derivable<V> {

    private final Equation<V, ?> equation;

    public InjectionDerivable(Equation<V, ?> equation) {
        Objects.requireNonNull(equation);
        this.equation = equation;
    }

    private Stream<? extends EquationTerm<V, ?>> getBranchTermStream() {
        return equation.getTerms().stream().filter(EquationTerm::isActive)
                .filter(t -> t.getElementType() == ElementType.BRANCH);
    }

    @Override
    public double der(Variable<V> variable) {
        // The variable part of the equation is   injectionPart+branchPart
        // Thus Variable injectionPart = - branchPart
        // And the derivative of the injection is the opposite of the derivative of the branch terms
        return -getBranchTermStream().mapToDouble(t -> t.der(variable)).sum();
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        // The variable part of the equation is   injectionPart+branchPart
        // Thus Variable injectionPart = - branchPart
        // And the sensitivity of the injection is the opposite of the sensitivity of the branch terms
        return -getBranchTermStream().mapToDouble(t -> t.calculateSensi(x, column)).sum();
    }

    @Override
    public double eval() {
        // The equation is
        // rhs = VariableInjectionPart+ branchPart
        // with rhs = - (cte injectionPart)  (for ex sum of targetQ)
        // -branchPart = variableInjectionPart + cteInjectionPart
        return -getBranchTermStream().mapToDouble(EquationTerm::eval).sum();
    }

    @Override
    public boolean isActive() {
        return getBranchTermStream().anyMatch(EquationTerm::isActive);
    }

    @Override
    public double eval(StateVector sv) {
        // The equation is
        // rhs = VariableInjectionPart+ branchPart
        // with rhs = - (cte injectionPart)  (for ex sum of targetQ)
        // -branchPart = variableInjectionPart + cteInjectionPart
        return -getBranchTermStream().mapToDouble(t -> t.eval(sv)).sum();
    }
}
