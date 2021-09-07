/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum DcEquationType implements Quantity {
    BUS_P("p", ElementType.BUS),
    BUS_PHI("\u03C6", ElementType.BUS),
    BRANCH_P("t", ElementType.BRANCH),
    BRANCH_ALPHA1("\u03B1" + "1", ElementType.BRANCH),
    ZERO_PHI("z_\u03C6", ElementType.BRANCH);

    private final String symbol;

    private final ElementType elementType;

    DcEquationType(String symbol, ElementType elementType) {
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
