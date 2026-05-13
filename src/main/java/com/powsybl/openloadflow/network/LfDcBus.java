/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public interface LfDcBus extends LfElement {

    /**
     * Get the DC bus voltage.
     *
     * @return The DC bus voltage in per unit.
     */
    double getV();

    /**
     * Set the DC bus voltage.
     *
     * @param v DC bus voltage in per unit.
     */
    void setV(double v);

    /**
     * Get the DC bus nominal voltage.
     *
     * @return The DC bus nominal voltage in kV.
     */
    double getNominalV();

    /**
     * Get whether the DC bus is connected to the ground.
     *
     * @return Whether the DC bus is grounded
     */
    boolean isGrounded();

    /**
     * Set whether the DC bus is connected to the ground.
     *
     * @param isGrounded Whether the DC bus is grounded.
     */
    void setGround(boolean isGrounded);

    /**
     * Update the DC bus state after the load flow.
     *
     * @param parameters Parameters of state update.
     */
    void updateState(LfNetworkStateUpdateParameters parameters);
}
