/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

/**
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public enum WlsSolverStatus {
    /** The iteration met the tolerance. */
    CONVERGED,
    /** The iteration reached its cap with the fit still improving. */
    MAX_ITERATION_REACHED,
    /** The gain matrix could not be factorized: the readings leave the state undetermined. */
    SINGULAR_GAIN_MATRIX,
    /** The iteration diverged or left the finite numbers. */
    SOLVER_FAILED
}
