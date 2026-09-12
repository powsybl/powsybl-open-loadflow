/**
 * Copyright (c) 2025, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.List;

/**
 * @author Denis Bonnand {@literal <denis.bonnand at supergrid-institute.com>}
 */
public interface LfVoltageSourceConverter extends LfAcDcConverter {

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

    /**
     * Reference point of the droop law {@code P = refP + k*(U_dc - refVdc)} for a solved DC voltage, all in per unit.
     *
     * @param k      the droop coefficient of the band containing the solved DC voltage.
     * @param refVdc the reference DC voltage of that band (its lower bound).
     * @param refP   the reference active power of that band (the anchored power at {@code refVdc}).
     */
    record LfDroopReference(double k, double refVdc, double refP) {
    }

    /**
     * Get the droop curve as a sorted list of segments, each with its min voltage,
     * reference active power and droop coefficient.
     * @return Droop curve as sorted list.
     */
    List<LfDroopReference> getDroopCurve();
}
