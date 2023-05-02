/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationTermArray<X extends ElementVector, V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationTermArray<V, E> {

    protected final EquationSystem<V, E> equationSystem;

    protected final X elementVector;

    protected int firstColumn = -1;

    protected boolean[] active;

    protected int length = 0;

    protected AbstractEquationTermArray(EquationSystem<V, E> equationSystem, X elementVector) {
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.elementVector = Objects.requireNonNull(elementVector);
        active = new boolean[elementVector.getSize()];
        for (int elementNum = 0; elementNum < elementVector.getSize(); elementNum++) {
            active[elementNum] = elementVector.isDisabled(elementNum);
            if (active[elementNum]) {
                length++;
            }
        }
    }

    @Override
    public EquationSystem<V, E> getEquationSystem() {
        return equationSystem;
    }

    @Override
    public Set<Variable<V>> getVariables() {
        return Collections.emptySet(); // FIXME
    }

    @Override
    public int getFirstColumn() {
        return firstColumn;
    }

    @Override
    public void setFirstColumn(int firstColumn) {
        this.firstColumn = firstColumn;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public boolean isActive(int elementNum) {
        return active[elementNum];
    }

    @Override
    public void setActive(int elementNum, boolean active) {
        if (active != this.active[elementNum]) {
            this.active[elementNum] = active;
            if (active) {
                length++;
            } else {
                length--;
            }
            // TODO notify equation system listeners
        }
    }

    protected abstract double[] getAttribute();

    @Override
    public void eval(double[] values) {
        int column = firstColumn;
        double[] attribute = getAttribute();
        for (int elementNum = 0; elementNum < elementVector.getSize(); elementNum++) {
            if (!elementVector.isDisabled(elementNum)) {
                values[column++] = attribute[elementNum];
            }
        }
    }

    @Override
    public void der(DerHandler<V> handler) {
        // TODO
    }
}
