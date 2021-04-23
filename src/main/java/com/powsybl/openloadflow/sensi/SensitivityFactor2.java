/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Stopwatch;
import com.powsybl.commons.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SensitivityFactor2 {

    Logger LOGGER = LoggerFactory.getLogger(SensitivityFactor2.class);

    SensitivityFactorType getType();

    static List<SensitivityFactor2> parseJson(Path jsonFile) {
        return JsonUtil.parseJson(jsonFile, SensitivityFactor2::parseJson);
    }

    final class ParsingContext {
        private SensitivityFactorType factorType;
        private SensitivityFunctionType functionType;
        private String functionId;
        private SensitivityVariableType variableType;
        private String variableId;
        private List<WeightedSensitivityVariable> variables;
        private ContingencyContextType contingencyContextType;
        private String contingencyId;

        private void reset() {
            factorType = null;
            functionType = null;
            functionId = null;
            variableType = null;
            variableId = null;
            variables = null;
            contingencyContextType = null;
            contingencyId = null;
        }
    }

    static List<SensitivityFactor2> parseJson(JsonParser parser) {
        Objects.requireNonNull(parser);

        Stopwatch stopwatch = Stopwatch.createStarted();

        List<SensitivityFactor2> factors = new ArrayList<>();
        try {
            ParsingContext context = new ParsingContext();
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.FIELD_NAME) {
                    String fieldName = parser.getCurrentName();
                    switch (fieldName) {
                        case "factorType":
                            context.factorType = SensitivityFactorType.valueOf(parser.nextTextValue());
                            break;
                        case "functionType":
                            context.functionType = SensitivityFunctionType.valueOf(parser.nextTextValue());
                            break;
                        case "functionId":
                            context.functionId = parser.nextTextValue();
                            break;
                        case "variableType":
                            context.variableType = SensitivityVariableType.valueOf(parser.nextTextValue());
                            break;
                        case "variableId":
                            context.variableId = parser.nextTextValue();
                            break;
                        case "variables":
                            context.variables = WeightedSensitivityVariable.parseJson(parser);
                            break;
                        case "contingencyContextType":
                            context.contingencyContextType = ContingencyContextType.valueOf(parser.nextTextValue());
                            break;
                        case "contingencyId":
                            context.contingencyId = parser.nextTextValue();
                            break;
                        default:
                            break;
                    }
                } else if (token == JsonToken.END_OBJECT) {
                    switch (Objects.requireNonNull(context.factorType)) {
                        case SIMPLE:
                            factors.add(new SimpleSensitivityFactor(context.functionType, context.functionId, context.variableType, context.variableId,
                                    new ContingencyContext(context.contingencyContextType, context.contingencyId)));
                            break;
                        case MULTIPLE_VARIABLES:
                            factors.add(new MultipleVariablesSensitivityFactor(context.functionType, context.functionId, context.variableType, context.variableId,
                                    context.variables, new ContingencyContext(context.contingencyContextType, context.contingencyId)));
                            break;
                        default:
                            throw new IllegalStateException("Unexpected factor type: " + context.factorType);
                    }
                    context.reset();
                } else if (token == JsonToken.END_ARRAY) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        stopwatch.stop();
        LOGGER.info("{} factors read in {} ms", factors.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

        return factors;
    }
}
