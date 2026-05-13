/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;
import com.powsybl.openloadflow.util.Reports;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class UniformValueVoltageInitializer implements VoltageInitializer {

    public static final String NAME = "Uniform Values";

    private final Map<LfDcBus, Double> dcBusInitialVoltage = new HashMap<>();

    @Override
    public void prepare(LfNetwork network, ReportNode reportNode) {
        Reports.reportVoltageInitializer(reportNode, NAME);

        // We need to ensure that two LfDcBuses connected to the same converter are initialized with different voltage
        // values. There is no such constraints for other LfDcBuses.
        for (LfVoltageSourceConverter converter : network.getVoltageSourceConverters()) {
            LfDcBus dcBus1 = converter.getDcBus1();
            LfDcBus dcBus2 = converter.getDcBus2();

            if (!dcBusInitialVoltage.containsKey(dcBus1) && !dcBusInitialVoltage.containsKey(dcBus2)) {
                dcBusInitialVoltage.put(dcBus1, 1.0);
                dcBusInitialVoltage.put(dcBus2, 0.0);
            } else if (dcBusInitialVoltage.containsKey(dcBus1) && !dcBusInitialVoltage.containsKey(dcBus2)) {
                // Complement ensures the two DC buses get distinct values
                dcBusInitialVoltage.put(dcBus2, 1.0 - dcBusInitialVoltage.get(dcBus1));
            } else if (!dcBusInitialVoltage.containsKey(dcBus1)) {
                // Complement ensures the two DC buses get distinct values
                dcBusInitialVoltage.put(dcBus1, 1.0 - dcBusInitialVoltage.get(dcBus2));
            }
            // else: both already set by a previous converter, nothing to do
        }
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return 1;
    }

    @Override
    public double getAngle(LfBus bus) {
        return 0;
    }

    @Override
    public double getDcVoltage(LfDcBus dcBus) {
        return dcBusInitialVoltage.getOrDefault(dcBus, 1.0);
    }
}

