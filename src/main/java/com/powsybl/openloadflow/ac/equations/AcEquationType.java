/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum AcEquationType implements Quantity {
    BUS_P("bus_p", ElementType.BUS),
    BUS_Q("bus_q", ElementType.BUS),
    BUS_V("v", ElementType.BUS),
    BUS_V_SLOPE("v_slope", ElementType.BUS),
    BUS_PHI("\u03C6", ElementType.BUS),
    BRANCH_P("branch_p", ElementType.BRANCH),
    BRANCH_I1("branch_i1", ElementType.BRANCH),
    BRANCH_I2("branch_i2", ElementType.BRANCH),
    BRANCH_Q("branch_q", ElementType.BRANCH),
    BRANCH_ALPHA1("\u03B1" + "1", ElementType.BRANCH),
    BRANCH_RHO1("\u03C1" + "1", ElementType.BRANCH),
    ZERO_Q("zero_q", ElementType.BUS),
    ZERO_V("zero_v", ElementType.BRANCH),
    ZERO_PHI("zero_\u03C6", ElementType.BRANCH),
    ZERO_RHO1("zero_\u03C1", ElementType.BRANCH);

    private final String symbol;

    private final ElementType elementType;

    AcEquationType(String symbol, ElementType elementType) {
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
