/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.equations;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.Variable;

import java.util.Collection;

/**
 * One row of a measurement model: the function h_i(x) belonging to a single meter,
 * and with it one row of the measurement Jacobian H.
 *
 * <p>Deliberately not an {@code Equation}: an equation system keys equations by
 * (element, type) and yields one row per pair, whereas a measurement model must admit
 * several redundant readings of the very same quantity, each contributing its own row.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public interface MeasurementEquation {

    /** Predicted meter reading at the current state, per unit. */
    double eval();

    /** State variables this prediction depends on. */
    Collection<Variable<AcVariableType>> variables();

    /** Partial derivative of the prediction with respect to one state variable. */
    double der(Variable<AcVariableType> variable);
}
