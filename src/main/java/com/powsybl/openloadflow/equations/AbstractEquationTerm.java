/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

    protected Equation<V, E> equation;

    private EquationTerm<V, E> parent;

    protected boolean active;

    protected StateVector sv;

    protected AbstractEquationTerm() {
        this(true);
    }

    protected AbstractEquationTerm(boolean active) {
        this.active = active;
    }

    @Override
    public Set<Variable<V>> getActiveVariables() {
        return getVariables();
    }

    @Override
    public void setStateVector(StateVector sv) {
        this.sv = sv;
    }

    @Override
    public void setParent(EquationTerm<V, E> parent) {
        if (parent != null && equation != null) {
            this.parent = Objects.requireNonNull(parent);
            equation.getEquationSystem().notifyEquationChange(equation, EquationEventType.EQUATION_CHANGED);
        }
    }

    @Override
    public EquationTerm<V, E> getParent() {
        return parent;
    }

    @Override
    public List<EquationTerm<V, E>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public void setEquation(Equation<V, E> equation) {
        this.equation = Objects.requireNonNull(equation);
    }

    @Override
    public Equation<V, E> getEquation() {
        return equation;
    }

    @Override
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            equation.getEquationSystem().notifyEquationChange(equation, EquationEventType.EQUATION_CHANGED);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public double calculateSensi(DenseMatrix dx, int column) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean hasRhs() {
        return false;
    }

    @Override
    public double rhs() {
        return 0;
    }
}
