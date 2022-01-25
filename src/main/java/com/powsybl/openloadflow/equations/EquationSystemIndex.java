/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationSystemIndex<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    NavigableMap<Equation<V, E>, NavigableMap<Variable<V>, List<EquationTerm<V, E>>>> getSortedEquationsToSolve();

    NavigableSet<Variable<V>> getSortedVariablesToFind();

    void addListener(EquationSystemIndexListener listener);
}
