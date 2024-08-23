/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class Variable<V extends Enum<V> & Quantity> implements Comparable<Variable<V>> {

    private final int elementNum;

    private final V type;

    private final int num;

    private int row = -1;

    Variable(int elementNum, V type, int num) {
        this.elementNum = elementNum;
        this.type = Objects.requireNonNull(type);
        this.num = num;
    }

    public int getElementNum() {
        return elementNum;
    }

    public V getType() {
        return type;
    }

    public int getNum() {
        return num;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
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
        if (obj instanceof Variable variable) {
            return compareTo(variable) == 0;
        }
        return false;
    }

    @Override
    public int compareTo(Variable<V> o) {
        if (o == this) {
            return 0;
        }
        int c = elementNum - o.elementNum;
        if (c == 0) {
            c = type.ordinal() - o.type.ordinal();
        }
        return c;
    }

    public void write(Writer writer) throws IOException {
        writer.write(type.getSymbol());
        writer.write(Integer.toString(elementNum));
    }

    public <E extends Enum<E> & Quantity> EquationTerm<V, E> createTerm() {
        return new VariableEquationTerm<>(this);
    }

    @Override
    public String toString() {
        return "Variable(elementNum=" + elementNum + ", type=" + type + ", row=" + row + ")";
    }
}
