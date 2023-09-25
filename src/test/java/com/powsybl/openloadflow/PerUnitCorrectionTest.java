/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class PerUnitCorrectionTest {

    private Network network = IeeeCdfNetworkFactory.create14();

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        OpenLoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    @ParameterizedTest(name = "{index} => perUnitCorrectionMode=''{0}''")
    @EnumSource(PerUnit.CorrectionMode.class)
    void testAc(PerUnit.CorrectionMode correctionMode) {
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setPerUnitCorrectionMode(correctionMode);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertVoltageEquals(143.1, network.getBusView().getBus("VL1_0"));
        assertVoltageEquals(141.075, network.getBusView().getBus("VL2_0"));
        assertVoltageEquals(136.35, network.getBusView().getBus("VL3_0"));
        assertVoltageEquals(137.38, network.getBusView().getBus("VL4_0"));
        assertVoltageEquals(137.63, network.getBusView().getBus("VL5_0"));
        assertVoltageEquals(12.84, network.getBusView().getBus("VL6_0"));
        assertVoltageEquals(14.86, network.getBusView().getBus("VL7_0"));
        assertVoltageEquals(21.8, network.getBusView().getBus("VL8_0"));
        assertVoltageEquals(12.67, network.getBusView().getBus("VL9_0"));
        assertVoltageEquals(12.61, network.getBusView().getBus("VL10_0"));
        assertVoltageEquals(12.68, network.getBusView().getBus("VL11_0"));
        assertVoltageEquals(12.66, network.getBusView().getBus("VL12_0"));
        assertVoltageEquals(12.60, network.getBusView().getBus("VL13_0"));
        assertVoltageEquals(12.42, network.getBusView().getBus("VL14_0"));
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        assertAngleEquals(-4.982589, network.getBusView().getBus("VL2_0"));
        assertAngleEquals(-12.725099, network.getBusView().getBus("VL3_0"));
        assertAngleEquals(-10.312901, network.getBusView().getBus("VL4_0"));
        assertAngleEquals(-8.773853, network.getBusView().getBus("VL5_0"));
        assertAngleEquals(-14.220946, network.getBusView().getBus("VL6_0"));
        assertAngleEquals(-13.359627, network.getBusView().getBus("VL7_0"));
        assertAngleEquals(-13.359627, network.getBusView().getBus("VL8_0"));
        assertAngleEquals(-14.938521, network.getBusView().getBus("VL9_0"));
        assertAngleEquals(-15.097288, network.getBusView().getBus("VL10_0"));
        assertAngleEquals(-14.790622, network.getBusView().getBus("VL11_0"));
        assertAngleEquals(-15.075584, network.getBusView().getBus("VL12_0"));
        assertAngleEquals(-15.156276, network.getBusView().getBus("VL13_0"));
        assertAngleEquals(-16.033644, network.getBusView().getBus("VL14_0"));
    }

    @ParameterizedTest(name = "{index} => piModelNominalVoltageCorrectionMode=''{0}''")
    @EnumSource(PerUnit.CorrectionMode.class)
    void testDc(PerUnit.CorrectionMode correctionMode) {
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setDc(true)
                .setDcUseTransformerRatio(true);
        OpenLoadFlowParameters.create(parameters)
                .setPerUnitCorrectionMode(correctionMode);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        assertAngleEquals(-5.012011, network.getBusView().getBus("VL2_0"));
        assertAngleEquals(-12.953663, network.getBusView().getBus("VL3_0"));
        assertAngleEquals(-10.583667, network.getBusView().getBus("VL4_0"));
        assertAngleEquals(-9.093894, network.getBusView().getBus("VL5_0"));
        assertAngleEquals(-14.852079, network.getBusView().getBus("VL6_0"));
        assertAngleEquals(-13.907054, network.getBusView().getBus("VL7_0"));
        assertAngleEquals(-13.907054, network.getBusView().getBus("VL8_0"));
        assertAngleEquals(-15.694688, network.getBusView().getBus("VL9_0"));
        assertAngleEquals(-15.974123, network.getBusView().getBus("VL10_0"));
        assertAngleEquals(-15.61885, network.getBusView().getBus("VL11_0"));
        assertAngleEquals(-15.967076, network.getBusView().getBus("VL12_0"));
        assertAngleEquals(-16.139703, network.getBusView().getBus("VL13_0"));
        assertAngleEquals(-17.188287, network.getBusView().getBus("VL14_0"));

        parameters.setDcUseTransformerRatio(false);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        assertAngleEquals(-5.013434, network.getBusView().getBus("VL2_0"));
        assertAngleEquals(-12.959124, network.getBusView().getBus("VL3_0"));
        assertAngleEquals(-10.592617, network.getBusView().getBus("VL4_0"));
        assertAngleEquals(-9.088529, network.getBusView().getBus("VL5_0"));
        assertAngleEquals(-15.165267, network.getBusView().getBus("VL6_0"));
        assertAngleEquals(-14.065521, network.getBusView().getBus("VL7_0"));
        assertAngleEquals(-14.065521, network.getBusView().getBus("VL8_0"));
        assertAngleEquals(-15.892482, network.getBusView().getBus("VL9_0"));
        assertAngleEquals(-16.192424, network.getBusView().getBus("VL10_0"));
        assertAngleEquals(-15.883766, network.getBusView().getBus("VL11_0"));
        assertAngleEquals(-16.271146, network.getBusView().getBus("VL12_0"));
        assertAngleEquals(-16.436648, network.getBusView().getBus("VL13_0"));
        assertAngleEquals(-17.429432, network.getBusView().getBus("VL14_0"));
    }
}
