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
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MultipleVariablesSensitivityFactor extends AbstractSensitivityFactor {

    private final List<WeightedSensitivityVariable> variables;

    public MultipleVariablesSensitivityFactor(SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                                              String variableId, List<WeightedSensitivityVariable> variables, ContingencyContext contingencyContext) {
        super(functionType, functionId, variableType, variableId, contingencyContext);
        this.variables = Objects.requireNonNull(variables);
    }

    @Override
    public SensitivityFactorType getType() {
        return SensitivityFactorType.MULTIPLE_VARIABLES;
    }

    public List<WeightedSensitivityVariable> getVariables() {
        return variables;
    }

    static void writeJson(JsonGenerator jsonGenerator, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                          String variableId, List<WeightedSensitivityVariable> variables, ContingencyContext contingencyContext) {
        try {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("factorType", "MULTIPLE_VARIABLES");
            jsonGenerator.writeStringField("functionType", functionType.name());
            jsonGenerator.writeStringField("functionId", functionId);
            jsonGenerator.writeStringField("variableType", variableType.name());
            jsonGenerator.writeStringField("variableId", variableId);
            jsonGenerator.writeFieldName("variables");
            jsonGenerator.writeStartArray();
            for (WeightedSensitivityVariable variable : variables) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("id", variable.getId());
                jsonGenerator.writeNumberField("weight", variable.getWeight());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
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
