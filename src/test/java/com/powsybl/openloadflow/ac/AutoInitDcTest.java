/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.util.VoltageInitializerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AutoInitDcTest {

    @Test
    void test() {
        var network = PhaseShifterTestCaseFactory.create();
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters();
        DenseMatrixFactory matrixFactory = new DenseMatrixFactory();
        NaiveGraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new NaiveGraphConnectivityFactory<>(LfBus::getNum);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory);
        assertEquals(VoltageInitializerType.UNIFORM_VALUE, acParameters.getVoltageInitializer().getType());

        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().getCurrentStep().setAlpha(30);
        acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory);
        assertEquals(VoltageInitializerType.DC_VALUE, acParameters.getVoltageInitializer().getType());

        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
        acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory);
        assertEquals(VoltageInitializerType.PREVIOUS_VALUE, acParameters.getVoltageInitializer().getType());

        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.VOLTAGE_MAGNITUDE);
        acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory);
        assertEquals(VoltageInitializerType.FULL_VOLTAGE, acParameters.getVoltageInitializer().getType());

        parametersExt.setAutoDcInitPhaseShifterAngleThreshold(40);
        acParameters = OpenLoadFlowParameters.createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory);
        assertEquals(VoltageInitializerType.VOLTAGE_MAGNITUDE, acParameters.getVoltageInitializer().getType());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parametersExt.setAutoDcInitPhaseShifterAngleThreshold(-30));
        assertEquals("Invalid angle: -30.0", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> parametersExt.setAutoDcInitPhaseShifterAngleThreshold(380));
        assertEquals("Invalid angle: 380.0", e.getMessage());
    }
}
