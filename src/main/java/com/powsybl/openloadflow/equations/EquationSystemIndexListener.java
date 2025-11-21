/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface EquationSystemIndexListener<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    enum ChangeType {
        ADDED,
        REMOVED
    }

    /**
     * Called when a new variable has been added or removed to the system.
     */
    void onVariableChange(Variable<V> variable, ChangeType changeType);

    /**
     * Called when a new equation has been added or removed to the system.
     */
    void onEquationChange(AtomicEquation<V, E> equation, ChangeType changeType);

    /**
     * Called when a term is added or removed from an equation.
     */
    void onEquationTermChange(AtomicEquationTerm<V, E> term);

    void onEquationArrayChange(EquationArray<V, E> equationArray, ChangeType changeType);

    void onEquationTermArrayChange(EquationTermArray<V, E> equationTermArray, int termNum, ChangeType changeType);

    /**
     * Called when the order of variables or columns has been changed even without any change in equations (can happen when using Fast Decoupled)
     */
    void onEquationIndexOrderChanged();
}
