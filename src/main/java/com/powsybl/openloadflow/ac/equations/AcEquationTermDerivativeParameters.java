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
public class AcEquationTermDerivativeParameters {

    private final boolean deriveA1;

    private final boolean deriveA2;

    public AcEquationTermDerivativeParameters(boolean deriveA1, boolean deriveA2) {
        this.deriveA1 = deriveA1;
        this.deriveA2 = deriveA2;
    }

    public boolean isDeriveA1() {
        return deriveA1;
    }

    public boolean isDeriveA2() {
        return deriveA2;
    }
}
