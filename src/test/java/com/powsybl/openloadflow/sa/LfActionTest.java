/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.NaiveGraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.security.action.SwitchAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

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
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new NaiveGraphConnectivityFactory<>(LfBus::getNum), Reporter.NO_OP, true, false);
        List<LfNetwork> lfNetworks = Networks.load(network, acParameters.getNetworkParameters(), Set.of(network.getSwitch("C")), Collections.emptySet(), Reporter.NO_OP);
        LfAction lfAction = new LfAction(switchAction, lfNetworks.get(0));
        String loadId = "LOAD";
        Contingency contingency = new Contingency(loadId, new LoadContingency("LD"));
        PropagatedContingency propagatedContingency = PropagatedContingency.createList(network,
                Collections.singletonList(contingency), new HashSet<>(), false, false, false, true).get(0);
        Optional<LfContingency> lfContingency = propagatedContingency.toLfContingency(lfNetworks.get(0));
        if (lfContingency.isPresent()) {
            LfAction.apply(List.of(lfAction), lfNetworks.get(0), lfContingency.get());
            assertTrue(lfNetworks.get(0).getBranchById("C").isDisabled());
            assertEquals("C", lfAction.getDisabledBranch().getId());
            assertNull(lfAction.getEnabledBranch());
        }

        SwitchAction switchAction2 = new SwitchAction("switchAction", "S", true);
        assertThrows(PowsyblException.class, () -> new LfAction(switchAction2, lfNetworks.get(0)), "Branch S not found in the network");
    }
}
