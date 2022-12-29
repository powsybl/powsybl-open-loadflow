/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.lf.AbstractLoadFlowContext;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreator;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
 */
public class DcLoadFlowContext extends AbstractLoadFlowContext<DcVariableType, DcEquationType, DcLoadFlowParameters> {

    private DcTargetVector targetVector;

    public DcLoadFlowContext(LfNetwork network, DcLoadFlowParameters parameters) {
        super(network, parameters);
    }

    @Override
    public EquationSystem<DcVariableType, DcEquationType> getEquationSystem() {
        if (equationSystem == null) {
            equationSystem = new DcEquationSystemCreator(network, parameters.getEquationSystemCreationParameters()).create(true);
        }
        return equationSystem;
    }

    @Override
    public TargetVector<DcVariableType, DcEquationType> getTargetVector() {
        if (targetVector == null) {
            targetVector = new DcTargetVector(network, getEquationSystem());
        }
        return targetVector;
    }

    @Override
    public void close() {
        super.close();
        if (targetVector != null) {
            targetVector.close();
        }
    }
}
