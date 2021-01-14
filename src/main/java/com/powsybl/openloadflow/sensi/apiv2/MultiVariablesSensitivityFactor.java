/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Sensitivity factors of a list of function. For each function we calculate the sensitivity as a weighted linear
 * sum of individual sensitivity of the function with respect to each variable.
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MultiVariablesSensitivityFactor {

    private final List<String> functionsIds;

    private final FunctionType functionType;

    private final List<WeightedVariable> variables;

    private final VariableType variableType;

    private double[] values;

    private double[] functionsReferences;

    public MultiVariablesSensitivityFactor(List<String> functionsIds, FunctionType functionType, List<WeightedVariable> variables, VariableType variableType) {
        this.functionsIds = Objects.requireNonNull(functionsIds);
        if (functionsIds.isEmpty()) {
            throw new IllegalArgumentException("Function ID list is empty");
        }
        this.functionType = Objects.requireNonNull(functionType);
        this.variables = Objects.requireNonNull(variables);
        this.variableType = Objects.requireNonNull(variableType);
    }

    public static MultiVariablesSensitivityFactor createBranchFlowWithRespectToWeightedInjections(List<String> branchsIds, List<WeightedVariable> injections) {
        return new MultiVariablesSensitivityFactor(branchsIds, FunctionType.BRANCH_FLOW, injections, VariableType.INJECTION);
    }

    public List<String> getFunctionsIds() {
        return functionsIds;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public List<WeightedVariable> getVariables() {
        return variables;
    }

    public VariableType getVariableType() {
        return variableType;
    }

    public double[] getValues() {
        if (values == null) {
            values = new double[functionsIds.size()];
        }
        return values;
    }

    public double[] getFunctionsReferences() {
        if (functionsReferences == null) {
            functionsReferences = new double[functionsIds.size()];
        }
        return functionsReferences;
    }

    public void writeJson(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeFieldName("functionsIds");
        jsonGenerator.writeStartArray();
        for (String functionId : functionsIds) {
            jsonGenerator.writeString(functionId);
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeStringField("functionType", functionType.name());

        jsonGenerator.writeFieldName("functionsIds");
        jsonGenerator.writeStartArray();
        for (String functionId : functionsIds) {
            jsonGenerator.writeString(functionId);
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeStringField("variableType", variableType.name());

        if (values != null) {
            jsonGenerator.writeFieldName("values");
            jsonGenerator.writeArray(values, 0, values.length);
        }

        if (functionsReferences != null) {
            jsonGenerator.writeFieldName("functionsReferences");
            jsonGenerator.writeArray(functionsReferences, 0, functionsReferences.length);
        }

        jsonGenerator.writeEndObject();
    }
}
