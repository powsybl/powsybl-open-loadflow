/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se;

/**
 * The estimated state, indexed by bus number within the estimated network.
 *
 * @param converged      whether the iteration met the tolerance before the cap
 * @param iterations     how many iterations were run
 * @param busV           estimated voltage magnitude per bus, per unit
 * @param busAngleRadian estimated voltage angle per bus, radians
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public record WlsResult(boolean converged, int iterations, double[] busV, double[] busAngleRadian) {
}
