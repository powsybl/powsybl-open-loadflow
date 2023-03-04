/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Equation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements Evaluable, Comparable<Equation<V, E>> {

    private final int elementNum;

    private final E type;

    private final EquationSystem<V, E> equationSystem;

    private int column = -1;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private final List<EquationTerm<V, E>> terms = new ArrayList<>();

    private final Map<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = new TreeMap<>();

    Equation(int elementNum, E type, EquationSystem<V, E> equationSystem) {
        this.elementNum = elementNum;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    public int getElementNum() {
        return elementNum;
    }

    public E getType() {
        return type;
    }

    public EquationSystem<V, E> getEquationSystem() {
        return equationSystem;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public boolean isActive() {
        return active;
    }

    public Equation<V, E> setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            equationSystem.notifyEquationChange(this, active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
        return this;
    }

    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        if (term.getEquation() != null) {
            throw new PowsyblException("Equation term already added to another equation: "
                    + term.getEquation());
        }
        terms.add(term);
        for (Variable<V> v : term.getVariables()) {
            termsByVariable.computeIfAbsent(v, k -> new ArrayList<>())
                    .add(term);
        }
        term.setEquation(this);
        equationSystem.addEquationTerm(term);
        equationSystem.notifyEquationTermChange(term, EquationTermEventType.EQUATION_TERM_ADDED);
        return this;
    }

    public Equation<V, E> addTerms(List<EquationTerm<V, E>> terms) {
        Objects.requireNonNull(terms);
        for (EquationTerm<V, E> term : terms) {
            addTerm(term);
        }
        return this;
    }

    public List<EquationTerm<V, E>> getTerms() {
        return terms;
    }

    public Map<Variable<V>, List<EquationTerm<V, E>>> getTermsByVariable() {
        return termsByVariable;
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

    public double rhs() {
        double rhs = 0;
        for (var term : terms) {
            if (term.isActive() && term.hasRhs()) {
                rhs += term.rhs();
            }
        }
        return rhs;
    }

    @Override
    public int hashCode() {
        return elementNum + type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Equation) {
            return compareTo((Equation) obj) == 0;
        }
        return false;
    }

    @Override
    public int compareTo(Equation<V, E> o) {
        if (o == this) {
            return 0;
        }
        int c = elementNum - o.elementNum;
        if (c == 0) {
            c = type.ordinal() - o.type.ordinal();
        }
        return c;
    }

    public void write(Writer writer, boolean writeInactiveTerms) throws IOException {
        writer.append(type.getSymbol())
                .append(Integer.toString(elementNum))
                .append(" = ");
        List<EquationTerm<V, E>> activeTerms = writeInactiveTerms ? terms : terms.stream().filter(EquationTerm::isActive).collect(Collectors.toList());
        for (Iterator<EquationTerm<V, E>> it = activeTerms.iterator(); it.hasNext();) {
            EquationTerm<V, E> term = it.next();
            if (!term.isActive()) {
                writer.write("[ ");
            }
            term.write(writer);
            if (!term.isActive()) {
                writer.write(" ]");
            }
            if (it.hasNext()) {
                writer.append(" + ");
            }
        }
    }

    public Optional<LfElement> getElement(LfNetwork network) {
        Objects.requireNonNull(network);
        LfElement element = null;
        switch (type.getElementType()) {
            case BUS:
                element = network.getBus(elementNum);
                break;
            case BRANCH:
                element = network.getBranch(elementNum);
                break;
            case SHUNT_COMPENSATOR:
                element = network.getShunt(elementNum);
                break;
        }
        return Optional.ofNullable(element);
    }

    @Override
    public String toString() {
        return "Equation(elementNum=" + elementNum +
                ", type=" + type +
                ", column=" + column + ")";
    }
}
