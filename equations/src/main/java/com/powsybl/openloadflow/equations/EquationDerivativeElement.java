/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Objects;

/**
 * @author Florian Dupuy {@literal <florian.dupuy at rte-france.com>}
 */
class EquationDerivativeElement<V extends Enum<V> & Quantity> {
    final int termArrayNum;
    final int termNum;
    final Derivative<V> derivative;

    EquationDerivativeElement(int termArrayNum, int termNum, Derivative<V> derivative) {
        this.termArrayNum = termArrayNum;
        this.termNum = termNum;
        this.derivative = Objects.requireNonNull(derivative);
    }
}
