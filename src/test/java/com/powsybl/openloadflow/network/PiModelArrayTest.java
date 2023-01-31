/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

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

    @Test
    void test() {
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
        PiModelArray piModelArray = new PiModelArray(List.of(piModel1, piModel2, piModel3), 1, 2);
        piModelArray.setBranch(branch);
        assertEquals(2, piModelArray.getTapPosition());
        assertEquals(Range.between(1, 3), piModelArray.getTapPositionRange());
        var e = assertThrows(IllegalArgumentException.class, () -> piModelArray.setTapPosition(4));
        assertEquals("Tap position 4 out of range [1..3]", e.getMessage());
        piModelArray.setTapPosition(1);
        assertEquals(1, piModelArray.getTapPosition());
    }
}
