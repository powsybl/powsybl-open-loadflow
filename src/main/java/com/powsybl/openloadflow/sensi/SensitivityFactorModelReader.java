/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityFactorModelReader implements SensitivityFactorReader {

    private final List<SensitivityFactor2> factors;

    public SensitivityFactorModelReader(List<SensitivityFactor2> factors) {
        this.factors = Objects.requireNonNull(factors);
    }

    @Override
    public void read(Handler handler) {
        Objects.requireNonNull(handler);
        for (SensitivityFactor2 factor : factors) {
            switch (factor.getType()) {
                case SIMPLE:
                    SimpleSensitivityFactor simpleFactor = (SimpleSensitivityFactor) factor;
                    handler.onSimpleFactor(simpleFactor, simpleFactor.getFunctionType(), simpleFactor.getFunctionId(),
                            simpleFactor.getVariableType(), simpleFactor.getVariableId(), simpleFactor.getContingencyContext());
                    break;
                case MULTIPLE_VARIABLES:
                    MultipleVariablesSensitivityFactor multiVarFactor = (MultipleVariablesSensitivityFactor) factor;
                    handler.onMultipleVariablesFactor(multiVarFactor, multiVarFactor.getFunctionType(), multiVarFactor.getFunctionId(),
                            multiVarFactor.getVariableType(), multiVarFactor.getVariableId(), multiVarFactor.getVariables(),
                            multiVarFactor.getContingencyContext());
                    break;
                default:
                    throw new IllegalStateException("Factor type not supported: " + factor.getType());
            }
        }
    }
}
