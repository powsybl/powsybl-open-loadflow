/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.network.LinePerUnitMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class Ieee14Test {

    private Network network = IeeeCdfNetworkFactory.create14();

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        network = IeeeCdfNetworkFactory.create14();
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
        OpenLoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    @ParameterizedTest
    @EnumSource(LinePerUnitMode.class)
    void testAc(LinePerUnitMode linePerUnitMode) {
        parametersExt.setLinePerUnitMode(linePerUnitMode);
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

    @ParameterizedTest
    @EnumSource(LinePerUnitMode.class)
    void testDc(LinePerUnitMode linePerUnitMode) {
        parameters.setDc(true)
                .setDcUseTransformerRatio(true);
        parametersExt.setLinePerUnitMode(linePerUnitMode);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        assertAngleEquals(-5.202361, network.getBusView().getBus("VL2_0"));
        assertAngleEquals(-13.123227, network.getBusView().getBus("VL3_0"));
        assertAngleEquals(-10.735275, network.getBusView().getBus("VL4_0"));
        assertAngleEquals(-9.232584, network.getBusView().getBus("VL5_0"));
        assertAngleEquals(-14.994984, network.getBusView().getBus("VL6_0"));
        assertAngleEquals(-14.056344, network.getBusView().getBus("VL7_0"));
        assertAngleEquals(-14.056344, network.getBusView().getBus("VL8_0"));
        assertAngleEquals(-15.842732, network.getBusView().getBus("VL9_0"));
        assertAngleEquals(-16.121253, network.getBusView().getBus("VL10_0"));
        assertAngleEquals(-15.763905, network.getBusView().getBus("VL11_0"));
        assertAngleEquals(-16.110388, network.getBusView().getBus("VL12_0"));
        assertAngleEquals(-16.283332, network.getBusView().getBus("VL13_0"));
        assertAngleEquals(-17.334401, network.getBusView().getBus("VL14_0"));

        parameters.setDcUseTransformerRatio(false);
        result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        // this is expected to not have the same results with no transformer ratio in DC mode
        if (linePerUnitMode == LinePerUnitMode.IMPEDANCE) {
            assertAngleEquals(-5.203786, network.getBusView().getBus("VL2_0"));
            assertAngleEquals(-13.128696, network.getBusView().getBus("VL3_0"));
            assertAngleEquals(-10.744237, network.getBusView().getBus("VL4_0"));
            assertAngleEquals(-9.227213, network.getBusView().getBus("VL5_0"));
            assertAngleEquals(-15.308350, network.getBusView().getBus("VL6_0"));
            assertAngleEquals(-14.214821, network.getBusView().getBus("VL7_0"));
            assertAngleEquals(-14.214821, network.getBusView().getBus("VL8_0"));
            assertAngleEquals(-16.040562, network.getBusView().getBus("VL9_0"));
            assertAngleEquals(-16.339616, network.getBusView().getBus("VL10_0"));
            assertAngleEquals(-16.028939, network.getBusView().getBus("VL11_0"));
            assertAngleEquals(-16.414624, network.getBusView().getBus("VL12_0"));
            assertAngleEquals(-16.580434, network.getBusView().getBus("VL13_0"));
            assertAngleEquals(-17.575635, network.getBusView().getBus("VL14_0"));
        } else {
            assertAngleEquals(-5.202933, network.getBusView().getBus("VL2_0"));
            assertAngleEquals(-13.125422, network.getBusView().getBus("VL3_0"));
            assertAngleEquals(-10.738872, network.getBusView().getBus("VL4_0"));
            assertAngleEquals(-9.2304289, network.getBusView().getBus("VL5_0"));
            assertAngleEquals(-15.372452, network.getBusView().getBus("VL6_0"));
            assertAngleEquals(-14.106806, network.getBusView().getBus("VL7_0"));
            assertAngleEquals(-14.106806, network.getBusView().getBus("VL8_0"));
            assertAngleEquals(-16.173837, network.getBusView().getBus("VL9_0"));
            assertAngleEquals(-16.460598, network.getBusView().getBus("VL10_0"));
            assertAngleEquals(-16.121978, network.getBusView().getBus("VL11_0"));
            assertAngleEquals(-16.484192, network.getBusView().getBus("VL12_0"));
            assertAngleEquals(-16.654274, network.getBusView().getBus("VL13_0"));
            assertAngleEquals(-17.682923, network.getBusView().getBus("VL14_0"));
        }
    }

    @Test
    void testDcApproxIgnoreG() {
        parameters.setDc(true)
                .setDcUseTransformerRatio(true);
        parametersExt.setDcApproximationType(DcApproximationType.IGNORE_G);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());
        assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        assertAngleEquals(-5.699355, network.getBusView().getBus("VL2_0"));
        assertAngleEquals(-14.243298, network.getBusView().getBus("VL3_0"));
        assertAngleEquals(-11.655581, network.getBusView().getBus("VL4_0"));
        assertAngleEquals(-9.9945657, network.getBusView().getBus("VL5_0"));
        assertAngleEquals(-15.734873, network.getBusView().getBus("VL6_0"));
        assertAngleEquals(-14.988798, network.getBusView().getBus("VL7_0"));
        assertAngleEquals(-14.988798, network.getBusView().getBus("VL8_0"));
        assertAngleEquals(-16.781720, network.getBusView().getBus("VL9_0"));
        assertAngleEquals(-17.100048, network.getBusView().getBus("VL10_0"));
        assertAngleEquals(-16.678279, network.getBusView().getBus("VL11_0"));
        assertAngleEquals(-17.056112, network.getBusView().getBus("VL12_0"));
        assertAngleEquals(-17.367469, network.getBusView().getBus("VL13_0"));
        assertAngleEquals(-18.632961, network.getBusView().getBus("VL14_0"));
    }
}
