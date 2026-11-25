/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

/**
 * The structure the AC Jacobian matrix is solved on.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public enum AcJacobianStructure {
    /**
     * A matrix whose matrix structure holds every position the controls can use, analysed once and filled in place
     * ({@link AcFixedJacobianMatrix}).
     */
    FIXED,

    /**
     * The matrix of the equation system's index, built again when the equations to solve change.
     */
    INDEXED
}
