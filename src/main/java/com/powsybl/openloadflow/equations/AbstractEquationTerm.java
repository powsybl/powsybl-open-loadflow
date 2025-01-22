/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements EquationTerm<V, E> {

    private Equation<V, E> equation;

    private boolean active;

    // When attached to an equation, the active status is attached to the equation to enable faster iterations
    // without having to load the term
    private Supplier<Boolean> activeGetter = () -> active;

    private Consumer<Boolean> activeSetter = b -> active = b;

    protected StateVector sv;

    protected EquationTerm<V, E> self = this;

    protected AbstractEquationTerm() {
        this(true);
    }

    protected AbstractEquationTerm(boolean active) {
        this.active = active;
    }

    @Override
    public void setStateVector(StateVector sv) {
        this.sv = Objects.requireNonNull(sv);
    }

    @Override
    public List<EquationTerm<V, E>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public Equation<V, E> getEquation() {
        return equation;
    }

    @Override
    public void setEquation(Equation<V, E> equation, Supplier<Boolean> activeGetter, Consumer<Boolean> activeSetter) {
        this.equation = Objects.requireNonNull(equation);
        this.activeGetter = activeGetter;
        this.activeSetter = activeSetter;
    }

    @Override
    public void setActive(boolean active) {
        if (isActive() != active) {
            activeSetter.accept(active);
            equation.getEquationSystem().notifyEquationTermChange(self, active ? EquationTermEventType.EQUATION_TERM_ACTIVATED
                                                                               : EquationTermEventType.EQUATION_TERM_DEACTIVATED);
        }
    }

    @Override
    public boolean isActive() {
        return activeGetter.get();
    }

    @Override
    public void setSelf(EquationTerm<V, E> self) {
        this.self = Objects.requireNonNull(self);
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
