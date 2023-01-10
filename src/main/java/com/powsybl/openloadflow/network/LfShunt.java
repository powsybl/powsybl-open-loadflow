/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface LfShunt extends LfElement {

    double getB();

    void setB(double b);

    double dispatchB();

    double getG();

    void setG(double g);

    void updateState(LfNetworkStateUpdateParameters parameters);

    boolean hasVoltageControlCapability();

    void setVoltageControlCapability(boolean voltageControlCapability);

    boolean isVoltageControlEnabled();

    void setVoltageControlEnabled(boolean voltageControlEnabled);

    Optional<ShuntVoltageControl> getVoltageControl();

    void setVoltageControl(ShuntVoltageControl voltageControl);
}
