/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Toggle equation term.
 * toggleTerm.value = commandTerm.value * term1.value + (1 - commandTerm.value) * term2.value
 * commandTerm.value is expected to be 0 or 1
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ToggleEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    private final EquationTerm<V, E> termA;

    private final EquationTerm<V, E> termB;

    private final VariableEquationTerm<V, E> commandTerm;

    private final Variable<V> commandVar;

    private final List<Variable<V>> variables;

    public ToggleEquationTerm(boolean active, EquationTerm<V, E> termA, EquationTerm<V, E> termB, VariableEquationTerm<V, E> commandTerm) {
        super(active);
        this.termA = Objects.requireNonNull(termA);
        this.termB = Objects.requireNonNull(termB);
        this.commandTerm = Objects.requireNonNull(commandTerm);
        if (termA.getElementType() != termB.getElementType()) {
            throw new IllegalArgumentException("The 2 terms should have same element type");
        }
        if (termA.getElementNum() != termB.getElementNum()) {
            throw new IllegalArgumentException("The 2 terms should have same element number");
        }
        commandVar = commandTerm.getVariable();
        if (termA.getVariables().contains(commandVar) || termB.getVariables().contains(commandVar)) {
            throw new IllegalArgumentException("None of the 2 terms should use command variable");
        }
        Set<Variable<V>> uniqueVariables = new HashSet<>();
        uniqueVariables.addAll(termA.getVariables());
        uniqueVariables.addAll(termB.getVariables());
        uniqueVariables.add(commandVar);
        variables = new ArrayList<>(uniqueVariables);
    }

    @Override
    public ElementType getElementType() {
        return termA.getElementType();
    }

    @Override
    public int getElementNum() {
        return termA.getElementNum();
    }

    @Override
    public List<Variable<V>> getVariables() {
        return variables;
    }

    @Override
    public void setStateVector(StateVector sv) {
        super.setStateVector(sv);
        termA.setStateVector(sv);
        termB.setStateVector(sv);
    }

    @Override
    public double eval() {
        double c = commandTerm.eval();
        return c * termA.eval() + (1 - c) * termB.eval();
    }

    @Override
    public double der(Variable<V> variable) {
        // it has been checked that terms A and B does not depend on var command
        if (variable.equals(commandVar)) {
            return termA.eval() - termB.eval();
        } else {
            double c = commandTerm.eval();
            return c * termA.der(variable) + (1 - c) * termB.der(variable);
        }
    }

    @Override
    public void write(Writer writer) throws IOException {
        writer.write("toggle(");
        termA.write(writer);
        writer.write(", ");
        termB.write(writer);
        writer.write(", ");
        commandTerm.write(writer);
        writer.write(")");
    }
}
