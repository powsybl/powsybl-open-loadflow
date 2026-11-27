/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.solver;

import java.util.Objects;

/**
 * @param status     how the iteration ended
 * @param statusText why it ended that way, empty when there is nothing to add
 * @param iterations the state updates applied
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public record WlsSolverResult(WlsSolverStatus status, String statusText, int iterations) {

    public WlsSolverResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(statusText);
        if (iterations < 0) {
            throw new IllegalArgumentException("Invalid iteration value: " + iterations);
        }
    }
}
