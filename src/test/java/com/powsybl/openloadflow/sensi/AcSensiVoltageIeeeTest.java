/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AcSensiVoltageIeeeTest extends AbstractSensitivityAnalysisTest {

    private Network network;

    private SensitivityAnalysisParameters sensiParameters;

    @BeforeEach
    @Override
    public void setUp() throws IOException {
        super.setUp();
        network = IeeeCdfNetworkFactory.create14();
        sensiParameters = new SensitivityAnalysisParameters();
        runLf(network, sensiParameters.getLoadFlowParameters());
        for (var g : network.getGenerators()) {
            if (g.getId().equals("B1-G") || g.getId().equals("B3-G")) {
                g.setVoltageRegulatorOn(true);
            } else {
                g.setVoltageRegulatorOn(false);
                g.setTargetQ(-g.getTerminal().getQ());
            }
        }
    }

    @Test
    void testVarVFunTargetQ() {
        var sensiMatrix = new SensitivityMatrix(SensitivityFunctionType.BUS_VOLTAGE, network.getBusBreakerView().getBusStream().limit(5),
                                                SensitivityVariableType.INJECTION_REACTIVE_POWER, network.getGeneratorStream().limit(3));
        SensitivityAnalysisResult result = sensiRunner.run(network, sensiMatrix.toFactors(), Collections.emptyList(), Collections.emptyList(), sensiParameters);
        var m = sensiMatrix.toResultMatrix(result);
        @SuppressWarnings("SingleSpaceSeparator")
        var mRef = new DenseMatrix(5, 3, new double[] {
        //  B1   B2        B3   B4        B5
            0.0, 0.0,      0.0, 0.0,      0.0,      // B1-G
            0.0, 0.047924, 0.0, 0.026885, 0.027269, // B2-G
            0.0, 0.0,      0.0, 0.0,      0.0,      // B3-G
        }).transpose();
        assertMatricesEquals(mRef, m, 1e-6);
    }

    @Test
    void testVarVFunTargetV() {
        var sensiMatrix = new SensitivityMatrix(SensitivityFunctionType.BUS_VOLTAGE, network.getBusBreakerView().getBusStream().limit(5),
                                                SensitivityVariableType.BUS_TARGET_VOLTAGE, network.getGeneratorStream().limit(3));
        SensitivityAnalysisResult result = sensiRunner.run(network, sensiMatrix.toFactors(), Collections.emptyList(), Collections.emptyList(), sensiParameters);
        var m = sensiMatrix.toResultMatrix(result);
        @SuppressWarnings("SingleSpaceSeparator")
        var mRef = new DenseMatrix(5, 3, new double[] {
        //  B1, B2, B3, B4, B5
            1.0, 0.744498, 0.0, 0.606942, 0.692691, // B1-G
            0.0, 0.0,      0.0, 0.0,      0.0,      // B2-G => all zero because PV bus
            0.0, 0.295352, 1.0, 0.485870, 0.397779, // B3-G
        }).transpose();
        assertMatricesEquals(mRef, m, 1e-6);
    }

    @Test
    void testVarBusQFunBranchI() {
        var sensiMatrix = new SensitivityMatrix(SensitivityFunctionType.BRANCH_CURRENT_1, network.getLineStream().limit(5),
                                                SensitivityVariableType.INJECTION_REACTIVE_POWER, network.getLoadStream().limit(3));
        SensitivityAnalysisResult result = sensiRunner.run(network, sensiMatrix.toFactors(), Collections.emptyList(), Collections.emptyList(), sensiParameters);
        var m = sensiMatrix.toResultMatrix(result);
        @SuppressWarnings("SingleSpaceSeparator")
        var mRef = new DenseMatrix(5, 3, new double[] {
        //  L1-2-1  L1-5-1 L2-3-1 L2-4-1 L2-5-1
            0.344380, -0.039832, -0.114189, -0.056692, -0.024813, // B2-L
            0.0,      0.0,       0.0,       0.0,       0.0,       // B3-L => all zero because PV bus
            0.079204, 0.032299,  -0.177781, 0.019083,  -0.042493  // B4-L
        }).transpose();
        assertMatricesEquals(mRef, m, 1e-6);
    }

    @Test
    void testVarBusQFunBusQ() {
        var sensiMatrix = new SensitivityMatrix(SensitivityFunctionType.BUS_REACTIVE_POWER, network.getBusBreakerView().getBusStream().limit(5),
                                                SensitivityVariableType.INJECTION_REACTIVE_POWER, network.getLoadStream().limit(3));
        SensitivityAnalysisResult result = sensiRunner.run(network, sensiMatrix.toFactors(), Collections.emptyList(), Collections.emptyList(), sensiParameters);
        var m = sensiMatrix.toResultMatrix(result);
        @SuppressWarnings("SingleSpaceSeparator")
        var mRef = new DenseMatrix(5, 3, new double[] {
        //  B1, B2, B3, B4, B5
            0.715067, -1.0, 0.307969, 0.0,  0.0, // B2-L
            0.0,      0.0,  0.0,      0.0,  0.0, // B3-L => all zero because PV bus
            0.568577, 0.0,  0.493863, -1.0, 0.0  // B4-L
        }).transpose();
        assertMatricesEquals(mRef, m, 1e-6);
    }
}
