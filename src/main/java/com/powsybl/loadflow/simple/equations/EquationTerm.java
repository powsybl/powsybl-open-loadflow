/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.powsybl.loadflow.simple.util.Evaluable;

import java.util.List;

/**
 * An equation term, i.e part of the equation sum.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface EquationTerm extends Evaluable {

    /**
     * Get the list of variable this equation term depends on.
     * @return the list of variable this equation term depends on.
     */
    List<Variable> getVariables();

    /**
     * Update equation term using {@code x} variable values.
     * @param x variables values vector
     */
    void update(double[] x);

    /**
     * Evaluate equation term.
     * @return value of the equation term
     */
    double eval();

    /**
     * Get partial derivative.
     *
     * @param variable the variable the partial derivative is with respect to
     * @return value of the partial derivative
     */
    double der(Variable variable);

    /**
     * Check {@link #rhs(Variable)} can return a value different from zero.
     *
     * @return true if {@link #rhs(Variable)} can return a value different from zero, false otherwise
     */
    boolean hasRhs();

    /**
     * Get part of the partial derivative that has to be moved to right hand side.
     * @param variable the variable the partial derivative is with respect to
     * @return value of part of the partial derivative that has to be moved to right hand side
     */
    double rhs(Variable variable);
}
