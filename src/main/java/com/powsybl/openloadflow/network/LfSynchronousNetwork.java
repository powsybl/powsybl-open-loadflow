/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.report.ReportNode;

import java.util.List;
import java.util.Set;

/**
 * Methods specific to a network in a single synchronous component.
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public interface LfSynchronousNetwork {

    /**
     * @return The synchronous component number
     */
    int getNumSC();

    /**
     * @return The reference bus of the synchronous component (its voltage angle is 0)
     */
    LfBus getReferenceBus();

    /**
     * @return The reference generator of the synchronous component. If there is not one, returns null.
     */
    LfGenerator getReferenceGenerator();

    /**
     * @return The list of slack buses in the synchronous component
     */
    List<LfBus> getSlackBuses();

    /**
     * Get the excluded buses (i.e. separated from the main network due to a contingency).
     *
     * @return A set of buses that cannot be selected as slack buses.
     */
    Set<LfBus> getExcludedSlackBuses();

    /**
     * Set the excluded buses (i.e. separated from the main network due to a contingency).
     *
     * @param excludedSlackBuses buses that cannot be set as slack bus anymore.
     */
    void setExcludedSlackBuses(Set<LfBus> excludedSlackBuses);

    /**
     * Ensure the synchronous component contains at least one generator. If loadFlowModel is AC, we also make sure at
     * least one generator controls voltage
     *
     * @param loadFlowModel either AC or DC
     * @param reportNode    A report node object
     * @return Whether this synchronous component is valid
     */
    LfNetwork.Validity validateBuses(LoadFlowModel loadFlowModel, ReportNode reportNode);

    /**
     * Get the list of buses inside this synchronous component. It is only a view of the buses defined in a LfNetwork.
     *
     * @return The list of AC buses in this synchronous component.
     */
    List<LfBus> getBuses();

    /**
     * Unset slack bus, reference bus and reference generator.
     * Meant to be use only by implementations and the LfNetwork class.
     */
    void invalidateSlackAndReference();

    /**
     * If not already set, select the slack buses, the reference bus and the reference generator (if the reference bus
     * is selected via the reference generator).
     * If the LfSynchronousNetwork is the main one of a LfNetwork, its first slack bus is the main vertex of the
     * LfNetwork connectivity graph. Therefore, this method might also update the connectivity graph if it is not null.
     * Meant to be use only by implementations and the LfNetwork class.
     */
    void updateSlackBusesAndReferenceBus();
}
