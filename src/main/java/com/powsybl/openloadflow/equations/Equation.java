/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Equation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> implements Evaluable, Comparable<Equation<V, E>> {

    /**
     * Bus or any other equipment id.
     */
    private final int num;

    private final E type;

    private final EquationSystem<V, E> equationSystem;

    private int column = -1;

    private Object data;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private EquationSystem.EquationUpdateType updateType;

    private final List<EquationTerm<V, E>> terms = new ArrayList<>();

    Equation(int num, E type, EquationSystem<V, E> equationSystem) {
        this(num, type, equationSystem, EquationSystem.EquationUpdateType.DEFAULT);
    }

    Equation(int num, E type, EquationSystem<V, E> equationSystem, EquationSystem.EquationUpdateType updateType) {
        this.num = num;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.updateType = updateType;
    }

    public int getNum() {
        return num;
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

    public void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            equationSystem.notifyEquationChange(this, active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
    }

    public EquationSystem.EquationUpdateType getUpdateType() {
        return updateType;
    }

    public void setUpdateType(EquationSystem.EquationUpdateType updateType) {
        this.updateType = updateType;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public <T> T getData() {
        return (T) data;
    }

    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        terms.add(term);
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

    public void update(double[] x) {
        for (EquationTerm<V, E> term : terms) {
            if (term.isActive()) {
                term.update(x);
            }
        }
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
    public int hashCode() {
        return num + type.hashCode();
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
        int c = num - o.num;
        if (c == 0) {
            c = type.ordinal() - o.type.ordinal();
        }
        return c;
    }

    public void write(Writer writer) throws IOException {
        writer.write(type.getSymbol());
        writer.append(Integer.toString(num));
        writer.append(" = ");
        List<EquationTerm<V, E>> activeTerms = terms.stream().filter(EquationTerm::isActive).collect(Collectors.toList());
        for (Iterator<EquationTerm<V, E>> it = activeTerms.iterator(); it.hasNext();) {
            EquationTerm<V, E> term = it.next();
            term.write(writer);
            if (it.hasNext()) {
                writer.write(" + ");
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Equation(num=").append(num)
                .append(", type=").append(type)
                .append(", column=").append(column).append(")");
        return builder.toString();
    }
}
