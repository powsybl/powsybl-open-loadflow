/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.loadflow.simple.network.LfBus;
import com.powsybl.loadflow.simple.network.LfNetwork;

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

    void initState(VoltageInitializer initializer, LfNetwork network, double[] x) {
        Objects.requireNonNull(initializer);
        Objects.requireNonNull(network);
        Objects.requireNonNull(x);
        LfBus bus = network.getBus(num);
        switch (type) {
            case BUS_V:
                x[column] = initializer.getMagnitude(bus);
                break;

            case BUS_PHI:
                x[column] = Math.toRadians(initializer.getAngle(bus));
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

    @Override
    public String toString() {
        return "Variable(num=" + num + ", type=" + type + ", column=" + column + ")";
    }
}
