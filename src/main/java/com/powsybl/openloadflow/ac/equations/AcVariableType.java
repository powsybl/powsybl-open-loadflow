/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.network.ElementType;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public enum AcVariableType implements Quantity {
    BUS_V("v", ElementType.BUS), // bus voltage magnitude
    BUS_PHI("\u03C6", ElementType.BUS), // bus voltage angle
    BUS_V_ZERO("vh", ElementType.BUS), // bus voltage magnitude for zero sequence
    BUS_PHI_ZERO("\u03C6h", ElementType.BUS), // bus voltage angle for zero sequence
    BUS_V_NEGATIVE("vi", ElementType.BUS), // bus voltage magnitude for negative sequence
    BUS_PHI_NEGATIVE("\u03C6i", ElementType.BUS), // bus voltage angle for negative sequence
    SHUNT_B("b", ElementType.SHUNT_COMPENSATOR), // shunt susceptance
    BRANCH_ALPHA1("\u03B1", ElementType.BRANCH), // branch phase shift
    BRANCH_RHO1("\u03C1", ElementType.BRANCH), // branch voltage ratio
    DUMMY_P("dummy_p", ElementType.BRANCH), // dummy active power injection (zero impedance branch)
    DUMMY_Q("dummy_q", ElementType.BRANCH); // dummy reactive power injection (zero impedance branch)

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
