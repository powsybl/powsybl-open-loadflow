/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfDcBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfVoltageSourceConverter;
import com.powsybl.openloadflow.util.Reports;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
        dcBusInitialVoltage.clear();
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
            } else {
                // Both have been already set by a previous converter, nothing to do
                if (Objects.equals(dcBusInitialVoltage.get(dcBus1), dcBusInitialVoltage.get(dcBus2))) {
                    throw new PowsyblException("Could not initialize DC bus voltage properly. Two DC buses connected to " +
                        "the same converter have been initialized at the same voltage");
                }
            }
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

    /**
     * Get the initial voltage of a DC bus. If the DC bus is connected to a converter, its initial voltage has been
     * computed in prepare. Otherwise, it defaults to 1 pu.
     * @param dcBus: DC bus whose initial voltage is requested.
     * @return The DC voltage of the DC bus in per unit.
     */
    @Override
    public double getDcVoltage(LfDcBus dcBus) {
        return dcBusInitialVoltage.getOrDefault(dcBus, 1.0);
    }
}

