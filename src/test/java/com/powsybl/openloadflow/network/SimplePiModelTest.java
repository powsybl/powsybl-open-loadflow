/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class SimplePiModelTest {

    @Test
    void testRescaleZ() {
        SimplePiModel piModel = new SimplePiModel()
                .setR(1)
                .setX(2.4);
        assertEquals(1, piModel.getR(), 0);
        assertEquals(2.4, piModel.getX(), 0);
        assertEquals(2.6, piModel.getZ(), 0);
        piModel.setMinZ(5.2, LoadFlowModel.AC);
        assertEquals(2, piModel.getR(), 10e-16);
        assertEquals(4.8, piModel.getX(), 10e-16);
        assertEquals(5.2, piModel.getZ(), 10e-16);
    }
}
