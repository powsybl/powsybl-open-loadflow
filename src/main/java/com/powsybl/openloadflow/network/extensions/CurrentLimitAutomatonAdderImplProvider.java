/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.network.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Line;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class CurrentLimitAutomatonAdderImplProvider implements
        ExtensionAdderProvider<Line, CurrentLimitAutomaton, CurrentLimitAutomatonAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return CurrentLimitAutomaton.NAME;
    }

    @Override
    public Class<CurrentLimitAutomatonAdder> getAdderClass() {
        return CurrentLimitAutomatonAdder.class;
    }

    @Override
    public CurrentLimitAutomatonAdder newAdder(Line line) {
        return new CurrentLimitAutomatonAdderImpl(line);
    }
}
