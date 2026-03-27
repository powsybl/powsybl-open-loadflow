/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.equations.fastdecoupled;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.LoadModelReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.LfLoadModel;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public class LoadModelReactiveFlowFastDecoupledEquationTerm implements FastDecoupledEquationTerm {

    private final LoadModelReactiveFlowEquationTerm term;

    public LoadModelReactiveFlowFastDecoupledEquationTerm(LoadModelReactiveFlowEquationTerm loadModelReativeFlowEquationTerm) {
        this.term = loadModelReativeFlowEquationTerm;
    }

    public double derFastDecoupled(Variable<AcVariableType> variable) {
        double value = 0;
        for (LfLoadModel.ExpTerm expTerm : term.getExpTerms()) {
            if (expTerm.n() != 0) {
                value += expTerm.c() * expTerm.n();
            }
        }
        return value * term.getTarget();
    }
}
