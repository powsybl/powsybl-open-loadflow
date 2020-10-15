/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.equations;

import com.powsybl.openloadflow.equations.AbstractTargetEquationTerm;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.LfBranch;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DummyReactivePowerEquationTerm extends AbstractTargetEquationTerm {

    public DummyReactivePowerEquationTerm(LfBranch branch, VariableSet variableSet) {
        super(SubjectType.BRANCH, Objects.requireNonNull(branch).getNum(), VariableType.DUMMY_Q, variableSet);
    }
}
