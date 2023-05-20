/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Substation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SubstationAutomationFunctionsImpl extends AbstractExtension<Substation> implements SubstationAutomationFunctions {

    private final List<OverloadManagementFunction> overloadManagementFunctions;

    public SubstationAutomationFunctionsImpl(Substation substation, List<OverloadManagementFunction> overloadManagementFunctions) {
        super(substation);
        this.overloadManagementFunctions = Objects.requireNonNull(overloadManagementFunctions);
    }

    @Override
    public List<OverloadManagementFunction> getOverloadManagementFunctions() {
        return Collections.unmodifiableList(overloadManagementFunctions);
    }

    @Override
    public OverloadManagementFunctionAdder<SubstationAutomationFunctions> newOverloadManagementFunction() {
        return new OverloadManagementFunctionAdderImpl<>(this, overloadManagementFunctions::add);
    }
}
