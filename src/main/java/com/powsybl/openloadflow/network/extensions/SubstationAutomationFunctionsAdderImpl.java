/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Substation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SubstationAutomationFunctionsAdderImpl extends AbstractExtensionAdder<Substation, SubstationAutomationFunctions> implements SubstationAutomationFunctionsAdder {

    private final List<OverloadManagementFunction> overloadManagementFunctions = new ArrayList<>();

    public SubstationAutomationFunctionsAdderImpl(Substation substation) {
        super(substation);
    }

    @Override
    public Class<? super SubstationAutomationFunctions> getExtensionClass() {
        return SubstationAutomationFunctions.class;
    }

    @Override
    public OverloadManagementFunctionAdder<SubstationAutomationFunctionsAdder> newOverloadManagementFunction() {
        return new OverloadManagementFunctionAdderImpl<>(this, overloadManagementFunctions::add);
    }

    @Override
    protected SubstationAutomationFunctions createExtension(Substation substation) {
        return new SubstationAutomationFunctionsImpl(substation, overloadManagementFunctions);
    }
}
