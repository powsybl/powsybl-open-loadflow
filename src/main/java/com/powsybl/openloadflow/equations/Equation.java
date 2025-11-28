/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface Equation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    E getType();

    int getElementNum();

    void setActive(boolean active);

    Equation<V, E> addTerm(EquationTerm<V, E> term);

    <T extends EquationTerm<V, E>> Equation<V, E> addTerms(List<T> terms);

    boolean isActive();

    int getColumn();

    <T extends EquationTerm<V, E>> List<T> getTerms();

    <T extends EquationTerm<V, E>> Map<Variable<V>, List<T>> getTermsByVariable();

    EquationSystem<V, E> getEquationSystem();

    interface DerHandler<V extends Enum<V> & Quantity> {
        int onDer(Variable<V> variable, double value, int matrixElementIndex);
    }
}
