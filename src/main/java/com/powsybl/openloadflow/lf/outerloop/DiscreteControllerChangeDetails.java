/**
 * Copyright (c) 2026, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.lf.outerloop;

/**
 * Used to track tap changers and shunts section changes
 * @param elementId
 * @param oldPosition
 * @param newPosition
 *  *
 *  * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public record DiscreteControllerChangeDetails(String elementId, int oldPosition, int newPosition) {
}
