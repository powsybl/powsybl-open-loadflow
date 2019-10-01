/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.loadflow.simple.network.LfNetwork;
import com.powsybl.loadflow.simple.util.Evaluable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Equation implements Evaluable, Comparable<Equation> {

    /**
     * Bus or any other equipment id.
     */
    private final int num;

    private final EquationType type;

    private final EquationSystem equationSystem;

    private int row = -1;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private final List<EquationTerm> terms = new ArrayList<>();

    Equation(int num, EquationType type, EquationSystem equationSystem) {
        this.num = num;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    public int getNum() {
        return num;
    }

    public EquationType getType() {
        return type;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            equationSystem.notifyListeners(this, active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
    }

    public Equation addTerm(EquationTerm term) {
        Objects.requireNonNull(term);
        terms.add(term);
        return this;
    }

    public List<EquationTerm> getTerms() {
        return terms;
    }

    void initTarget(LfNetwork network, double[] targets) {
        switch (type) {
            case BUS_P:
                targets[row] = network.getBus(num).getTargetP();
                break;

            case BUS_Q:
                targets[row] = network.getBus(num).getTargetQ();
                break;

            case BUS_V:
                targets[row] = network.getBus(num).getTargetV();
                break;

            case BUS_PHI:
                targets[row] = 0;
                break;

            default:
                throw new IllegalStateException("Unknown state variable type "  + type);
        }

        for (EquationTerm term : terms) {
            if (term.hasRhs()) {
                for (Variable variable : term.getVariables()) {
                    targets[row] -= term.rhs(variable);
                }
            }
        }
    }

    public void update(double[] x) {
        for (EquationTerm term : terms) {
            term.update(x);
        }
    }

    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm term : terms) {
            value += term.eval();
            if (term.hasRhs()) {
                for (Variable variable : term.getVariables()) {
                    value -= term.rhs(variable);
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
    public int compareTo(Equation o) {
        if (o == this) {
            return 0;
        }
        int c = num - o.num;
        if (c == 0) {
            c = type.ordinal() - o.type.ordinal();
        }
        return c;
    }

    @Override
    public String toString() {
        return "Equation(num=" + num + ", type=" + type + ", row=" + row + ")";
    }
}
