/**
 * Copyright (c) 2022, Jean-Baptiste Heyberger & Geoffroy Jamgotchian
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.sc.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Generator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at gmail.com>
 */
public class GeneratorFortescueType extends AbstractExtension<Generator> {

    public static final String NAME = "generatorFortescueType";

    public enum GeneratorType {
        UNKNOWN,
        ROTATING_MACHINE,
        FEEDER
    }

    private final GeneratorType generatorType;

    @Override
    public String getName() {
        return NAME;
    }

    public GeneratorFortescueType(Generator generator, GeneratorType generatorType) {
        super(generator);
        this.generatorType = generatorType;

    }

    public GeneratorType getGeneratorType() {
        return generatorType;
    }
}
