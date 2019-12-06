/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Iterator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationTerm implements EquationTerm {

    protected abstract String getName();

    @Override
    public void print(StringBuilder builder) {
        builder.append(getName()).append("(");
        for (Iterator<Variable> it = getVariables().iterator(); it.hasNext();) {
            Variable variable = it.next();
            variable.print(builder);
            if (it.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(")");
    }
}
