/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class BranchStateTest {

    @Test
    void test() {
        var piModel1 = new SimplePiModel()
                .setR(1)
                .setX(2.4)
                .setR1(1)
                .setA1(0);
        var piModel2 = new SimplePiModel()
                .setR(1.01)
                .setX(2.41)
                .setR1(1.1)
                .setA1(0.1);
        var branch = Mockito.mock(LfBranch.class);
        var piModelArray = new PiModelArray(List.of(piModel1, piModel2), 0, 0);
        piModelArray.setBranch(branch);
        Mockito.when(branch.getPiModel()).thenReturn(piModelArray);
        assertEquals(0, piModelArray.getA1());
        BranchState branchState = BranchState.save(branch);
        piModelArray.setA1(0.2);
        assertEquals(0.2, piModelArray.getA1());
        branchState.restore();
        assertEquals(0, piModelArray.getA1());

        piModelArray.setA1(-0.05);
        branchState = BranchState.save(branch);
        branchState.restore();
        assertEquals(-0.05, piModelArray.getA1());
    }
}
