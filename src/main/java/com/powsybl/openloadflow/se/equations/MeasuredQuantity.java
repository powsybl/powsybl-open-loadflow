/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.equations;

/**
 * The physical quantity a meter reads. Values are per unit.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public enum MeasuredQuantity {
    BUS_V, // bus voltage magnitude, per unit of nominal voltage
    BUS_P, // net active power injected into the network at a bus
    BUS_Q, // net reactive power injected into the network at a bus
    BRANCH_P, // active power flowing from a bus into a branch, at one side
    BRANCH_Q, // reactive power flowing from a bus into a branch, at one side
    BRANCH_I // current magnitude at one branch side
}
