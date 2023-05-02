/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractEquationTermArray;
import com.powsybl.openloadflow.equations.EquationSystem;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class BusReactivePowerTargetEquationTermArray extends AbstractEquationTermArray<AcBusVector, AcVariableType, AcEquationType> {

    public BusReactivePowerTargetEquationTermArray(EquationSystem<AcVariableType, AcEquationType> equationSystem, AcBusVector busVector) {
        super(equationSystem, busVector);
    }

    @Override
    public AcEquationType getType() {
        return AcEquationType.BUS_TARGET_Q;
    }

    @Override
    protected double[] getAttribute() {
        return elementVector.q;
    }
}
