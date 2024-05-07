/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface EquationSystemListener<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> {

    void onEquationChange(Equation<V, E> equation, EquationEventType eventType);

    void onEquationTermChange(EquationTerm<V, E> term, EquationTermEventType eventType);
}
