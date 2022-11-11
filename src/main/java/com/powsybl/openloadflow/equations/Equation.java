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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private EquationTerm<V, E> rootTerm = new SumEquationTerm<>();

    Equation(int elementNum, E type, EquationSystem<V, E> equationSystem) {
        this.elementNum = elementNum;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        rootTerm.setEquation(this);
        rootTerm.setStateVector(equationSystem.getStateVector());
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

    public EquationTerm<V, E> getRootTerm() {
        return rootTerm;
    }

    public void setRootTerm(EquationTerm<V, E> rootTerm) {
        this.rootTerm = Objects.requireNonNull(rootTerm);
    }

    private SumEquationTerm<V, E> getSum() {
        if (!(rootTerm instanceof SumEquationTerm)) {
            throw new PowsyblException("Root equation term is not a sum anymore");
        }
        return (SumEquationTerm<V, E>) rootTerm;
    }

    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        getSum().addTerm(term);
        return this;
    }

    public Equation<V, E> addTerms(List<EquationTerm<V, E>> terms) {
        getSum().addTerms(terms);
        return this;
    }

    public List<EquationTerm<V, E>> getTerms() {
        return getSum().getChildren();
    }

    @Override
    public double eval() {
        return rootTerm.eval();
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
        rootTerm.write(writer, writeInactiveTerms);
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
