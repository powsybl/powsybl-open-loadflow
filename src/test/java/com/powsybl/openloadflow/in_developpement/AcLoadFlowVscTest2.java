/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.in_developpement;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.Test;

//import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
//import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;

class AcLoadFlowVscTest2 {

    @Test
    void test() {
        System.out.println("#############################################################");
        Network network = HvdcNetworkFactory.createVsc();
        network.newLine() // in order to have only one synchronous component for the moment.
                .setId("l23")
                .setVoltageLevel1("vl2")
                .setBus1("b2")
                .setVoltageLevel2("vl3")
                .setBus2("b3")
                .setR(1)
                .setX(3)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        HvdcAngleDroopActivePowerControl hvdcAngleDroopActivePowerControl = network.getHvdcLine("hvdc23").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setDc(true);
        OpenLoadFlowParameters olfParams = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
    }
}
