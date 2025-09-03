/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Derivative<V extends Enum<V> & Quantity> {

    private final Variable<V> variable;

    private final int localIndex;

    public Derivative(Variable<V> variable, int localIndex) {
        this.variable = Objects.requireNonNull(variable);
        this.localIndex = localIndex;
    }

    public Variable<V> getVariable() {
        return variable;
    }

    public int getLocalIndex() {
        return localIndex;
    }
}
