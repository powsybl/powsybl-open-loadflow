/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.powsybl.openloadflow.sensi.apiv2.SimpleSensitivityFactor;

import java.io.IOException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleSensitivityFactorSerializer extends StdSerializer<SimpleSensitivityFactor> {

    SimpleSensitivityFactorSerializer() {
        super(SimpleSensitivityFactor.class);
    }

    @Override
    public void serialize(SimpleSensitivityFactor factor, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        factor.writeJson(jsonGenerator);
    }
}
