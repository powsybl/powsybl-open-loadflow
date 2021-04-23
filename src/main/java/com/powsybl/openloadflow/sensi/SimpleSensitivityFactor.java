/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleSensitivityFactor extends AbstractSensitivityFactor {

    public SimpleSensitivityFactor(SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                                   String variableId, ContingencyContext contingencyContext) {
        super(functionType, functionId, variableType, variableId, contingencyContext);
    }

    @Override
    public SensitivityFactorType getType() {
        return SensitivityFactorType.SIMPLE;
    }

    static void writeJson(JsonGenerator jsonGenerator, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                          String variableId, ContingencyContext contingencyContext) {
        try {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("factorType", "SIMPLE");
            jsonGenerator.writeStringField("functionType", functionType.name());
            jsonGenerator.writeStringField("functionId", functionId);
            jsonGenerator.writeStringField("variableType", variableType.name());
            jsonGenerator.writeStringField("variableId", variableId);
            jsonGenerator.writeStringField("contingencyContextType", contingencyContext.getContextType().name());
            if (contingencyContext.getContingencyId() != null) {
                jsonGenerator.writeStringField("contingencyId", contingencyContext.getContingencyId());
            }

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
