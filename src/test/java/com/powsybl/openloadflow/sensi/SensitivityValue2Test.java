/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SensitivityValue2Test {

    @Test
    void test() {
        SensitivityValue2 value = new SensitivityValue2("", "c1", 0.34, 35);
        assertEquals("SensitivityValue(factorContext=, contingencyId='c1', value=0.34, functionReference=35.0)", value.toString());
    }
}