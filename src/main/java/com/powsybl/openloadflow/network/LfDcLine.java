/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public interface LfDcLine extends LfElement, LfCopyable<LfDcLine> {

    /**
     * @return The LfDcBus on side one of the DC line
     */
    LfDcBus getDcBus1();

    /**
     * @return The LfDcBus on side two of the DC line
     */
    LfDcBus getDcBus2();

    /**
     * @return The resistance of the DC line in Ohm.
     */
    double getR();

    /**
     * @param i1 evaluable computing in per unit the current entering the DC line in DC bus 1.
     */
    void setI1(Evaluable i1);

    /**
     * @param i2 evaluable computing in per unit the current entering the DC line in DC bus 2.
     */
    void setI2(Evaluable i2);

    /**
     * @param p1 evaluable computing in per unit the power entering the DC line in DC bus 1.
     */
    void setP1(Evaluable p1);

    /**
     * @param p2 evaluable computing in per unit the power entering the DC line in DC bus 2.
     */
    void setP2(Evaluable p2);

    /**
     * Update the DC line state after the load flow.
     *
     * @param parameters   Parameters of state update.
     * @param updateReport report of connected/disconnected branches and open/closed switch count.
     */
    void updateState(LfNetworkStateUpdateParameters parameters, LfNetworkUpdateReport updateReport);

    /**
     * Update the DC line terminals current and power at the end of the load flow.
     *
     * @param i1 current entering the DC line in DC bus 1. In per unit.
     * @param i2 current entering the DC line in DC bus 2. In per unit.
     * @param p1 power entering the DC line in DC bus 1. In per unit.
     * @param p2 power entering the DC line in DC bus 2. In per unit.
     */
    void updateFlows(double i1, double i2, double p1, double p2);
}
