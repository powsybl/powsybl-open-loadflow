/**
 * Copyright (c) 2026, SuperGrid Institute (http://www.supergrid-institute.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.AcDcConverter;

/**
 * State of a LfAcDcConverter, storing its control mode and target active power
 *
 * @author Baptiste Perreyon {@literal <baptiste.perreyon at supergrid-institute.com>}
 */
public class AcDcConverterState extends ElementState<LfAcDcConverter> {

    private final double targetP;
    private final AcDcConverter.ControlMode controlMode;

    public AcDcConverterState(LfAcDcConverter converter) {
        super(converter);
        this.targetP = converter.getTargetP();
        this.controlMode = converter.getControlMode();
    }

    public static AcDcConverterState save(LfAcDcConverter converter) {
        return new AcDcConverterState(converter);
    }

    @Override
    public void restore() {
        super.restore();
        element.setTargetP(targetP);
        element.setControlMode(controlMode);
    }
}
