/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Terminal;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public enum NoOpNominalVoltageMapping implements NominalVoltageMapping {
    INSTANCE;

    @Override
    public double get(Terminal terminal) {
        return terminal.getVoltageLevel().getNominalV();
    }

    @Override
    public double get(Bus bus) {
        return bus.getVoltageLevel().getNominalV();
    }
}
