/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.ElementType;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ToggleEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    private final EquationTerm<V, E> term1;

    private final EquationTerm<V, E> term2;

    public ToggleEquationTerm(boolean active, EquationTerm<V, E> term1, EquationTerm<V, E> term2) {
        super(active);
        this.term1 = term1;
        this.term2 = term2;
    }

    @Override
    public ElementType getElementType() {
        return null;
    }

    @Override
    public int getElementNum() {
        return 0;
    }

    @Override
    public List<Variable<V>> getVariables() {
        return null;
    }

    @Override
    public double eval() {
        return 0;
    }

    @Override
    public double der(Variable<V> variable) {
        return 0;
    }

    @Override
    public void write(Writer writer) throws IOException {

    }
}
