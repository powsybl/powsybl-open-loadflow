/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DisymAcEquationSystemCreationParameters {

    private final boolean forceA1Var;

    public DisymAcEquationSystemCreationParameters() {
        this(false);
    }

    public DisymAcEquationSystemCreationParameters(boolean forceA1Var) {
        this.forceA1Var = forceA1Var;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    @Override
    public String toString() {
        return "DisymAcEquationSystemCreationParameters(" +
                "forceA1Var=" + forceA1Var +
                ')';
    }
}
