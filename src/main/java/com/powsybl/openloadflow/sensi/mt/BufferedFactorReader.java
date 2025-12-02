/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.sensi.mt;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.sensitivity.SensitivityFactorReader;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 * A class that reads factors once from the source and can then provide them to each thread
 */
public class BufferedFactorReader implements SensitivityFactorReader, SensitivityFactorReader.Handler {

    private final List<Entry> entries = new ArrayList<>();

    private record Entry(SensitivityFunctionType functionType,
                 String functionId,
                 SensitivityVariableType variableType,
                 String variableId,
                 boolean variableSet,
                 ContingencyContext contingencyContext) {
    }

    public BufferedFactorReader(SensitivityFactorReader source) {
        source.read(this);

    }

    @Override
    public void read(Handler handler) {
        entries.forEach(e -> handler.onFactor(e.functionType,
                e.functionId,
                e.variableType,
                e.variableId,
                e.variableSet,
                e.contingencyContext));
    }

    @Override
    public void onFactor(SensitivityFunctionType functionType, String functionId, SensitivityVariableType variableType,
                         String variableId, boolean variableSet, ContingencyContext contingencyContext) {
        entries.add(new Entry(functionType, functionId, variableType, variableId, variableSet, contingencyContext));
    }
}
