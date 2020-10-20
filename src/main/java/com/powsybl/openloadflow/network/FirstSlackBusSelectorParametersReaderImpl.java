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
public class FirstSlackBusSelectorParametersReaderImpl implements SlackBusSelectorParametersReader {

    @Override
    public String getName() {
        return "First";
    }

    @Override
    public SlackBusSelector read(ModuleConfig moduleConfig) {
        return new FirstSlackBusSelector();
    }
}
