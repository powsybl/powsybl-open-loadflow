/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityVariableSetJsonDeserializer extends StdDeserializer<SensitivityVariableSet> {

    public SensitivityVariableSetJsonDeserializer() {
        super(SensitivityVariableSet.class);
    }

    @Override
    public SensitivityVariableSet deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        return SensitivityVariableSet.parseJson(jsonParser);
    }
}
