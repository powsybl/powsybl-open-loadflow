/**
 * Copyright (c) 2024, Artelys (https://www.artelys.com/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.knitroextension;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */
public interface KnitroSolverStoppingCriteria {

    double DEFAULT_CONV_EPS_PER_EQ = Math.pow(10, -6);

}
