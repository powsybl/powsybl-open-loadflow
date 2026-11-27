/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;

import java.util.Objects;

/**
 * Forms the gain matrix explicitly and decomposes it by LU, with whichever matrix implementation
 * the scaled Jacobian came in.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class LuGainFactorization implements GainFactorization {

    private final Matrix gain;

    private final LUDecomposition lu;

    public LuGainFactorization(Matrix scaledJacobian) {
        Objects.requireNonNull(scaledJacobian);
        gain = scaledJacobian.times(scaledJacobian.transpose());
        lu = gain.decomposeLU();
    }

    @Override
    public void solve(double[] b) {
        lu.solve(b);
    }

    @Override
    public Matrix getGainMatrix() {
        return gain;
    }

    @Override
    public void close() {
        lu.close();
    }
}
