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
public interface LfVoltageSourceConverter extends LfAcDcConverter {

    /**
     * Get whether the voltage source converter controls AC voltage magnitude or reactive power.
     *
     * @return whether the voltage source converter controls AC voltage magnitude or reactive power.
     */
    boolean isVoltageRegulatorOn();

    /**
     * Get the AC reactive power the converter request from the AC network.
     *
     * @return The AC reactive power requested by the converter from the AC network. In per unit
     */
    double getTargetQ();

    /**
     * Get the target AC voltage magnitude at the converter AC bus.
     *
     * @return the target AC voltage magnitude at the converter AC bus. In per unit.
     */
    double getTargetVac();
}
