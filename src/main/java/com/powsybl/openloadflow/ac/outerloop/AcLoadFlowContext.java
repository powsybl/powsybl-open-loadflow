/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.lf.AbstractLoadFlowContext;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowContext extends AbstractLoadFlowContext<AcVariableType, AcEquationType, AcLoadFlowParameters> {

    private AcTargetVector targetVector;

    private EquationVector<AcVariableType, AcEquationType> equationVector;

    public AcLoadFlowContext(LfNetwork network, AcLoadFlowParameters parameters) {
        super(network, parameters);
    }

    @Override
    public EquationSystem<AcVariableType, AcEquationType> getEquationSystem() {
        if (equationSystem == null) {
            equationSystem = AcEquationSystem.create(network, parameters.getEquationSystemCreationParameters(),
                    parameters.getNetworkParameters().getLowImpedanceThreshold());
        }
        return equationSystem;
    }

    @Override
    public TargetVector<AcVariableType, AcEquationType> getTargetVector() {
        if (targetVector == null) {
            targetVector = new AcTargetVector(network, getEquationSystem());
        }
        return targetVector;
    }

    public EquationVector<AcVariableType, AcEquationType> getEquationVector() {
        if (equationVector == null) {
            equationVector = new EquationVector<>(getEquationSystem());
        }
        return equationVector;
    }
}
