/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Lists2 {

    private Lists2() {
    }

    /**
     * A better partitioner than the guava one which only to give partition count instead of
     * partition size so that we can have fixed number of partition and adjusted unbalanced
     * partition sizes.
     */
    public static <E> List<List<E>> partition(List<E> list, int partitionCount) {
        if (partitionCount == 0) {
            throw new IllegalArgumentException("Partition count should be > 0");
        }
        List<List<E>> partitions = new ArrayList<>();
        int partitionSize = list.size() / partitionCount;
        int remainder = list.size() % partitionCount;
        int partitionStart = 0;
        for (int partition = 0; partition < partitionCount; partition++) {
            int adjustedPartitionSize = partitionSize;
            if (partition < remainder) {
                adjustedPartitionSize++;
            }
            partitions.add(list.subList(partitionStart, partitionStart + adjustedPartitionSize));
            partitionStart += adjustedPartitionSize;
        }
        return partitions;
    }
}
