/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfLoadModel;

import java.util.Collection;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LoadModelReactiveFlowEquationTerm extends AbstractLoadModelEquationTerm {

    public LoadModelReactiveFlowEquationTerm(LfBus bus, LfLoadModel loadModel, LfLoad load, VariableSet<AcVariableType> variableSet) {
        super(bus, loadModel, load, variableSet);
    }

    @Override
    public Collection<LfLoadModel.ExpTerm> getExpTerms() {
        return loadModel.getExpTermsQ();
    }

    @Override
    public double getTarget() {
        return load.getTargetQ();
    }

    @Override
    protected String getName() {
        return "ac_load_q";
    }
}
