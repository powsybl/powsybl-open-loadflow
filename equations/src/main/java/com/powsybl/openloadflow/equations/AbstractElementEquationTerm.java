/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfElement;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractElementEquationTerm<T extends LfElement, V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractNamedEquationTerm<V, E> {

    protected final T element;

    protected AbstractElementEquationTerm(T element) {
        super(!Objects.requireNonNull(element).isDisabled());
        this.element = element;
    }

    @Override
    public ElementType getElementType() {
        return element.getType();
    }

    @Override
    public int getElementNum() {
        return element.getNum();
    }
}
