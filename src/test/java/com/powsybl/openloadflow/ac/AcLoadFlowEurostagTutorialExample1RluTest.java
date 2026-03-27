/*
 * Copyright (c) 2019-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.loadflow.LoadFlow;
import com.powsybl.math.matrix.RLUSparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;

/**
 * Runs all {@link AcLoadFlowEurostagTutorialExample1Test} scenarios with the RTE RLU
 * sparse factorization ({@link RLUSparseMatrixFactory}) instead of KLU, to validate
 * that the new native implementation produces identical results.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcLoadFlowEurostagTutorialExample1RluTest extends AcLoadFlowEurostagTutorialExample1Test {

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        // Replace the runner: same network/parameters, but use RLU sparse factorization
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new RLUSparseMatrixFactory()));
    }
}
