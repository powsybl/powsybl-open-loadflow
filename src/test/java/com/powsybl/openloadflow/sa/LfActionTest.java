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
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.security.action.SwitchAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Collections;
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
    void test() throws IOException {
        Network network = NodeBreakerNetworkFactory.create();
        SwitchAction switchAction = new SwitchAction("switchAction", "C", true);
        var matrixFactory = new DenseMatrixFactory();
        AcSecurityAnalysis securityAnalysis = new AcSecurityAnalysis(network, matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>(), Collections.emptyList(), Reporter.NO_OP);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network,
                new LoadFlowParameters(), new OpenLoadFlowParameters(), matrixFactory, new EvenShiloachGraphDecrementalConnectivityFactory<>(), Reporter.NO_OP, true, false);
        List<LfNetwork> lfNetworks = securityAnalysis.createNetworks(Collections.emptySet(), Set.of(network.getSwitch("C")), acParameters.getNetworkParameters(), Reporter.NO_OP);
        LfAction lfAction = new LfAction(switchAction, lfNetworks.get(0));
        assertFalse(lfNetworks.get(0).getBranchById("C").isDisabled());
        lfAction.apply(true, Collections.emptyList());
        assertTrue(lfNetworks.get(0).getBranchById("C").isDisabled());
        assertEquals("C", lfAction.getDisabledBranch().getId());
        assertNull(lfAction.getEnabledBranch());

        SwitchAction switchAction2 = new SwitchAction("switchAction", "S", true);
        assertThrows(PowsyblException.class, () -> new LfAction(switchAction2, lfNetworks.get(0)), "Branch S not found in the network");
    }
}
