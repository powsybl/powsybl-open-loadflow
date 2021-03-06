/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.util.ParameterConstants;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NonImpedantBranchWithBreakerIssueTest {

    @Test
    void busBreakerAndNonImpedantBranchIssue() {
        Network network = NodeBreakerNetworkFactory.create3barsAndJustOneVoltageLevel();
        network.getGenerator("G1").newMinMaxReactiveLimits().setMaxQ(100).setMinQ(-100).add();
        network.getGenerator("G2").newMinMaxReactiveLimits().setMaxQ(100).setMinQ(-100).add();
        FirstSlackBusSelector slackBusSelector = new FirstSlackBusSelector();
        boolean breakers = true;
        LfNetworkParameters networkParameters = new LfNetworkParameters(slackBusSelector, false, false, false, breakers,
                                                                        ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false,
                                                                        true, Collections.emptySet(), false, false, false, false);
        LfNetwork lfNetwork = LfNetwork.load(network, networkParameters).get(0);
        AcLoadFlowParameters acLoadFlowParameters = new AcLoadFlowParameters(slackBusSelector, new UniformValueVoltageInitializer(), new DefaultNewtonRaphsonStoppingCriteria(),
                                                                             Collections.emptyList(), new DenseMatrixFactory(), false, false, false, false, false, breakers, ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE,
                                                                             false, true, Collections.emptySet(), true, Collections.emptySet(), false, false);
        new AcloadFlowEngine(lfNetwork, acLoadFlowParameters)
                .run();
        lfNetwork.updateState(false, false, false, false, false, false);
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
        FirstSlackBusSelector slackBusSelector = new FirstSlackBusSelector();
        boolean breakers = false;
        LfNetworkParameters networkParameters = new LfNetworkParameters(slackBusSelector, false, false, false, breakers,
                ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false,
                true, Collections.emptySet(), false, false, false, false);
        LfNetwork lfNetwork = LfNetwork.load(network, networkParameters).get(0);
        AcLoadFlowParameters acLoadFlowParameters = new AcLoadFlowParameters(slackBusSelector, new UniformValueVoltageInitializer(), new DefaultNewtonRaphsonStoppingCriteria(),
                Collections.emptyList(), new DenseMatrixFactory(), false, false, false, false, false, breakers, ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE,
                false, true, Collections.emptySet(), true, Collections.emptySet(), false, false);
        new AcloadFlowEngine(lfNetwork, acLoadFlowParameters)
                .run();
        lfNetwork.updateState(false, false, false, false, false, false);
        assertEquals(-100, network.getGenerator("G1").getTerminal().getQ(), 0);
        assertEquals(-100, network.getGenerator("G2").getTerminal().getQ(), 0);
    }
}
