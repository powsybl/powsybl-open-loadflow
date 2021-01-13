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
public class MultiVariablesSensitivityFactor {

    private final String functionId;

    private final FunctionType functionType;

    private final List<WeightedVariable> variables;

    private final VariableType variableType;

    public MultiVariablesSensitivityFactor(String functionId, FunctionType functionType, List<WeightedVariable> variables, VariableType variableType) {
        this.functionId = Objects.requireNonNull(functionId);
        this.functionType = Objects.requireNonNull(functionType);
        this.variables = Objects.requireNonNull(variables);
        this.variableType = Objects.requireNonNull(variableType);
    }

    public static MultiVariablesSensitivityFactor createBranchFlowWithRespectToWeightedInjectionsFactor(String branchId, List<WeightedVariable> injections) {
        return new MultiVariablesSensitivityFactor(branchId, FunctionType.BRANCH_FLOW, injections, VariableType.INJECTION);
    }

    public String getFunctionId() {
        return functionId;
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
}
