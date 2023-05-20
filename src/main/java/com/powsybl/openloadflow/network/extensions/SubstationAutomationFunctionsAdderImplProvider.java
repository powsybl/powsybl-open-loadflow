/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.network.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Substation;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class SubstationAutomationFunctionsAdderImplProvider implements
        ExtensionAdderProvider<Substation, SubstationAutomationFunctions, SubstationAutomationFunctionsAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return SubstationAutomationFunctions.NAME;
    }

    @Override
    public Class<SubstationAutomationFunctionsAdder> getAdderClass() {
        return SubstationAutomationFunctionsAdder.class;
    }

    @Override
    public SubstationAutomationFunctionsAdder newAdder(Substation substation) {
        return new SubstationAutomationFunctionsAdderImpl(substation);
    }
}
