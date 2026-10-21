/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractNamedEquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends AbstractEquationTerm<V, E> {

    protected AbstractNamedEquationTerm(boolean active) {
        super(active);
    }

    protected abstract String getName();

    @Override
    public void write(Writer writer) throws IOException {
        writer.write(getName());
        writer.write("(");
        for (Iterator<Variable<V>> it = getVariables().iterator(); it.hasNext();) {
            Variable<V> variable = it.next();
            variable.write(writer);
            if (it.hasNext()) {
                writer.write(", ");
            }
        }
        writer.write(")");
    }
}
