/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.nr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NewtonRaphsonParametersTest {

    @Test
    void test() {
        NewtonRaphsonParameters parameters = new NewtonRaphsonParameters();
        assertEquals(30, parameters.getMaxIteration());
        parameters.setMaxIteration(40);
        assertEquals(40, parameters.getMaxIteration());
        try {
            parameters.setMaxIteration(-3);
            fail();
        } catch (IllegalArgumentException ignored) {
        }
    }
}
