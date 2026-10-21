/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.extensions;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionAdderProvider;
import com.powsybl.iidm.network.Generator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
@AutoService(ExtensionAdderProvider.class)
public class GeneratorFortescueTypeAdderImplProvider implements ExtensionAdderProvider<Generator, GeneratorFortescueType, GeneratorFortescueTypeAdder> {

    @Override
    public String getImplementationName() {
        return "Default";
    }

    @Override
    public String getExtensionName() {
        return GeneratorFortescueType.NAME;
    }

    @Override
    public Class<GeneratorFortescueTypeAdder> getAdderClass() {
        return GeneratorFortescueTypeAdder.class;
    }

    @Override
    public GeneratorFortescueTypeAdder newAdder(Generator generator) {
        return new GeneratorFortescueTypeAdder(generator);
    }
}
