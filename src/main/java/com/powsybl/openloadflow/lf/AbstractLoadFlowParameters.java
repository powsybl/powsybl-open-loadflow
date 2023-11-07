/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfNetworkParameters;

import java.util.Objects;

/**
 * @author Jean-Luc Bouchot (Artelys) {@literal <jlbouchot at gmail.com>}
 */
public abstract class AbstractLoadFlowParameters {

    protected final LfNetworkParameters networkParameters;

    protected final MatrixFactory matrixFactory;

    protected AbstractLoadFlowParameters(LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }
}
