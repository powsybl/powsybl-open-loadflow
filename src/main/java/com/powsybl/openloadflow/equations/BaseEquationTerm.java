/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.function.DoubleSupplier;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface BaseEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable {

    void setActive(boolean active);

    BaseEquationTerm<V, E> multiply(DoubleSupplier scalarSupplier);

    BaseEquationTerm<V, E> multiply(double scalar);

    BaseEquationTerm<V, E> minus();
}
