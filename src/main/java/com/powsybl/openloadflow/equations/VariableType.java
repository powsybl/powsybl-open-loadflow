/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum VariableType {
    BUS_V("v"),
    BUS_PHI("\u03C6"),
    BRANCH_ALPHA1("\u03B1" + "1"),
    BRANCH_RHO1("\u03C1" + "1"),
    DUMMY_P("dummy_p"),
    DUMMY_Q("dummy_q");

    private final String symbol;

    VariableType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
