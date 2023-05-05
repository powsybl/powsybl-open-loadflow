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

    EquationArray<V, E> getEquationArray();

    Set<Variable<V>> getVariables();

    void eval(double[] values, int firstColumn, boolean[] elementActive);
}
