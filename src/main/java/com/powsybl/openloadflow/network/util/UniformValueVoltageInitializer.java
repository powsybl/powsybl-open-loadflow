/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.extensions.AsymBus;
import com.powsybl.openloadflow.util.Fortescue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class UniformValueVoltageInitializer implements VoltageInitializer {

    @Override
    public void prepare(LfNetwork network) {
        // nothing to do
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return 1;
    }

    @Override
    public double getAngle(LfBus bus) {
        return 0;
    }

    public static double getMagnitude(LfBus bus, Fortescue.SequenceType sequenceType) {
        AsymBus asymBus = AsymBus.getAsymBus(bus);
        if (asymBus == null) {
            return 1;
        }
        return AsymUniformValueVoltageInitializer.getMagnitude(bus, asymBus, sequenceType);
    }

    public static double getAngle(LfBus bus, Fortescue.SequenceType sequenceType) {
        AsymBus asymBus = AsymBus.getAsymBus(bus);
        if (asymBus == null) {
            return 0;
        }
        return AsymUniformValueVoltageInitializer.getAngle(bus, asymBus, sequenceType);
    }
}
