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
public interface LfVoltageSourceConverter extends LfAcDcConverter, LfCopyable<LfVoltageSourceConverter> {

    /**
     * @return If true, the voltage source converter controls AC voltage magnitude. Else it controls reactive power.
     */
    boolean isVoltageRegulatorOn();

    /**
     * @return The AC reactive power requested by the converter from the AC network. In per unit
     * Positive value means the power flows from AC network to DC network.
     */
    double getTargetQ();

    /**
     * @return the target AC voltage magnitude at the converter AC bus. In per unit.
     */
    double getTargetVac();
}
