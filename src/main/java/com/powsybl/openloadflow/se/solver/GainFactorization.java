/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import com.powsybl.math.matrix.Matrix;

/**
 * A factorization of the gain matrix {@code G = H^T W H} of one linearization, which is what both
 * the iteration and the accuracy of its result solve against.
 *
 * <p>A singular gain matrix is refused with a {@code MatrixException}, either when factorizing or
 * on the first solve.</p>
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public interface GainFactorization extends AutoCloseable {

    @FunctionalInterface
    interface Factory {

        /**
         * @param scaledJacobian {@code W^(1/2) H^T} as {@link WlsProblem#scaleJacobian} builds it,
         *                       so that the gain matrix is this times its own transpose
         * @return the factorization, to be closed by the caller
         */
        GainFactorization factorize(Matrix scaledJacobian);
    }

    /** Solves {@code G x = b} in place. */
    void solve(double[] b);

    Matrix getGainMatrix();

    @Override
    void close();
}
