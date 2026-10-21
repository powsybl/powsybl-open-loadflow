/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.sc.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Load;

/**
 * @author Jean-Baptiste Heyberger <jbheyberger at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class LoadShortCircuitAdderImplProvider
        implements ExtensionAdderProvider<Load, LoadShortCircuit, LoadShortCircuitAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return LoadShortCircuit.NAME;
    }

    @Override
    public Class<LoadShortCircuitAdder> getAdderClass() {
        return LoadShortCircuitAdder.class;
    }

    @Override
    public LoadShortCircuitAdder newAdder(Load load) {
        return new LoadShortCircuitAdder(load);
    }
}
