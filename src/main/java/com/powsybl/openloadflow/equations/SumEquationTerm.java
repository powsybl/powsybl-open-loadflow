/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SumEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    private final List<EquationTerm<V, E>> terms = new ArrayList<>();

    private final Set<Variable<V>> variables = new HashSet<>();

    private boolean hasRhs = false;

    public SumEquationTerm<V, E> addTerm(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        if (term.getParent() != null) {
            throw new PowsyblException("Equation term already added to another parent: "
                    + term.getParent());
        }
        terms.add(term);
        term.setEquation(equation);
        term.setParent(this);
        term.setStateVector(sv);
        variables.addAll(term.getVariables());
        hasRhs |= term.hasRhs();
        equation.getEquationSystem().notifyEquationTermChange(term, EquationTermEventType.EQUATION_TERM_ADDED);
        return this;
    }

    public SumEquationTerm<V, E> addTerms(List<EquationTerm<V, E>> terms) {
        Objects.requireNonNull(terms);
        for (EquationTerm<V, E> term : terms) {
            addTerm(term);
        }
        return this;
    }

    @Override
    public List<EquationTerm<V, E>> getChildren() {
        return terms;
    }

    @Override
    public ElementType getElementType() {
        return null;
    }

    @Override
    public int getElementNum() {
        return -1;
    }

    @Override
    public List<Variable<V>> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm<V, E> term : terms) {
            if (term.isActive()) {
                value += term.eval();
                if (term.hasRhs()) {
                    value -= term.rhs();
                }
            }
        }
        return value;
    }

    @Override
    public double der(Variable<V> variable) {
        double der = 0;
        for (var term : terms) {
            der += term.der(variable);
        }
        return der;
    }

    @Override
    public boolean hasRhs() {
        return hasRhs;
    }

    @Override
    public double rhs() {
        double rhs = 0;
        for (var term : terms) {
            rhs += term.rhs();
        }
        return rhs;
    }

    @Override
    public double calculateSensi(DenseMatrix x, int column) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(Writer writer, boolean writeInactiveTerms) throws IOException {
        writer.write("sum(");
        List<EquationTerm<V, E>> activeTerms = writeInactiveTerms ? terms : terms.stream().filter(EquationTerm::isActive).collect(Collectors.toList());
        for (Iterator<EquationTerm<V, E>> it = activeTerms.iterator(); it.hasNext(); ) {
            EquationTerm<V, E> term = it.next();
            if (!term.isActive()) {
                writer.write("[ ");
            }
            term.write(writer, writeInactiveTerms);
            if (!term.isActive()) {
                writer.write(" ]");
            }
            if (it.hasNext()) {
                writer.append(" + ");
            }
        }
        writer.write(")");
    }
}
