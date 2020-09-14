/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class OperationalLimitsTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        parametersExt = new OpenLoadFlowParameters();
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void testPermanentCurrentLimits() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        LoadFlowParameters lfParameters = new LoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(lfParameters);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), lfParameters, lfParametersExt);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertTrue(branch1.getI1() < branch1.getPermanentLimit1());
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertTrue(branch2.getI1() < branch2.getPermanentLimit1());
        LfBranch branch3 = lfNetwork.getBranchById("NGEN_NHV1");
        assertTrue(Double.isNaN(branch3.getPermanentLimit1()));
        assertEquals(branch3.getI1(), 3654.181, 10E-3);
        LfBranch branch4 = lfNetwork.getBranchById("NHV2_NLOAD");
        assertTrue(Double.isNaN(branch4.getPermanentLimit1()));
        assertEquals(branch4.getI1(), 3716.345, 10E-3);
    }
}
