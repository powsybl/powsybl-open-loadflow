/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationTerm implements EquationTerm {

    private Equation equation;

    @Override
    public Equation getEquation() {
        return equation;
    }

    @Override
    public void setEquation(Equation equation) {
        this.equation = Objects.requireNonNull(equation);
    }

    protected abstract String getName();

    @Override
    public void write(Writer writer) throws IOException {
        writer.write(getName());
        writer.write("(");
        for (Iterator<Variable> it = getVariables().iterator(); it.hasNext();) {
            Variable variable = it.next();
            variable.write(writer);
            if (it.hasNext()) {
                writer.write(", ");
            }
        }
        writer.write(")");
    }
}
