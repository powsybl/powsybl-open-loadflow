/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Generator;

import static com.powsybl.sc.extensions.FortescueConstants.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class GeneratorFortescueTypeAdder extends AbstractExtensionAdder<Generator, GeneratorFortescueType> {

    private GeneratorFortescueType.GeneratorType generatorType = DEFAULT_GENERATOR_FORTESCUE_TYPE;

    public GeneratorFortescueTypeAdder(Generator generator) {
        super(generator);
    }

    @Override
    public Class<? super GeneratorFortescueType> getExtensionClass() {
        return GeneratorFortescueType.class;
    }

    @Override
    protected GeneratorFortescueType createExtension(Generator generator) {
        return new GeneratorFortescueType(generator, generatorType);
    }

    public GeneratorFortescueTypeAdder withGeneratorType(GeneratorFortescueType.GeneratorType generatorType) {
        this.generatorType = generatorType;
        return this;
    }
}
