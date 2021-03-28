/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SensitivityValueWriterAdapter implements SensitivityValueWriter {

    private final List<SensitivityValue> sensitivityValues = new ArrayList<>();

    private final Map<String, List<SensitivityValue>> sensitivityValuesByContingency = new HashMap<>();

    public List<SensitivityValue> getSensitivityValues() {
        return sensitivityValues;
    }

    public Map<String, List<SensitivityValue>> getSensitivityValuesByContingency() {
        return sensitivityValuesByContingency;
    }

    @Override
    public void write(Object factorContext, String contingencyId, double value, double functionReference) {
        SensitivityFactor sensitivityFactor = (SensitivityFactor) factorContext;
        if (contingencyId == null) {
            sensitivityValues.add(new SensitivityValue(sensitivityFactor, value, functionReference, Double.NaN));
        } else {
            sensitivityValuesByContingency.computeIfAbsent(contingencyId, k -> new ArrayList<>())
                    .add(new SensitivityValue(sensitivityFactor, value, functionReference, Double.NaN));
        }
    }
}
