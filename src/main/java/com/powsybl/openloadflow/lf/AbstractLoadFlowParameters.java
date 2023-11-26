/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.network.LfNetworkParameters;

import java.util.Objects;

/**
 * @author Jean-Luc Bouchot (Artelys) {@literal <jlbouchot at gmail.com>}
 */
public abstract class AbstractLoadFlowParameters<P extends AbstractLoadFlowParameters<P>> {

    public static final int DEFAULT_MAX_OUTER_LOOP_ITERATIONS = 20;

    protected LfNetworkParameters networkParameters;

    protected MatrixFactory matrixFactory;

    protected AbstractLoadFlowParameters() {
        this(new LfNetworkParameters(), new SparseMatrixFactory());
    }

    protected AbstractLoadFlowParameters(LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public P setNetworkParameters(LfNetworkParameters networkParameters) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        return (P) this;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public P setMatrixFactory(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        return (P) this;
    }
}
