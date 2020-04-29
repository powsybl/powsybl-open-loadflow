/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfNetwork;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Variable implements Comparable<Variable> {

    /**
     * Bus or any other equipment num.
     */
    private final int num;

    private final VariableType type;

    private int column = -1;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    Variable(int num, VariableType type) {
        this.num = num;
        this.type = Objects.requireNonNull(type);
    }

    public int getNum() {
        return num;
    }

    public VariableType getType() {
        return type;
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
        // FIXME invalidate equation system cache
        this.active = active;
    }

    void initState(VoltageInitializer initializer, LfNetwork network, double[] x) {
        Objects.requireNonNull(initializer);
        Objects.requireNonNull(network);
        Objects.requireNonNull(x);
        switch (type) {
            case BUS_V:
                x[column] = initializer.getMagnitude(network.getBus(num));
                break;

            case BUS_PHI:
                x[column] = Math.toRadians(initializer.getAngle(network.getBus(num)));
                break;

            case BRANCH_ALPHA1:
                x[column] = network.getBranch(num).getPiModel().getA1();
                break;

            case BRANCH_ALPHA2:
                x[column] = network.getBranch(num).getPiModel().getA2();
                break;

            case DUMMY_P:
            case DUMMY_Q:
                x[column] = 0;
                break;

            default:
                throw new IllegalStateException("Unknown variable type "  + type);
        }
    }

    void updateState(LfNetwork network, double[] x) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(x);
        switch (type) {
            case BUS_V:
                network.getBus(num).setV(x[column]);
                break;

            case BUS_PHI:
                network.getBus(num).setAngle(Math.toDegrees(x[column]));
                break;

            case BRANCH_ALPHA1:
                network.getBranch(num).getPiModel().setA1(x[column]);
                break;

            case BRANCH_ALPHA2:
                network.getBranch(num).getPiModel().setA2(x[column]);
                break;

            case DUMMY_P:
            case DUMMY_Q:
                // nothing to do
                break;

            default:
                throw new IllegalStateException("Unknown variable type "  + type);
        }
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
        if (obj instanceof Variable) {
            return compareTo((Variable) obj) == 0;
        }
        return false;
    }

    @Override
    public int compareTo(Variable o) {
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
        writer.write(Integer.toString(num));
    }

    @Override
    public String toString() {
        return "Variable(num=" + num + ", type=" + type + ", column=" + column + ")";
    }
}
