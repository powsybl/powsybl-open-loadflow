/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class MostMeshedSlackBusSelectorTest {

    @Test
    void test() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var provider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        var runner = new LoadFlow.Runner(provider);
        var parameters = new LoadFlowParameters();
        LoadFlowResult result = runner.run(network, parameters);
        assertEquals("VLHV1_0", result.getComponentResults().get(0).getSlackBusResults().get(0).getId());
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters)
                .setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(50);
        result = runner.run(network, parameters);
        assertEquals("VLLOAD_0", result.getComponentResults().get(0).getSlackBusResults().get(0).getId());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parametersExt.setMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(120));
        assertEquals("Invalid value for parameter mostMeshedSlackBusSelectorMaxNominalVoltagePercentile: 120.0", exception.getMessage());
    }

    @Test
    void testMultipleSlackBuses() {
        Network network = MetrixTutorialSixBusesFactory.create();
        network.getLine("NO_N_1")
                .setR(0.0)
                .setX(0.0);
        var provider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        var runner = new LoadFlow.Runner(provider);
        var parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE)
                .setMaxSlackBusCount(5);
        LoadFlowResult result = runner.run(network, parameters);

        //assertEquals(List.of("NE_poste_0", "NO_poste_0", "N_poste_0"), result.getComponentResults().get(0).getSlackBusResults().stream().map(r -> r.getId()).toList());
    }
}
