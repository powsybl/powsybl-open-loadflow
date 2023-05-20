/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.extensions.SubstationAutomationSystemsAdder;
import org.junit.jupiter.api.Test;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class AutomationSystemTest {

    @Test
    void test() {
        Network network = IeeeCdfNetworkFactory.create14();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters)
                .setSimulateAutomatons(true);
        Substation s1 = network.getSubstation("S1");
        s1.newExtension(SubstationAutomationSystemsAdder.class)
                .newOverloadManagementSystem()
                    .withLineIdToMonitor("")
                    .withThreshold(100)
                    .withSwitchIdToOperate("")
                    .withSwitchOpen(true)
                .add()
            .add();
        loadFlowRunner.run(network, parameters);
    }
}
