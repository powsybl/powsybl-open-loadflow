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
public enum DcEquationType implements Quantity {
    BUS_TARGET_P("bus_target_p", ElementType.BUS), // bus active power target
    BUS_TARGET_PHI("bus_target_\u03C6", ElementType.BUS), // slack bus voltage angle target
    BRANCH_TARGET_ALPHA1("branch_target_\u03B1", ElementType.BRANCH), // phase shifter constant shift
    ZERO_PHI("zero_\u03C6", ElementType.BRANCH), // zero impedance branch, voltage angle equality
    DUMMY_TARGET_P("dummy_target_p", ElementType.BRANCH);

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
