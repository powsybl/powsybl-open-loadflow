/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfBranch;

import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class BranchRho1EquationTerm extends AbstractTargetEquationTerm {

    public BranchRho1EquationTerm(LfBranch branch, VariableSet variableSet) {
        super(SubjectType.BRANCH, Objects.requireNonNull(branch).getNum(), VariableType.BRANCH_RHO1, variableSet);
    }
}
