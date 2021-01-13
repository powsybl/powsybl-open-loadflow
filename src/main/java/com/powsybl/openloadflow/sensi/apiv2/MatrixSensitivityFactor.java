/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class MatrixSensitivityFactor {

    private final String id;

    private final List<String> functionsIds;

    private final FunctionType functionType;

    private final List<String> variablesIds;

    private final VariableType variableType;

    public MatrixSensitivityFactor(String id, List<String> functionsIds, FunctionType functionType, List<String> variablesIds, VariableType variableType) {
        this.id = Objects.requireNonNull(id);
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

    public String getId() {
        return id;
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
}
