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

    interface Handler {

        void onSimpleFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, String variableId);

        void onMultipleVariablesFactor(Object factorContext, SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType, List<WeightedSensitivityVariable> variables);
    }

    void read(Handler handler);
}
