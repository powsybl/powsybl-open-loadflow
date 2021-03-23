/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.google.auto.service.AutoService;
import com.powsybl.commons.config.ModuleConfig;

/**
 * @author Thomas Adam <tadam at silicom.fr>
 */
@AutoService(SlackBusSelectorParametersReader.class)
public class NameSlackBusSelectorParametersReader implements SlackBusSelectorParametersReader {

    public static final String NAME = "Name";

    @Override
    public String getName() {
        return "Name";
    }

    @Override
    public SlackBusSelector read(ModuleConfig moduleConfig) {
        String busId = moduleConfig.getStringProperty("nameSlackBusSelectorBusId");
        return new NameSlackBusSelector(busId);
    }
}
