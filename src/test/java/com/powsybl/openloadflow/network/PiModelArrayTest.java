/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.lang3.Range;
import org.mockito.Mockito;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class PiModelArrayTest {

    private PiModelArray piModelArray;

    @BeforeEach
    void setUp() {
        SimplePiModel piModel1 = new SimplePiModel()
                .setR(1)
                .setX(2.4)
                .setR1(1)
                .setA1(0);
        SimplePiModel piModel2 = new SimplePiModel()
                .setR(1.01)
                .setX(2.41)
                .setR1(1.1)
                .setA1(0.1);
        SimplePiModel piModel3 = new SimplePiModel()
                .setR(1.05)
                .setX(2.43)
                .setR1(1.2)
                .setA1(0.2);
        LfNetwork network = Mockito.mock(LfNetwork.class);
        Mockito.when(network.getListeners()).thenReturn(Collections.emptyList());
        LfBranch branch = Mockito.mock(LfBranch.class);
        Mockito.when(branch.getNetwork()).thenReturn(network);
        piModelArray = new PiModelArray(List.of(piModel1, piModel2, piModel3), 1, 2);
        piModelArray.setBranch(branch);
    }

    @Test
    void test() {
        assertEquals(2, piModelArray.getTapPosition());
        assertEquals(Range.between(1, 3), piModelArray.getTapPositionRange());
        var e = assertThrows(IllegalArgumentException.class, () -> piModelArray.setTapPosition(4));
        assertEquals("Tap position 4 out of range [1..3]", e.getMessage());
        piModelArray.setTapPosition(1);
        assertEquals(1, piModelArray.getTapPosition());
    }

    @Test
    void testShiftOneTapPositionToChangeA1() {
        assertTrue(piModelArray.shiftOneTapPositionToChangeA1(Direction.DECREASE));
        assertEquals(1, piModelArray.getTapPosition());
        assertFalse(piModelArray.shiftOneTapPositionToChangeA1(Direction.DECREASE));
        assertEquals(1, piModelArray.getTapPosition());
        assertTrue(piModelArray.shiftOneTapPositionToChangeA1(Direction.INCREASE));
        assertEquals(2, piModelArray.getTapPosition());
        assertTrue(piModelArray.shiftOneTapPositionToChangeA1(Direction.INCREASE));
        assertEquals(3, piModelArray.getTapPosition());
        assertFalse(piModelArray.shiftOneTapPositionToChangeA1(Direction.INCREASE));
        assertEquals(3, piModelArray.getTapPosition());
    }

    @Test
    void testRoundA1ToClosestTap() {
        piModelArray.roundA1ToClosestTap();
        assertEquals(2, piModelArray.getTapPosition());
        piModelArray.setA1(0.04d);
        assertEquals(0.04d, piModelArray.getA1(), 0d);
        piModelArray.roundA1ToClosestTap();
        assertEquals(1, piModelArray.getTapPosition());
        assertEquals(0d, piModelArray.getA1(), 0d);
    }

    @Test
    void testRoundR1ToClosestTap() {
        piModelArray.roundR1ToClosestTap();
        assertEquals(2, piModelArray.getTapPosition());
        assertTrue(Double.isNaN(piModelArray.getContinuousR1()));
        piModelArray.setR1(1.3d);
        assertEquals(1.3d, piModelArray.getR1(), 0d);
        piModelArray.roundR1ToClosestTap();
        assertEquals(3, piModelArray.getTapPosition());
        assertEquals(1.2d, piModelArray.getR1(), 0d);
        assertEquals(1.3d, piModelArray.getContinuousR1(), 0d);
    }

    @Test
    void testUpdateTapPositionToReachNewR1() {
        piModelArray.updateTapPositionToReachNewR1(-0.08d, 1, AllowedDirection.BOTH);
        assertEquals(1, piModelArray.getTapPosition());
    }

    @Test
    void nextTapPositionIndexTest() {
        assertEquals(0, piModelArray.nextTapPositionIndex(1, -0.01, PiModel::getA1));
        assertEquals(2, piModelArray.nextTapPositionIndex(1, 0.01, PiModel::getA1));
        assertEquals(0, piModelArray.nextTapPositionIndex(1, -0.5, PiModel::getA1));
        assertEquals(2, piModelArray.nextTapPositionIndex(1, 0.5, PiModel::getA1));
        assertEquals(-1, piModelArray.nextTapPositionIndex(0, -0.01, PiModel::getA1));
        assertEquals(1, piModelArray.nextTapPositionIndex(0, 0.01, PiModel::getA1));
        assertEquals(1, piModelArray.nextTapPositionIndex(0, 1, PiModel::getA1));
        assertEquals(-1, piModelArray.nextTapPositionIndex(2, 0.01, PiModel::getA1));
        assertEquals(1, piModelArray.nextTapPositionIndex(2, -0.01, PiModel::getA1));
        assertEquals(1, piModelArray.nextTapPositionIndex(2, -1, PiModel::getA1));
    }

    @Test
    void findFirstTapPositionAboveTest() {
        assertEquals(0, piModelArray.findFirstTapPositionAbove(-0.01, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(3);
        assertEquals(1, piModelArray.findFirstTapPositionAbove(-0.01, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(3);
        assertEquals(0, piModelArray.findFirstTapPositionAbove(-0.11, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(3);
        assertEquals(0, piModelArray.findFirstTapPositionAbove(-100, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(1);
        assertEquals(1, piModelArray.findFirstTapPositionAbove(0.01, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(1);
        assertEquals(2, piModelArray.findFirstTapPositionAbove(0.12, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(1);
        assertEquals(2, piModelArray.findFirstTapPositionAbove(5, PiModel::getA1, Range.between(0, 2), Integer.MAX_VALUE));
        piModelArray.setTapPosition(1);
        assertEquals(1, piModelArray.findFirstTapPositionAbove(5, PiModel::getA1, Range.between(0, 1), Integer.MAX_VALUE));
    }
}
