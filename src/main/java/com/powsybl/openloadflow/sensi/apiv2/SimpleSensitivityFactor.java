/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleSensitivityFactor {

    private final String functionId;

    private final FunctionType functionType;

    private final String variableId;

    private final VariableType variableType;

    private double value = Double.NaN;

    private double functionReference = Double.NaN;

    public SimpleSensitivityFactor(String functionId, FunctionType functionType, String variableId, VariableType variableType) {
        this.functionId = Objects.requireNonNull(functionId);
        this.functionType = Objects.requireNonNull(functionType);
        this.variableId = Objects.requireNonNull(variableId);
        this.variableType = Objects.requireNonNull(variableType);
    }

    public static SimpleSensitivityFactor createBranchFlowWithRespectToInjection(String branchId, String injectionId) {
        return new SimpleSensitivityFactor(branchId, FunctionType.BRANCH_FLOW, injectionId, VariableType.INJECTION);
    }

    public static SimpleSensitivityFactor createBranchFlowWithRespectToTransformerPhaseShift(String branchId, String transformerId) {
        return new SimpleSensitivityFactor(branchId, FunctionType.BRANCH_FLOW, transformerId, VariableType.TRANSFORMER_PHASE_SHIFT);
    }

    public String getFunctionId() {
        return functionId;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public String getVariableId() {
        return variableId;
    }

    public VariableType getVariableType() {
        return variableType;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getFunctionReference() {
        return functionReference;
    }

    public void setFunctionReference(double functionReference) {
        this.functionReference = functionReference;
    }

    public void writeJson(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("functionId", functionId);
        jsonGenerator.writeStringField("functionType", functionType.name());
        jsonGenerator.writeStringField("variableId", variableId);
        jsonGenerator.writeStringField("variableType", variableType.name());
        if (!Double.isNaN(value)) {
            jsonGenerator.writeNumberField("value", value);
        }
        if (!Double.isNaN(functionReference)) {
            jsonGenerator.writeNumberField("functionReference", functionReference);
        }

        jsonGenerator.writeEndObject();
    }
}
