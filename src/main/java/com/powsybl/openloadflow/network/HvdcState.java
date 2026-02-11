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
    private final LfHvdc.AcEmulationControl.AcEmulationStatus acEmulationStatus;
    private final double vsc1TargetP;
    private final double vsc2TargetP;

    public HvdcState(LfHvdc hvdc) {
        super(hvdc);
        this.acEmulation = hvdc.isAcEmulation();
        if (this.acEmulation) {
            acEmulationStatus = hvdc.getAcEmulationControl().getAcEmulationStatus();
            // VSCs targetP are stored to be used if the AC emulation is in saturated mode
            vsc1TargetP = hvdc.getConverterStation1().getTargetP();
            vsc2TargetP = hvdc.getConverterStation2().getTargetP();
        } else {
            vsc1TargetP = Double.NaN;
            vsc2TargetP = Double.NaN;
            acEmulationStatus = null;
        }
    }

    public static HvdcState save(LfHvdc hvdc) {
        return new HvdcState(hvdc);
    }

    @Override
    public void restore() {
        super.restore();
        element.setAcEmulation(acEmulation);
        if (acEmulation) {
            element.getConverterStation1().setTargetP(vsc1TargetP);
            element.getConverterStation2().setTargetP(vsc2TargetP);
            element.updateAcEmulationStatus(acEmulationStatus);
        }
    }
}
