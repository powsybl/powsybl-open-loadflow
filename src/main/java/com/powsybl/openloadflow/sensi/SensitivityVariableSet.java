/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityVariableSet {

    private final String id;

    private final List<WeightedSensitivityVariable> variables;

    public SensitivityVariableSet(String id, List<WeightedSensitivityVariable> variables) {
        this.id = Objects.requireNonNull(id);
        this.variables = Objects.requireNonNull(variables);
    }

    public String getId() {
        return id;
    }

    public List<WeightedSensitivityVariable> getVariables() {
        return variables;
    }

    @Override
    public String toString() {
        return "SensitivityVariableSet(" +
                "id='" + id + '\'' +
                ", variables=" + variables +
                ')';
    }

    static void writeJson(JsonGenerator jsonGenerator, SensitivityVariableSet variableSet) {
        try {
            jsonGenerator.writeStartObject();

            jsonGenerator.writeStringField("id", variableSet.getId());
            jsonGenerator.writeFieldName("variables");
            jsonGenerator.writeStartArray();
            for (WeightedSensitivityVariable variable : variableSet.getVariables()) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("id", variable.getId());
                jsonGenerator.writeNumberField("weight", variable.getWeight());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();

            jsonGenerator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class ParsingContext {

        private String id;

        private List<WeightedSensitivityVariable> variables;

        private void reset() {
            id = null;
            variables = null;
        }
    }

    static List<SensitivityVariableSet> parseJson(JsonParser parser) {
        Objects.requireNonNull(parser);

        List<SensitivityVariableSet> variableSets = new ArrayList<>();
        try {
            ParsingContext context = new ParsingContext();
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    switch (fieldName) {
                        case "id":
                            context.id = parser.nextTextValue();
                            break;
                        case "variables":
                            context.variables = WeightedSensitivityVariable.parseJson(parser);
                            break;
                        default:
                            break;
                    }
                } else if (token == JsonToken.END_ARRAY) {
                    variableSets.add(new SensitivityVariableSet(context.id, context.variables));
                    context.reset();
                } else if (token == JsonToken.END_OBJECT) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return variableSets;
    }
}
