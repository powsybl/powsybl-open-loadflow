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
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractNamedEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    protected AbstractNamedEquationTerm(boolean active) {
        super(active);
    }

    protected abstract String getName();

    @Override
    public void write(Writer writer, boolean writeInactiveTerms) throws IOException {
        writer.write(getName());
        writer.write("(");
        List<Variable<V>> sortedVariables = getVariables().stream().sorted().collect(Collectors.toList());
        for (Iterator<Variable<V>> it = sortedVariables.iterator(); it.hasNext();) {
            Variable<V> variable = it.next();
            variable.write(writer);
            if (it.hasNext()) {
                writer.write(", ");
            }
        }
        writer.write(")");
    }
}
