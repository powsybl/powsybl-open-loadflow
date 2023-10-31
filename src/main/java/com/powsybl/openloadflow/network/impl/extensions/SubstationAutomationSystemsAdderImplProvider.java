/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Substation;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
@AutoService(ExtensionAdderProvider.class)
public class SubstationAutomationSystemsAdderImplProvider implements
        ExtensionAdderProvider<Substation, SubstationAutomationSystems, SubstationAutomationSystemsAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return SubstationAutomationSystems.NAME;
    }

    @Override
    public Class<SubstationAutomationSystemsAdder> getAdderClass() {
        return SubstationAutomationSystemsAdder.class;
    }

    @Override
    public SubstationAutomationSystemsAdderImpl newAdder(Substation substation) {
        return new SubstationAutomationSystemsAdderImpl(substation);
    }
}
