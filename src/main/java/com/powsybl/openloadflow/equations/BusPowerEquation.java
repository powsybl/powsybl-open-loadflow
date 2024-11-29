/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public class BusPowerEquation<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Equation<V, E> {

    BusPowerEquation(int elementNum, E type, EquationSystem<V, E> equationSystem) {
        super(elementNum, type, equationSystem);
    }

    /**
     * Sum the terms of the equation with the rhs. Represents the power balance equation of a bus.
     */
    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm<V, E> term : getTerms()) {
            if (term.isActive()) {
                value += term.eval();
            }
        }
        return value;
    }
}
