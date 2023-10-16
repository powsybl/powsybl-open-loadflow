/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Substation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SubstationAutomationSystemsAdderImpl extends AbstractExtensionAdder<Substation, SubstationAutomationSystems> implements SubstationAutomationSystemsAdder {

    private final List<OverloadManagementSystem> overloadManagementSystems = new ArrayList<>();

    public SubstationAutomationSystemsAdderImpl(Substation substation) {
        super(substation);
    }

    @Override
    public Class<? super SubstationAutomationSystems> getExtensionClass() {
        return SubstationAutomationSystems.class;
    }

    @Override
    public OverloadManagementSystemAdder<SubstationAutomationSystemsAdder> newOverloadManagementSystem() {
        return new OverloadManagementSystemAdderImpl<>(this, overloadManagementSystems::add);
    }

    @Override
    protected SubstationAutomationSystemsImpl createExtension(Substation substation) {
        return new SubstationAutomationSystemsImpl(substation, overloadManagementSystems);
    }
}
