/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.apiv2;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleSensitivityFactor {

    private final String functionId;

    private final FunctionType functionType;

    private final String variableId;

    private final VariableType variableType;

    public SimpleSensitivityFactor(String functionId, FunctionType functionType, String variableId, VariableType variableType) {
        this.functionId = Objects.requireNonNull(functionId);
        this.functionType = Objects.requireNonNull(functionType);
        this.variableId = Objects.requireNonNull(variableId);
        this.variableType = Objects.requireNonNull(variableType);
    }

    public static SimpleSensitivityFactor createBranchFlowWithRespectToInjectionFactor(String branchId, String injectionId) {
        return new SimpleSensitivityFactor(branchId, FunctionType.BRANCH_FLOW, injectionId, VariableType.INJECTION);
    }

    public static SimpleSensitivityFactor createBranchFlowWithRespectToTransformerPhaseShiftFactor(String branchId, String transformerId) {
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
}
