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
import com.powsybl.iidm.network.Generator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class GeneratorShortCircuitAdderImplProvider2
        implements ExtensionAdderProvider<Generator, GeneratorShortCircuit2, GeneratorShortCircuitAdder2> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return GeneratorShortCircuit2.NAME;
    }

    @Override
    public Class<GeneratorShortCircuitAdder2> getAdderClass() {
        return GeneratorShortCircuitAdder2.class;
    }

    @Override
    public GeneratorShortCircuitAdder2 newAdder(Generator generator) {
        return new GeneratorShortCircuitAdder2(generator);
    }
}
