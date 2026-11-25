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
 * An algorithm that minimizes the weighted sum of squared residuals of a {@link WlsProblem}.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public interface WlsSolver {

    String getName();

    /** Iterates from the state the problem holds and leaves the state it ended on there. */
    WlsSolverResult run();

    /**
     * Factorizes the gain matrix of a linearization with the linear algebra this solver iterates
     * on, so the accuracy of an estimate is worked out the same way the estimate was.
     *
     * @param scaledJacobian {@code W^(1/2) H^T} as {@link WlsProblem#scaleJacobian} builds it
     * @return the factorization, to be closed by the caller
     */
    GainFactorization factorizeGain(Matrix scaledJacobian);
}
