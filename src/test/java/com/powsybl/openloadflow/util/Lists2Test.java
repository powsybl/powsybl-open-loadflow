/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class Lists2Test {

    private static List<String> createList(int size) {
        List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add("s" + i);
        }
        return list;
    }

    private static List<Integer> toSizes(List<List<String>> partitions) {
        return partitions.stream().map(List::size).toList();
    }

    @Test
    void test() {
        assertEquals(List.of(15, 15, 15, 14, 14), toSizes(Lists2.partition(createList(73), 5)));
        assertEquals(List.of(15, 15, 15, 15, 15), toSizes(Lists2.partition(createList(75), 5)));
        assertEquals(List.of(16, 15, 15, 15, 15), toSizes(Lists2.partition(createList(76), 5)));
        assertEquals(List.of(1, 1, 1, 0, 0), toSizes(Lists2.partition(createList(3), 5)));
        assertEquals(List.of(0, 0, 0, 0, 0), toSizes(Lists2.partition(createList(0), 5)));
        assertEquals(List.of(2), toSizes(Lists2.partition(createList(2), 1)));
        var ls2 = createList(2);
        var e = assertThrows(IllegalArgumentException.class, () -> Lists2.partition(ls2, 0));
        assertEquals("Partition count should be > 0", e.getMessage());
    }
}
