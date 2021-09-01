/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum AcVariableType implements VariableType {
    BUS_V("v", ElementType.BUS),
    BUS_PHI("\u03C6", ElementType.BUS),
    BRANCH_ALPHA1("\u03B1" + "1", ElementType.BRANCH),
    BRANCH_RHO1("\u03C1" + "1", ElementType.BRANCH),
    DUMMY_P("dummy_p", ElementType.BRANCH),
    DUMMY_Q("dummy_q", ElementType.BRANCH);

    private final String symbol;

    private final ElementType elementType;

    AcVariableType(String symbol, ElementType elementType) {
        this.symbol = symbol;
        this.elementType = elementType;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public ElementType getElementType() {
        return elementType;
    }
}
