/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
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

    @Test
    void testSide2ImpedanceFollowsSide1ByDefault() {
        SimplePiModel piModel = new SimplePiModel()
                .setR(1)
                .setX(2.4);
        assertFalse(piModel.isNonReciprocal());
        assertEquals(1, piModel.getR21(), 0);
        assertEquals(2.4, piModel.getX21(), 0);
        assertEquals(piModel.getY(), piModel.getY21(), 0);
        assertEquals(piModel.getKsi(), piModel.getKsi21(), 0);

        piModel.setX(4.8);
        assertEquals(4.8, piModel.getX21(), 0);
        assertFalse(piModel.isNonReciprocal());
    }

    @Test
    void testNonReciprocal() {
        SimplePiModel piModel = new SimplePiModel()
                .setR(1)
                .setX(2.4)
                .setR21(-0.6)
                .setX21(0.8);
        assertTrue(piModel.isNonReciprocal());
        assertEquals(1, piModel.getR(), 0);
        assertEquals(2.4, piModel.getX(), 0);
        assertEquals(-0.6, piModel.getR21(), 0);
        assertEquals(0.8, piModel.getX21(), 0);
        assertEquals(1, piModel.getY21(), 10e-16);
        assertEquals(Math.atan2(-0.6, 0.8), piModel.getKsi21(), 10e-16);

        SimplePiModel copy = piModel.copy();
        assertEquals(-0.6, copy.getR21(), 0);
        assertEquals(0.8, copy.getX21(), 0);
    }

    @Test
    void testRescaleNonReciprocalZ() {
        SimplePiModel piModel = new SimplePiModel()
                .setR(1)
                .setX(2.4)
                .setR21(-0.6)
                .setX21(0.8);
        assertTrue(piModel.setMinZ(2, LoadFlowModel.AC));
        assertEquals(1, piModel.getR(), 0); // z12 = 2.6 > 2, kept
        assertEquals(2.4, piModel.getX(), 0);
        assertEquals(-1.2, piModel.getR21(), 10e-16); // z21 = 1 < 2, rescaled at constant angle
        assertEquals(1.6, piModel.getX21(), 10e-16);
        assertFalse(piModel.setMinZ(2, LoadFlowModel.AC));

        assertTrue(piModel.setMinZ(3, LoadFlowModel.DC));
        assertEquals(0, piModel.getR(), 0);
        assertEquals(3, piModel.getX(), 0);
        assertEquals(0, piModel.getR21(), 0);
        assertEquals(3, piModel.getX21(), 0);
    }
}
