/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.openloadflow.ac.newfiles.LfAcDcConverter;
import com.powsybl.openloadflow.ac.newfiles.LfDcNode;
import com.powsybl.openloadflow.ac.newfiles.LfVoltageSourceConverter;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public interface VoltageInitializer {
//TODO : Implement clean Voltage Initializer for Dc Elements
    void prepare(LfNetwork network);

    double getMagnitude(LfBus bus);

    double getReactivePower(LfVoltageSourceConverter converter);

    double getActivePower(LfAcDcConverter converter);

    double getCurrent(LfAcDcConverter converter);

    double getMagnitude(LfDcNode dcNode);

    double getAngle(LfBus bus);
}
