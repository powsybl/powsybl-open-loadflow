/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.openloadflow.ac.VoltageMagnitudeInitializer;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.util.VoltageInitializer;

import java.util.Objects;

/**
 * A voltage initializer that rely on {@link VoltageMagnitudeInitializer} for magnitude calculation and on
 * {@link DcValueVoltageInitializer} for angle calculation.
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class FullVoltageInitializer implements VoltageInitializer {

    private final VoltageMagnitudeInitializer magnitudeInitializer;

    private final DcValueVoltageInitializer angleInitializer;

    public FullVoltageInitializer(VoltageMagnitudeInitializer magnitudeInitializer, DcValueVoltageInitializer angleInitializer) {
        this.magnitudeInitializer = Objects.requireNonNull(magnitudeInitializer);
        this.angleInitializer = Objects.requireNonNull(angleInitializer);
    }

    @Override
    public void prepare(LfNetwork network) {
        magnitudeInitializer.prepare(network);
        angleInitializer.prepare(network);
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return magnitudeInitializer.getMagnitude(bus);
    }

    @Override
    public double getAngle(LfBus bus) {
        return angleInitializer.getAngle(bus);
    }
}
