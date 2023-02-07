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
        SimpleNominalVoltageMapping nominalVoltageMapping = SimpleNominalVoltageMapping.create(network, 0.1);
        assertEquals(Map.of(131d, 135d, 138d, 135d, 13d, 12d), nominalVoltageMapping.get());

        nominalVoltageMapping = SimpleNominalVoltageMapping.create(network, 0.05);
        assertEquals(Map.of(131d, 135d, 138d, 135d), nominalVoltageMapping.get());
    }

    private Map<String, Double> getBusVoltages() {
        return network.getBusView().getBusStream().collect(Collectors.toMap(Identifiable::getId, Bus::getV));
    }

    private Map<String, Double> getBusAngles() {
        return network.getBusView().getBusStream().collect(Collectors.toMap(Identifiable::getId, Bus::getAngle));
    }

    private static void compareValues(Map<String, Double> values1, Map<String, Double> values2) {
        assertEquals(values1.size(), values2.size());
        for (var e : values2.entrySet()) {
            double v1 = e.getValue();
            double v2 = values1.get(e.getKey());
            assertEquals(v1, v2, 10e-11); // differences at 10e-12
        }
    }

    @Test
    void testRunAc() {
        parametersExt.setNominalVoltagePerUnitResolution(0.1);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        Map<String, Double> voltages1 = getBusVoltages();

        parametersExt.setNominalVoltagePerUnitResolution(0);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertEquals(7, result.getComponentResults().get(0).getIterationCount());
        Map<String, Double> voltages2 = getBusVoltages();

        compareValues(voltages1, voltages2);
    }

    @Test
    void testRunDc() {
        parameters.setDc(true);
        parametersExt.setNominalVoltagePerUnitResolution(0.1);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Map<String, Double> angles1 = getBusAngles();

        parametersExt.setNominalVoltagePerUnitResolution(0);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        Map<String, Double> angles2 = getBusAngles();

        compareValues(angles1, angles2);
    }
}
