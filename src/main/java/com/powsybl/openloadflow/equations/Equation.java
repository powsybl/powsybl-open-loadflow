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

    static class DerIndexCache {

        private final int[] indexes;

        private final int termCount;

        DerIndexCache(int[] indexes, int termCount) {
            this.indexes = indexes;
            this.termCount = termCount;
        }

        int getDerIndex(int varIndex, int termIndex) {
            return indexes[varIndex * termCount + termIndex];
        }

        static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> DerIndexCache build(List<EquationTerm<V, E>> terms, List<Variable<V>> variables) {
            int[] indexes = new int[variables.size() * terms.size()];
            for (int varIndex = 0; varIndex < variables.size(); varIndex++) {
                Variable<V> variable = variables.get(varIndex);
                for (int termIndex = 0; termIndex < terms.size(); termIndex++) {
                    EquationTerm<V, E> term = terms.get(termIndex);
                    indexes[varIndex * terms.size() + termIndex] = term.getDerIndex(variable);
                }
            }
            return new DerIndexCache(indexes, terms.size());
        }
    }

    private final int elementNum;

    private final E type;

    private final EquationSystem<V, E> equationSystem;

    private int column = -1;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private final List<EquationTerm<V, E>> terms = new ArrayList<>();

    private List<Variable<V>> variables;

    private DerIndexCache derIndexCache;

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

    /**
     * Get all variables used by this equation, including non-active ones (so ones only used by deactivated terms).
     */
    public List<Variable<V>> getVariables() {
        if (variables == null) {
            Set<Variable<V>> sortedUniqueVariables = new TreeSet<>();
            for (EquationTerm<V, E> term : terms) {
                sortedUniqueVariables.addAll(term.getVariables());
            }
            variables = new ArrayList<>(sortedUniqueVariables);
        }
        return variables;
    }

    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        if (term.getEquation() != null) {
            throw new PowsyblException("Equation term already added to another equation: "
                    + term.getEquation());
        }
        terms.add(term);
        term.setEquation(this);
        equationSystem.addEquationTerm(term);
        variables = null;
        derIndexCache = null;
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

    private DerIndexCache getDerIndexCache() {
        if (derIndexCache == null) {
            derIndexCache = DerIndexCache.build(terms, getVariables());
        }
        return derIndexCache;
    }

    public double der(Variable<V> variable) {
        int varIndex = getVariables().indexOf(variable);
        if (varIndex == -1) {
            return 0;
        }
        double value = 0;
        for (int termIndex = 0; termIndex < terms.size(); termIndex++) {
            EquationTerm<V, E> term = terms.get(termIndex);
            if (term.isActive()) {
                int derIndex = getDerIndexCache().getDerIndex(varIndex, termIndex);
                value += term.der(derIndex);
            }
        }
        return value;
    }
//
//    public double der(Variable<V> variable) {
//        double value = 0;
//        for (EquationTerm<V, E> term : terms) {
//            if (term.isActive()) {
//                value += term.der(variable);
//            }
//        }
//        return value;
//    }

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
