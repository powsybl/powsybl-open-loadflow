/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro.done;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.KnitroSolverFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class NonImpedantBranchWithBreakerIssueTest {

    @Test
    void busBreakerAndNonImpedantBranchIssue() {
        Network network = NodeBreakerNetworkFactory.create3barsAndJustOneVoltageLevel();
        network.getGenerator("G1").newMinMaxReactiveLimits().setMaxQ(100).setMinQ(-100).add();
        network.getGenerator("G2").newMinMaxReactiveLimits().setMaxQ(100).setMinQ(-100).add();
        LfNetworkParameters networkParameters = new LfNetworkParameters()
                .setBreakers(true);
        LfNetwork lfNetwork = Networks.load(network, networkParameters).get(0);
        AcLoadFlowParameters acLoadFlowParameters = new AcLoadFlowParameters()
                .setNetworkParameters(networkParameters)
                .setMatrixFactory(new DenseMatrixFactory());
        try (var context = new AcLoadFlowContext(lfNetwork, acLoadFlowParameters)) {
            new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
        }
        lfNetwork.updateState(new LfNetworkStateUpdateParameters(false, false, false, false, false, false, false, false, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, false, ReferenceBusSelectionMode.FIRST_SLACK));
        for (Bus bus : network.getBusView().getBuses()) {
            assertEquals(400, bus.getV(), 0);
            assertEquals(0, bus.getAngle(), 0);
        }
        assertEquals(-100, network.getGenerator("G1").getTerminal().getQ(), 0);
        assertEquals(-100, network.getGenerator("G2").getTerminal().getQ(), 0);
    }

    @Test
    void busBreakerAndNonImpedantBranchIssueRef() {
        Network network = NodeBreakerNetworkFactory.create3barsAndJustOneVoltageLevel();
        LfNetworkParameters networkParameters = new LfNetworkParameters();
        LfNetwork lfNetwork = Networks.load(network, networkParameters).get(0);
        AcLoadFlowParameters acLoadFlowParameters = new AcLoadFlowParameters()
                .setNetworkParameters(networkParameters)
                .setMatrixFactory(new DenseMatrixFactory());
        try (var context = new AcLoadFlowContext(lfNetwork, acLoadFlowParameters)) {
            new AcloadFlowEngine(context, new KnitroSolverFactory())
                    .run();
        }
        lfNetwork.updateState(new LfNetworkStateUpdateParameters(false, false, false, false, false, false, false, false, ReactivePowerDispatchMode.Q_EQUAL_PROPORTION, false, ReferenceBusSelectionMode.FIRST_SLACK));
        assertEquals(-100, network.getGenerator("G1").getTerminal().getQ(), 0);
        assertEquals(-100, network.getGenerator("G2").getTerminal().getQ(), 0);
    }
}
