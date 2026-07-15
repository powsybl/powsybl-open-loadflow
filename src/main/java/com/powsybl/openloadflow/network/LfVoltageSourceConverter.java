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
    record DroopReference(double k, double refVdc, double refP) {
    }

    /**
     * @return the per-unit base used for the DC voltage of this converter (nominal voltage of the non-grounded DC bus, in kV).
     */
    double getDcVoltageBase();

    /**
     * Look up the droop reference point for a given solved DC voltage. Only relevant when the converter is in
     * {@code P_PCC_DROOP} control mode.
     *
     * @param uDc the solved pole-to-pole DC voltage, in per unit of {@link #getDcVoltageBase()}.
     * @return the droop reference {@code (k, refVdc, refP)} of the band containing {@code uDc} (clamped to the
     * nearest band outside the curve range), all in per unit.
     */
    DroopReference getDroopReference(double uDc);
}
