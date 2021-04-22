/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.commons.json.JsonUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class JsonSensitivityFactoryReader implements SensitivityFactorReader {

    static class JsonHandler implements SensitivityFactorReader.Handler {

        private final JsonGenerator jsonGenerator;

        public JsonHandler(JsonGenerator jsonGenerator) {
            this.jsonGenerator = Objects.requireNonNull(jsonGenerator);
        }

        @Override
        public void onSimpleFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                                   String variableId, SensitivityFactorReader.ContingencyContext contingencyContext) {
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

        @Override
        public void onMultipleVariablesFactor(Object factorContext, SensitivityFunctionType functionType, String functionId,
                                              SensitivityVariableType variableType, String variableId, List<WeightedSensitivityVariable> variables,
                                              SensitivityFactorReader.ContingencyContext contingencyContext) {
            try {
                jsonGenerator.writeStartObject();

                jsonGenerator.writeStringField("factorType", "MULTIPLE_VARIABLES");
                jsonGenerator.writeStringField("functionType", functionType.name());
                jsonGenerator.writeStringField("functionId", functionId);
                jsonGenerator.writeStringField("variableType", variableType.name());
                jsonGenerator.writeStringField("variableId", variableId);
                jsonGenerator.writeStartArray();
                for (WeightedSensitivityVariable variable : variables) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("id", variable.getId());
                    jsonGenerator.writeNumberField("weight", variable.getWeight());
                    jsonGenerator.writeEndObject();
                }
                jsonGenerator.writeEndArray();
                jsonGenerator.writeString("contingencyContext");
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

    private final SensitivityFactorReader reader;

    private final Path jsonFile;

    public JsonSensitivityFactoryReader(SensitivityFactorReader reader, Path jsonFile) {
        this.reader = Objects.requireNonNull(reader);
        this.jsonFile = Objects.requireNonNull(jsonFile);
    }

    @Override
    public void read(Handler handler) {
        JsonUtil.writeJson(jsonFile, jsonGenerator -> {
            try {
                jsonGenerator.writeStartArray();

                reader.read(new Handler() {

                    private final JsonHandler jsonHandler = new JsonHandler(jsonGenerator);

                    @Override
                    public void onSimpleFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, ContingencyContext contingencyContext) {
                        jsonHandler.onSimpleFactor(factorContext, functionType, functionId, variableType, variableId, contingencyContext);
                        handler.onSimpleFactor(factorContext, functionType, functionId, variableType, variableId, contingencyContext);
                    }

                    @Override
                    public void onMultipleVariablesFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, List<WeightedSensitivityVariable> variables, ContingencyContext contingencyContext) {
                        jsonHandler.onMultipleVariablesFactor(factorContext, functionType, functionId, variableType, variableId, variables, contingencyContext);
                        handler.onMultipleVariablesFactor(factorContext, functionType, functionId, variableType, variableId, variables, contingencyContext);
                    }
                });

                jsonGenerator.writeEndArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
