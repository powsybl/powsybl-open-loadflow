/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import java.util.List;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SensitivityFactorReader {

    enum ContingencyContextType {
        ALL,
        NONE,
        SPECIFIC,
    }

    class ContingencyContext {
        private final String contingencyId;
        private final ContingencyContextType contextType;

        ContingencyContext(ContingencyContextType contingencyContextType, String contingencyId) {
            this.contextType = contingencyContextType;
            this.contingencyId = contingencyId;
        }

        String getContingencyId() {
            return contingencyId;
        }

        ContingencyContextType getContextType() {
            return contextType;
        }
    }

    interface Handler {

        void onSimpleFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, ContingencyContext contingencyContext);

        void onMultipleVariablesFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId, List<WeightedSensitivityVariable> variables, ContingencyContext contingencyContext);
    }

    void read(Handler handler);
}
