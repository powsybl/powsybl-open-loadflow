/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NominalVoltageMappingTest {

    private Network network;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        network.getVoltageLevel("VL1").setNominalV(138);
        network.getVoltageLevel("VL2").setNominalV(131);
        network.getVoltageLevel("VL11").setNominalV(13);
        network.getVoltageLevel("VL8").setNominalV(25);

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void testMapping() {
        // 25Kv should not be mapped because for instance VL7 nominal voltage is more than 10% greater
        NominalVoltageMapping nominalVoltageMapping = NominalVoltageMapping.create(network, 0.1);
        assertEquals(Map.of(131d, 135d, 138d, 135d, 13d, 12d), nominalVoltageMapping.get());

        nominalVoltageMapping = NominalVoltageMapping.create(network, 0.05);
        assertEquals(Map.of(131d, 135d, 138d, 135d), nominalVoltageMapping.get());
    }

    @Test
    void testRun() {
        parametersExt.setNominalVoltagePerUnitResolution(0.1);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        Map<String, Double> voltages1 = network.getBusView().getBusStream().collect(Collectors.toMap(Identifiable::getId, Bus::getV));

        parametersExt.setNominalVoltagePerUnitResolution(0);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        Map<String, Double> voltages2 = network.getBusView().getBusStream().collect(Collectors.toMap(Identifiable::getId, Bus::getV));

        assertEquals(voltages1.size(), voltages2.size());
        for (var e : voltages2.entrySet()) {
            double nominalV1 = e.getValue();
            double nominalV2 = voltages1.get(e.getKey());
            assertEquals(nominalV1, nominalV2, 10e-11); // differences at 10e-12
        }
    }
}
