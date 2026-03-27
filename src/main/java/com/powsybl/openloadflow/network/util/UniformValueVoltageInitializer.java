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
import com.powsybl.openloadflow.util.Reports;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class UniformValueVoltageInitializer implements VoltageInitializer {

    public static final String NAME = "Uniform Values";

    @Override
    public void prepare(LfNetwork network, ReportNode reportNode) {
        Reports.reportVoltageInitializer(reportNode, NAME);
    }

    @Override
    public double getMagnitude(LfBus bus) {
        return 1;
    }

    @Override
    public double getAngle(LfBus bus) {
        return 0;
    }

    public double getMagnitude(LfDcBus dcBus) {
        if (dcBus.isNeutralPole()) {
            return 0.0;
        }
        return 1.0;
    }
}

