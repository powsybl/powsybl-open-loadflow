/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import com.fasterxml.jackson.core.JsonGenerator;
import com.powsybl.math.matrix.DenseMatrix;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MatrixSensitivityFactor {

    private final List<String> functionsIds;

    private final FunctionType functionType;

    private final List<String> variablesIds;

    private final VariableType variableType;

    private DenseMatrix values;

    private double[] functionsReferences;

    public MatrixSensitivityFactor(List<String> functionsIds, FunctionType functionType, List<String> variablesIds, VariableType variableType) {
        this.functionsIds = Objects.requireNonNull(functionsIds);
        if (functionsIds.isEmpty()) {
            throw new IllegalArgumentException("Function ID list is empty");
        }
        this.functionType = Objects.requireNonNull(functionType);
        this.variablesIds = Objects.requireNonNull(variablesIds);
        if (variablesIds.isEmpty()) {
            throw new IllegalArgumentException("Variable ID list is empty");
        }
        this.variableType = Objects.requireNonNull(variableType);
    }

    public static MatrixSensitivityFactor createBranchFlowWithRespectToInjection(List<String> branchsIds, List<String> injectionsIds) {
        return new MatrixSensitivityFactor(branchsIds, FunctionType.BRANCH_FLOW, injectionsIds, VariableType.INJECTION);
    }

    public static MatrixSensitivityFactor createBranchFlowWithRespectToTransformerPhaseShift(List<String> branchsIds, List<String> transformersIds) {
        return new MatrixSensitivityFactor(branchsIds, FunctionType.BRANCH_FLOW, transformersIds, VariableType.TRANSFORMER_PHASE_SHIFT);
    }

    public List<String> getFunctionsIds() {
        return functionsIds;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    public List<String> getVariablesIds() {
        return variablesIds;
    }

    public VariableType getVariableType() {
        return variableType;
    }

    public DenseMatrix getValues() {
        if (values == null) {
            values = new DenseMatrix(variablesIds.size(), functionsIds.size());
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
            jsonGenerator.writeStartArray();

            for (int i = 0; i < values.getRowCount(); i++) {
                jsonGenerator.writeStartArray();

                for (int j = 0; j < values.getColumnCount(); j++) {
                    jsonGenerator.writeNumber(values.get(i, j));
                }

                jsonGenerator.writeEndArray();
            }

            jsonGenerator.writeEndArray();
        }

        if (functionsReferences != null) {
            jsonGenerator.writeFieldName("functionsReferences");
            jsonGenerator.writeArray(functionsReferences, 0, functionsReferences.length);
        }

        jsonGenerator.writeEndObject();
    }
}
