/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.util;

import com.powsybl.math.matrix.CuDssMatrixFactory;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selection of the {@link MatrixFactory} used by the load flow.
 *
 * <p>The default is the CPU {@link SparseMatrixFactory} (KLU). The optional cuDSS
 * (GPU) factory is used only when explicitly requested AND available — the cuDSS
 * native library is built and a GPU is present — otherwise it falls back to KLU,
 * so enabling it can never break a run on a machine without cuDSS.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public final class MatrixFactoryUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatrixFactoryUtil.class);

    private MatrixFactoryUtil() {
    }

    /**
     * @return true if the cuDSS (GPU) backend is available (native library loaded).
     */
    public static boolean isCuDssAvailable() {
        return CuDssMatrixFactory.isAvailable();
    }

    /**
     * Return the cuDSS matrix factory when {@code useCuDss} is set and cuDSS is
     * available, otherwise the KLU {@link SparseMatrixFactory}.
     *
     * @param useCuDss whether the cuDSS (GPU) backend is requested
     * @return the selected matrix factory (never null)
     */
    public static MatrixFactory getMatrixFactory(boolean useCuDss) {
        if (useCuDss) {
            if (CuDssMatrixFactory.isAvailable()) {
                LOGGER.info("Using cuDSS (GPU) matrix factory");
                return new CuDssMatrixFactory();
            }
            LOGGER.warn("cuDSS matrix factory requested but not available; falling back to KLU (SparseMatrixFactory)");
        }
        return new SparseMatrixFactory();
    }
}
