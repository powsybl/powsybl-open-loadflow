/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfLoad;
import com.powsybl.openloadflow.network.LfLoadModel;

import java.util.Collection;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LoadModelActiveFlowEquationTerm extends AbstractLoadModelEquationTerm {

    public LoadModelActiveFlowEquationTerm(LfBus bus, LfLoadModel loadModel, LfLoad load, VariableSet<AcVariableType> variableSet) {
        super(bus, loadModel, load, variableSet);
    }

    @Override
    protected Collection<LfLoadModel.Term> getTerms() {
        return loadModel.getTermsP();
    }

    @Override
    protected double getTarget() {
        return load.getTargetP();
    }

    @Override
    protected String getName() {
        return "ac_load_p";
    }
}
