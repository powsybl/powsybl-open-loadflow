/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VariableEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    private final List<Variable<V>> variables;

    public VariableEquationTerm(Variable<V> variable) {
        this.variables = List.of(Objects.requireNonNull(variable));
    }

    private Variable<V> getVariable() {
        return variables.get(0);
    }

    @Override
    public ElementType getElementType() {
        return getVariable().getType().getElementType();
    }

    @Override
    public int getElementNum() {
        return getVariable().getElementNum();
    }

    @Override
    public List<Variable<V>> getVariables() {
        return variables;
    }

    @Override
    public double eval() {
        return sv.get(getVariable().getRow());
    }

    @Override
    public double der(Variable<V> variable) {
        return 1;
    }

    @Override
    public double calculateSensi(DenseMatrix dx, int column) {
        return dx.get(getVariable().getRow(), column);
    }

    @Override
    public void write(Writer writer) throws IOException {
        getVariable().write(writer);
    }
}
