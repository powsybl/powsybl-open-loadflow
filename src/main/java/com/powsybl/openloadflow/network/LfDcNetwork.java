/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;

import java.util.List;

/**
 * Methods specific to a network in a single DC component.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public interface LfDcNetwork {

    /**
     * @return The DC component number
     */
    int getNumDCC();

    /**
     * Check that a DC component is a configuration Open Load Flow can solve, and resolve any DC subcomponent (i.e. set
     * of DC buses connected through DC lines only) that has no element imposing the DC voltage (no connected DC
     * ground and no fully connected V_DC/DC_DROOP converter), by selecting all fully connected P_PCC
     * converters of that subcomponent to be promoted to V_DC control internally. Disconnected converters are
     * ignored: only fully connected ones take part in the checks/resolution.
     *
     * @return A list of AC-DC converter promoted to V_DC control, to use instead of the converter's own IIDM value;
     * empty if no promotion was necessary.
     * @throws PowsyblException If the DC component configuration is invalid and cannot be resolved automatically.
     */
    List<LfAcDcConverter> resolve();
}
