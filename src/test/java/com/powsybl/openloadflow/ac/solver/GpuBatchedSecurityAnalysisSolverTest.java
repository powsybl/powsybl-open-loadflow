/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcData;
import com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcNewtonSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * CPU-only checks of the GPU batch chunk-size resolution ({@code olf.gpu.sa.maxBatch}),
 * which bounds the per-chunk device-memory footprint. An explicit positive property is a
 * manual cap; otherwise the chunk is auto-sized from free device memory by a native query,
 * falling back to the default when no GPU is present. The auto path (and the native nnz /
 * cudaMemGetInfo estimate) is exercised by the device tests.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
class GpuBatchedSecurityAnalysisSolverTest {

    private static final GpuAcData EMPTY = new GpuAcData(new double[0], new int[0], new double[0], new int[0],
            new double[0], new int[0], new double[0], new int[0],
            new int[0], new int[0], new double[0], new int[0], new int[0],
            new double[0], new int[0],
            new int[0], new int[0], new double[0],
            new int[0], new double[0], new double[0], new double[0], new double[0],
            new int[0], new double[0], -1, 0.0,
            new double[0], new double[0], new double[0]);

    @Test
    void explicitCapIsHonouredAndClampedToCount() {
        System.setProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY, "64");
        try {
            assertEquals(64, GpuBatchedSecurityAnalysisSolver.resolveChunkSize(1000, 10, EMPTY));
            assertEquals(10, GpuBatchedSecurityAnalysisSolver.resolveChunkSize(10, 10, EMPTY),
                    "chunk is clamped to the scenario count");
        } finally {
            System.clearProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY);
        }
    }

    @Test
    void autoSizeFallsBackToDefaultWhenNoGpu() {
        assumeFalse(GpuAcNewtonSolver.isAvailable(), "auto-size queries the device when a GPU is present");
        // No GPU: recommendBatchSize is unlinked, so resolveChunkSize falls back to the default.
        System.clearProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY);
        assertEquals(GpuBatchedSecurityAnalysisSolver.DEFAULT_MAX_BATCH,
                GpuBatchedSecurityAnalysisSolver.resolveChunkSize(1000, 10, EMPTY));
    }

    @Test
    void chunkSizeAlwaysClampedToScenarioCount() {
        // Holds for every path (explicit cap, auto-size, or fallback): 1 <= chunk <= nb.
        System.clearProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY);
        int chunk = GpuBatchedSecurityAnalysisSolver.resolveChunkSize(5, 10, EMPTY);
        assertTrue(chunk >= 1 && chunk <= 5, "chunk must be in [1, nb], was " + chunk);

        System.setProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY, "0");   // invalid -> auto/fallback
        try {
            int c2 = GpuBatchedSecurityAnalysisSolver.resolveChunkSize(5, 10, EMPTY);
            assertTrue(c2 >= 1 && c2 <= 5, "chunk must be in [1, nb], was " + c2);
        } finally {
            System.clearProperty(GpuBatchedSecurityAnalysisSolver.MAX_BATCH_PROPERTY);
        }
    }
}
