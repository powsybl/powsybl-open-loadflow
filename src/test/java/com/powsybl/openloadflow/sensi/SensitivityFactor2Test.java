/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.contingency.ContingencyContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SensitivityFactor2Test {

    @Test
    void test() {
        SensitivityFactor2 factor = new SensitivityFactor2(SensitivityFunctionType.BRANCH_ACTIVE_POWER, "f1", SensitivityVariableType.INJECTION_ACTIVE_POWER, "v1", false, ContingencyContext.all());
        assertEquals("SensitivityFactor(functionType=BRANCH_ACTIVE_POWER, functionId='f1', variableType=INJECTION_ACTIVE_POWER, variableId='v1', variableSet=false, contingencyContext=ContingencyContext(contingencyId='', contextType=ALL))", factor.toString());
    }
}
