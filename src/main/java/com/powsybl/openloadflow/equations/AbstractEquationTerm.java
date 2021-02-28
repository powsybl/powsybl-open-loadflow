/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractEquationTerm implements EquationTerm {

    private Equation equation;

    private boolean active = true;

    @Override
    public Equation getEquation() {
        return equation;
    }

    @Override
    public void setEquation(Equation equation) {
        this.equation = Objects.requireNonNull(equation);
    }

    @Override
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            equation.getEquationSystem().notifyEquationTermChange(this, active ? EquationTermEventType.EQUATION_TERM_ACTIVATED
                                                                               : EquationTermEventType.EQUATION_TERM_DEACTIVATED);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }
}
