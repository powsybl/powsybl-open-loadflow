/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.IdBasedBusRef;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TopologyLevel;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorReader;
import com.powsybl.sensitivity.SensitivityFunctionType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class SensitivityFactoryJsonRecorder implements SensitivityFactorReader {

    private final SensitivityFactorReader delegate;

    private final Path jsonFile;

    private final Network network;

    public SensitivityFactoryJsonRecorder(SensitivityFactorReader delegate, Network network, Path jsonFile) {
        this.delegate = Objects.requireNonNull(delegate);
        this.jsonFile = Objects.requireNonNull(jsonFile);
        this.network = Objects.requireNonNull(network);
    }

    @Override
    public void read(Handler handler) {
        Objects.requireNonNull(handler);
        JsonUtil.writeJson(jsonFile, jsonGenerator -> {
            try {
                jsonGenerator.writeStartArray();

                delegate.read((functionType, functionId, variableType, variableId, variableSet, contingencyContext) -> {
                    SensitivityFactor.writeJson(jsonGenerator, functionType, functionId, variableType, variableId, variableSet, contingencyContext);
                    String finalFunctionId = functionId;
                    if (functionType == SensitivityFunctionType.BUS_VOLTAGE) {
                        Bus bus = new IdBasedBusRef(functionId).resolve(network, TopologyLevel.BUS_BRANCH)
                                .orElseThrow(() -> new PowsyblException("The bus ref for '" + functionId + "' cannot be resolved."));
                        finalFunctionId = bus.getId();
                    }
                    handler.onFactor(functionType, finalFunctionId, variableType, variableId, variableSet, contingencyContext);
                });

                jsonGenerator.writeEndArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
