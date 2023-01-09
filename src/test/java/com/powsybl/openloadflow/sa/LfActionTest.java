/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.test.AbstractConverterTest;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.LfAction;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.security.action.LineConnectionAction;
import com.powsybl.security.action.PhaseTapChangerTapPositionAction;
import com.powsybl.security.action.SwitchAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class LfActionTest extends AbstractConverterTest {

    @Override
    @BeforeEach
    public void setUp() throws IOException {
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws IOException {
        super.tearDown();
    }

    @Test
    void test() {
        Network network = NodeBreakerNetworkFactory.create();
        SwitchAction switchAction = new SwitchAction("switchAction", "C", true);
        var matrixFactory = new DenseMatrixFactory();
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), true, false);
        try (LfNetworkList lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow();
            LfAction lfAction = LfAction.create(switchAction, lfNetwork).orElseThrow();
            String loadId = "LOAD";
            Contingency contingency = new Contingency(loadId, new LoadContingency("LD"));
            PropagatedContingency propagatedContingency = PropagatedContingency.createList(network,
                    Collections.singletonList(contingency), new HashSet<>(), false, false, false, true).get(0);
            propagatedContingency.toLfContingency(lfNetwork).ifPresent(lfContingency -> {
                LfAction.apply(List.of(lfAction), lfNetwork, lfContingency);
                assertTrue(lfNetwork.getBranchById("C").isDisabled());
                assertEquals("C", lfAction.getDisabledBranch().getId());
                assertNull(lfAction.getEnabledBranch());
            });

            assertTrue(LfAction.create(new SwitchAction("switchAction", "S", true), lfNetwork).isEmpty());
            assertTrue(LfAction.create(new LineConnectionAction("A line action", "x", true), lfNetwork).isEmpty());
            assertTrue(LfAction.create(new PhaseTapChangerTapPositionAction("A phase tap change action", "y", false, 3), lfNetwork).isEmpty());
            var lineAction = new LineConnectionAction("A line action", "L1", true, false);
            assertEquals("Line connection action: only open line at both sides is supported yet.", assertThrows(UnsupportedOperationException.class, () -> LfAction.create(lineAction, lfNetwork)).getMessage());
        }
    }
}
