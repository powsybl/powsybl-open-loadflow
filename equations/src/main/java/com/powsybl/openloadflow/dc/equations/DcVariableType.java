/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.equations;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public enum DcVariableType implements Quantity {
    BUS_PHI("\u03C6", ElementType.BUS), // bus voltage angle
    BRANCH_ALPHA1("\u03B1", ElementType.BRANCH), // branch phase shift
    DUMMY_P("dummy_p", ElementType.BRANCH); // dummy active power injection (zero impedance branch)

    private final String symbol;

    private final ElementType elementType;

    DcVariableType(String symbol, ElementType elementType) {
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
