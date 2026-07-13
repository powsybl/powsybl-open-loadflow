/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An equation owning a single column of the Jacobian matrix but defined by several alternative bodies, only one of
 * them being active at a time.
 *
 * <p>The matrix structure contributed by this equation is the union of the sparsity patterns of all alternatives:
 * variables referenced by all alternatives stay registered in the {@link EquationSystemIndex}
 * (see {@link #keepsInactiveTermVariables()}) and the matrix elements of inactive alternatives are numerically
 * filled with zeros. Switching the active alternative with {@link #setActiveAlternative(int)} is therefore a pure
 * value update of the Jacobian matrix: the column set, the variable set and the sparsity pattern are preserved, so
 * the symbolic factorization of the matrix can be reused, only a numerical refactorization is needed.
 *
 * <p>This is intended for cases where the equation system alternates between equations during the resolution, like
 * PV/PQ bus switching (alternating between a reactive power balance equation and a voltage target equation), which
 * would otherwise require a full structural rebuild of the matrix.
 *
 * <p>The masking of inactive alternatives is done at evaluation/derivation level (see
 * {@link #isTermContributing(EquationTerm)}) and does NOT use the term activity flag: term activity keeps its usual
 * meaning (a term is deactivated when its element - branch, shunt, ... - is disabled in the network). An alternative
 * can thus be evaluated on its own at any time, whatever the active alternative is, with the usual term activity
 * semantic (see {@link Alternative#eval()}), e.g. to compute the reactive power at a bus while it is in voltage
 * control mode.
 *
 * <p>Terms added directly with {@link #addTerm(EquationTerm)} belong to the default alternative if one has been set
 * with {@link #setDefaultAlternative(int)} (so that existing code adding terms to the equation transparently fills
 * this alternative), otherwise they are common to all alternatives and always contribute.
 *
 * <p>The equation is registered in the equation system under its creation type (primary type), which defines its
 * identity and column ordering. The type and element number to use for target evaluation are the ones of the active
 * alternative, returned by {@link #getActiveType()} and {@link #getActiveElementNum()}.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
public class AlternativeEquation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends SingleEquation<V, E> {

    public final class Alternative implements Evaluable {

        private final E type;

        private final int elementNum;

        private final List<EquationTerm<V, E>> terms = new ArrayList<>();

        private Alternative(E type, int elementNum) {
            this.type = Objects.requireNonNull(type);
            this.elementNum = elementNum;
        }

        public E getType() {
            return type;
        }

        public int getElementNum() {
            return elementNum;
        }

        public List<EquationTerm<V, E>> getTerms() {
            return Collections.unmodifiableList(terms);
        }

        /**
         * The alternative equation this alternative belongs to. Useful to navigate from network elements holding
         * an alternative as evaluable (like {@code LfBus.getQ()}) back to the equation.
         */
        public AlternativeEquation<V, E> getEquation() {
            return AlternativeEquation.this;
        }

        /**
         * Evaluate this alternative whatever the active alternative of the equation is, with the usual term activity
         * semantic (terms of disabled elements are skipped).
         */
        @Override
        public double eval() {
            double value = 0;
            for (EquationTerm<V, E> term : terms) {
                if (term.isActive()) {
                    value += term.eval();
                }
            }
            return value;
        }
    }

    private final List<Alternative> alternatives = new ArrayList<>();

    private final Map<EquationTerm<V, E>, Integer> termAlternativeNums = new IdentityHashMap<>();

    private int activeAlternativeNum = 0;

    private int defaultAlternativeNum = -1;

    private int disabledAlternativeNum = -1;

    private int requestedAlternativeNum = 0;

    private boolean elementDisabled = false;

    AlternativeEquation(int elementNum, E type, EquationSystem<V, E> equationSystem) {
        super(elementNum, type, equationSystem);
    }

    /**
     * Add an alternative body to this equation. The first added alternative is the active one by default. Terms of
     * the other alternatives contribute zeros to the matrix but their variables stay registered, so that the matrix
     * structure covers all alternatives.
     *
     * @param type the equation type of the alternative, used for target evaluation when the alternative is active
     * @param elementNum the element number of the alternative, used for target evaluation when the alternative is
     *                   active (an alternative may refer to another element than the one of this equation, like with
     *                   remote voltage control)
     * @param terms the terms of the alternative
     * @return the index of the added alternative, to be used with {@link #setActiveAlternative(int)}
     */
    public int addAlternative(E type, int elementNum, List<? extends EquationTerm<V, E>> terms) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(terms);
        Alternative alternative = new Alternative(type, elementNum);
        int alternativeNum = alternatives.size();
        alternatives.add(alternative);
        for (EquationTerm<V, E> term : terms) {
            addTermToAlternative(term, alternativeNum);
        }
        return alternativeNum;
    }

    /**
     * Same as {@link #addAlternative(Enum, int, List)} with the element number of this equation.
     */
    public int addAlternative(E type, List<? extends EquationTerm<V, E>> terms) {
        return addAlternative(type, getElementNum(), terms);
    }

    /**
     * Add the alternative to activate when the element of this equation is disabled (typically a trivial equation
     * like {@code v = constant} or {@code phi = 0} keeping the variables of a disabled or islanded element in the
     * matrix structure with a well conditioned diagonal). Contrary to other alternatives, its terms contribute even
     * when deactivated, as disabling an element deactivates all its terms.
     *
     * @see #setElementDisabled(boolean)
     */
    public int addDisabledAlternative(E type, List<? extends EquationTerm<V, E>> terms) {
        if (disabledAlternativeNum != -1) {
            throw new PowsyblException(this + " already has a disabled alternative");
        }
        disabledAlternativeNum = addAlternative(type, terms);
        return disabledAlternativeNum;
    }

    @Override
    public boolean hasDisabledAlternative() {
        return disabledAlternativeNum != -1;
    }

    public boolean hasAlternative(E type) {
        Objects.requireNonNull(type);
        for (Alternative alternative : alternatives) {
            if (alternative.type == type) {
                return true;
            }
        }
        return false;
    }

    public boolean isElementDisabled() {
        return elementDisabled;
    }

    /**
     * Switch to the disabled alternative (element disabled) or restore the alternative that was active before
     * disabling, or requested while disabled (element re-enabled). The equation itself stays active: disabling is
     * fully represented by the alternative switch, preserving the matrix structure.
     */
    @Override
    public void setElementDisabled(boolean elementDisabled) {
        if (disabledAlternativeNum == -1) {
            throw new PowsyblException(this + " has no disabled alternative");
        }
        if (this.elementDisabled != elementDisabled) {
            this.elementDisabled = elementDisabled;
            if (elementDisabled) {
                requestedAlternativeNum = activeAlternativeNum;
                doSetActiveAlternative(disabledAlternativeNum);
            } else {
                doSetActiveAlternative(requestedAlternativeNum);
            }
        }
    }

    private void addTermToAlternative(EquationTerm<V, E> term, int alternativeNum) {
        super.addTerm(term);
        alternatives.get(alternativeNum).terms.add(term);
        termAlternativeNums.put(term, alternativeNum);
    }

    /**
     * Route terms later added with {@link #addTerm(EquationTerm)} to the given alternative, so that existing code
     * adding terms to the equation (branch flows, shunts, loads, ...) transparently fills this alternative.
     */
    public void setDefaultAlternative(int alternativeNum) {
        Objects.checkIndex(alternativeNum, alternatives.size());
        defaultAlternativeNum = alternativeNum;
    }

    @Override
    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        if (defaultAlternativeNum != -1) {
            addTermToAlternative(term, defaultAlternativeNum);
            return this;
        }
        // common term, contributing whatever the active alternative is
        return super.addTerm(term);
    }

    public int getAlternativeCount() {
        return alternatives.size();
    }

    public int getActiveAlternativeNum() {
        return activeAlternativeNum;
    }

    public Alternative getAlternative(int alternativeNum) {
        return alternatives.get(alternativeNum);
    }

    public Alternative getActiveAlternative() {
        return alternatives.get(activeAlternativeNum);
    }

    /**
     * Switch the active alternative. Terms of the other alternatives will contribute zeros to the matrix at next
     * update. The matrix structure is preserved, only values and targets are invalidated. While the element is
     * disabled, the switch is recorded and applied when the element is re-enabled.
     */
    public void setActiveAlternative(int alternativeNum) {
        Objects.checkIndex(alternativeNum, alternatives.size());
        if (elementDisabled) {
            // remember the alternative to restore at re-enabling
            requestedAlternativeNum = alternativeNum;
            return;
        }
        doSetActiveAlternative(alternativeNum);
    }

    private void doSetActiveAlternative(int alternativeNum) {
        if (alternativeNum != activeAlternativeNum) {
            activeAlternativeNum = alternativeNum;
            getEquationSystem().notifyEquationChange(this, EquationEventType.EQUATION_ALTERNATIVE_CHANGED);
        }
    }

    /**
     * Switch the active alternative to the first one declared with the given type.
     */
    public void setActiveAlternative(E type) {
        Objects.requireNonNull(type);
        for (int alternativeNum = 0; alternativeNum < alternatives.size(); alternativeNum++) {
            if (alternatives.get(alternativeNum).type == type) {
                setActiveAlternative(alternativeNum);
                return;
            }
        }
        throw new PowsyblException("No alternative of type " + type + " in " + this);
    }

    /**
     * A term contributes when it is active (its element is enabled) and belongs to the active alternative (or is a
     * common term). Terms of the disabled alternative contribute whatever their activity flag is: the element being
     * disabled, all its terms get deactivated, but the trivial equation must keep holding its variables.
     */
    @Override
    protected boolean isTermContributing(EquationTerm<V, E> term) {
        Integer alternativeNum = termAlternativeNums.get(term);
        if (alternativeNum == null) {
            return term.isActive(); // common term
        }
        if (alternativeNum != activeAlternativeNum) {
            return false;
        }
        return alternativeNum == disabledAlternativeNum || term.isActive();
    }

    @Override
    public E getActiveType() {
        return alternatives.isEmpty() ? getType() : getActiveAlternative().type;
    }

    @Override
    public int getActiveElementNum() {
        return alternatives.isEmpty() ? getElementNum() : getActiveAlternative().elementNum;
    }

    @Override
    public boolean keepsInactiveTermVariables() {
        return true;
    }

    @Override
    public String toString() {
        return "AlternativeEquation(elementNum=" + getElementNum() +
                ", type=" + getType() +
                ", activeType=" + getActiveType() +
                ", column=" + getColumn() + ")";
    }
}
