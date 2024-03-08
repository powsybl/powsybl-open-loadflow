/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
class AcLoadFlowGeneratorTest {
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void testWithCondenser() {
        Network network = FourBusNetworkFactory.createWithCondenser();
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-2.584977, b4);
    }

    @Test
    void testGeneratorDiscardedFromVoltageControl() {
        Network network = FourBusNetworkFactory.createWith2GeneratorsAtBus1();
        Generator g1Bis = network.getGenerator("g1Bis").setTargetP(0.0).setMinP(1.0).setTargetQ(Double.NaN); // must be discarded from voltage control
        Bus b1 = network.getBusBreakerView().getBus("b1");
        Bus b4 = network.getBusBreakerView().getBus("b4");
        Generator g1 = network.getGenerator("g1");
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(1.0, b1);
        assertAngleEquals(0, b1);
        assertVoltageEquals(1.0, b4);
        assertAngleEquals(-1.452245, b4);
        assertReactivePowerEquals(0.0, g1Bis.getTerminal());
        assertReactivePowerEquals(-0.570, g1.getTerminal());
    }
}

