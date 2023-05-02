/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationTermArray<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    E getType();

    EquationSystem<V, E> getEquationSystem();

    Set<Variable<V>> getVariables();

    int getFirstColumn();

    void setFirstColumn(int firstColumn);

    int getLength();

    boolean isActive(int elementNum);

    void setActive(int elementNum, boolean active);

    void eval(double[] values);

    interface DerHandler<V extends Enum<V> & Quantity> {

        int onDer(int column, Variable<V> variable, double value, int matrixElementIndex);
    }

    void der(DerHandler<V> handler);
}
