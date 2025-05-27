/**
 * Copyright (c) 2023, Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 * Copyright (c) 2023, Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.extensions.iidm;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Bus;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class BusAsymmetricalAdderImplProvider implements ExtensionAdderProvider<Bus, BusAsymmetrical, BusAsymmetricalAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LineAsymmetrical.NAME;
    }

    @Override
    public Class<BusAsymmetricalAdder> getAdderClass() {
        return BusAsymmetricalAdder.class;
    }

    @Override
    public BusAsymmetricalAdder newAdder(Bus bus) {
        return new BusAsymmetricalAdder(bus);
    }

}
