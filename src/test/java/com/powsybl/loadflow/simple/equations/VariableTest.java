/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.equations;

import com.google.common.testing.EqualsTester;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class VariableTest {

    @Test
    public void testEquals() {
        new EqualsTester()
                .addEqualityGroup(new Variable(0, VariableType.BUS_PHI), new Variable(0, VariableType.BUS_PHI))
                .addEqualityGroup(new Variable(1, VariableType.BUS_V), new Variable(1, VariableType.BUS_V))
                .testEquals();
    }

    @Test
    public void testToString() {
        assertEquals("Variable(num=0, type=BUS_PHI, column=-1)", new Variable(0, VariableType.BUS_PHI).toString());
    }
}
