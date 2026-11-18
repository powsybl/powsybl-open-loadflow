/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.na.network.util;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.na.network.LfBus;
import com.powsybl.na.network.LfDcBus;
import com.powsybl.na.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface VoltageInitializer {

    void prepare(LfNetwork network, ReportNode reportNode);

    double getMagnitude(LfBus bus);

    double getAngle(LfBus bus);

    double getDcVoltage(LfDcBus dcBus);

}
