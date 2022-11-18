/**
 * Copyright (c) 2022, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.network.LfNetworkParameters;

/**
 * @author Jean-Luc Bouchot (Artelys) <jlbouchot at gmail.com>
 */
public abstract class AbstractLoadFlowParameters {

    protected final LfNetworkParameters networkParameters;

    protected final MatrixFactory matrixFactory;

    public AbstractLoadFlowParameters(LfNetworkParameters param, MatrixFactory mtxFactory) {
        this.networkParameters = param;
        this.matrixFactory = mtxFactory;
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }
}
