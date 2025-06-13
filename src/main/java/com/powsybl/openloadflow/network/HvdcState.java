/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class HvdcState extends ElementState<LfHvdc> {

    private final boolean acEmulation;
    private final double angleDifferenceToFreeze;
    private final boolean acEmulationFrozen;

    public HvdcState(LfHvdc hvdc) {
        super(hvdc);
        this.acEmulation = hvdc.isAcEmulation();
        this.acEmulationFrozen = hvdc.isAcEmulationFrozen();
        this.angleDifferenceToFreeze = hvdc.getAngleDifferenceToFreeze();
    }

    public static HvdcState save(LfHvdc hvdc) {
        return new HvdcState(hvdc);
    }

    @Override
    public void restore() {
        super.restore();
        element.setAcEmulation(acEmulation);
        element.setAcEmulationFrozen(acEmulationFrozen);
        element.setAngleDifferenceToFreeze(angleDifferenceToFreeze);
    }
}
