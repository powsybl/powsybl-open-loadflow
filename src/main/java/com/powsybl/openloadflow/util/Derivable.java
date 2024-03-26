/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
public interface Derivable <V extends Enum<V> & Quantity> extends Evaluable {

    double der(Variable<V> variable);

    double calculateSensi(DenseMatrix x, int column);

    boolean isActive();

    double eval(StateVector sv);
}
