/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import org.jgrapht.alg.util.Triple;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityAnalysisResult2 {

    private final List<SensitivityValue2> values;

    private Map<String, List<SensitivityValue2>> valuesByContingencyId = new HashMap<>();

    private Map<Triple<String, String, String>, SensitivityValue2> valuesByContingencyIdAndFunctionIdAndVariableId = new HashMap<>();

    public SensitivityAnalysisResult2(List<SensitivityValue2> values) {
        this.values = Objects.requireNonNull(values);
        for (SensitivityValue2 value : values) {
            SensitivityFactor2 factor = (SensitivityFactor2) value.getFactorContext();
            valuesByContingencyId.computeIfAbsent(value.getContingencyId(), k -> new ArrayList<>())
                    .add(value);
            valuesByContingencyIdAndFunctionIdAndVariableId.put(Triple.of(value.getContingencyId(), factor.getFunctionId(), factor.getVariableId()), value);
        }
    }

    public List<SensitivityValue2> getValues() {
        return values;
    }

    public List<SensitivityValue2> getValues(String contingencyId) {
        return valuesByContingencyId.getOrDefault(contingencyId, Collections.emptyList());
    }

    public SensitivityValue2 getValue(String contingencyId, String functionId, String variableId) {
        return valuesByContingencyIdAndFunctionIdAndVariableId.get(Triple.of(contingencyId, functionId, variableId));
    }
}
