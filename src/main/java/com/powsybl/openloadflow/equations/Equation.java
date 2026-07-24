/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface Equation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    E getType();

    int getElementNum();

    /**
     * Type of the active alternative for a {@link AlternativeEquation}, same as {@link #getType()} otherwise.
     * This is the type to use when evaluating the equation target.
     */
    default E getActiveType() {
        return getType();
    }

    /**
     * Element number of the active alternative for a {@link AlternativeEquation}, same as {@link #getElementNum()}
     * otherwise. This is the element number to use when evaluating the equation target.
     */
    default int getActiveElementNum() {
        return getElementNum();
    }

    /**
     * Whether variables referenced by inactive terms of this equation must be kept registered in the
     * {@link EquationSystemIndex}, so that term activation/deactivation never changes the matrix structure.
     * This is the case for {@link AlternativeEquation} where the matrix structure is the union of the sparsity
     * patterns of all alternatives.
     */
    default boolean keepsInactiveTermVariables() {
        return false;
    }

    /**
     * Whether this equation has an alternative body to switch to when its element is disabled (typically a trivial
     * equation keeping the element variables in the matrix structure), see
     * {@link AlternativeEquation#addDisabledAlternative}.
     */
    default boolean hasDisabledAlternative() {
        return false;
    }

    /**
     * Switch to/from the disabled alternative, preserving the matrix structure; the equation itself stays active.
     * Only supported when {@link #hasDisabledAlternative()} is true.
     */
    default void setElementDisabled(boolean disabled) {
        throw new UnsupportedOperationException("No disabled alternative");
    }

    void setActive(boolean active);

    Equation<V, E> addTerm(EquationTerm<V, E> term);

    <T extends EquationTerm<V, E>> Equation<V, E> addTerms(List<T> terms);

    boolean isActive();

    int getColumn();

    <T extends EquationTerm<V, E>> List<T> getTerms();

    <T extends EquationTerm<V, E>> Map<Variable<V>, List<T>> getTermsByVariable();

    EquationSystem<V, E> getEquationSystem();

    interface DerHandler<V extends Enum<V> & Quantity> {
        int onDer(Variable<V> variable, double value, int matrixElementIndex);
    }
}
