/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.CuDssMatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class MatrixFactoryUtilTest {

    @Test
    void getMatrixFactoryTest() {
        // KLU when not requested
        assertInstanceOf(SparseMatrixFactory.class, MatrixFactoryUtil.getMatrixFactory(false));

        // when requested: cuDSS if the native library is available, otherwise fall back to KLU
        if (MatrixFactoryUtil.isCuDssAvailable()) {
            assertInstanceOf(CuDssMatrixFactory.class, MatrixFactoryUtil.getMatrixFactory(true));
        } else {
            assertInstanceOf(SparseMatrixFactory.class, MatrixFactoryUtil.getMatrixFactory(true));
        }
    }
}
