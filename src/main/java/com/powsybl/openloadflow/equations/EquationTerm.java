/**
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.util.Derivable;
import com.powsybl.openloadflow.util.Evaluable;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationTerm<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> extends Evaluable, Derivable<V> {

    static void setActive(Evaluable evaluable, boolean active) {
        if (evaluable instanceof EquationTerm<?, ?> term) {
            term.setActive(active);
        }
    }

    void setActive(boolean active);

    EquationTerm<V, E> multiply(DoubleSupplier scalarSupplier);

    EquationTerm<V, E> multiply(double scalar);

    EquationTerm<V, E> minus();

    ElementType getElementType();

    int getElementNum();

    double calculateSensi(DenseMatrix x, int column);

    Equation<V, E> getEquation();

    void setEquation(Equation<V, E> equation);

    /**
     * Get the list of variable this equation term depends on.
     * @return the list of variable this equation term depends on.
     */
    List<Variable<V>> getVariables();
}
