/**
 * Copyright (c) 2023, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

/**
 * @author Alexandre Le Jean {@literal <alexandre.le-jean at artelys.com>}
 */
public enum NewtonRaphsonStoppingCriteriaType {
    UNIFORM_CRITERIA,
    PER_EQUATION_TYPE_CRITERIA
}
