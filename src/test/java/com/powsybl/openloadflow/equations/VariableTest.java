/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.google.common.testing.EqualsTester;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class VariableTest {

    @Test
    void testEquals() {
        new EqualsTester()
                .addEqualityGroup(new Variable<>(0, AcVariableType.BUS_PHI, 0), new Variable<>(0, AcVariableType.BUS_PHI, 0))
                .addEqualityGroup(new Variable<>(1, AcVariableType.BUS_V, 1), new Variable<>(1, AcVariableType.BUS_V, 1))
                .testEquals();
    }

    @Test
    void testToString() {
        assertEquals("Variable(elementNum=0, type=BUS_PHI, row=-1)", new Variable<>(0, AcVariableType.BUS_PHI, 0).toString());
    }
}
