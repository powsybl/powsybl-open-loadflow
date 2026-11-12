/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.reduction;

/**
 * The Ward equivalent types used for network reduction.
 *
 * @author Romain Courtier {@literal <romain.courtier at rte-france.com>}
 */
public enum WardEquivalentType {

    /**
     * <p>All network elements are considered without conversion.<br/>
     * The external network is reduced to:</p>
     * <ul>
     *   <li>equivalent branches between boundary buses</li>
     *   <li>equivalent shunts on boundary buses</li>
     *   <li>equivalent injections on boundary buses</li>
     * </ul>
     */
    STANDARD
    // ADMITTANCE
    // INJECTION
    // PV
    // EXTENDED
}
