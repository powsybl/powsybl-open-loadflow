/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.DanglingLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
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
    void testLineCurrentLimits() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertTrue(branch1.getI1() < branch1.getPermanentLimit1());
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertTrue(branch2.getI2() < branch2.getPermanentLimit2());
        LfBranch branch3 = lfNetwork.getBranchById("NGEN_NHV1");
        assertTrue(Double.isNaN(branch3.getPermanentLimit1()));
        assertEquals(3654.18, branch3.getI1(), 10E-3);
        LfBranch branch4 = lfNetwork.getBranchById("NHV2_NLOAD");
        assertTrue(Double.isNaN(branch4.getPermanentLimit2()));
        assertEquals(3711.395, branch4.getI2(), 10E-3);
    }

    @Test
    void testDanglingLineCurrentLimits() {
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch = lfNetwork.getBranchById("DL");
        assertEquals(361.588, branch.getI1(), 10E-3);
        assertEquals(100.0, branch.getPermanentLimit1(), 10E-3);
        assertTrue(Double.isNaN(branch.getI2()));
        assertTrue(Double.isNaN(branch.getPermanentLimit2()));
    }

    @Test
    void testLegCurrentLimits() {
        Network network = ThreeWindingsTransformerNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch1 = lfNetwork.getBranchById("3WT_leg_1");
        assertEquals(6000.771, branch1.getI1(), 10E-3);
        assertTrue(Double.isNaN(branch1.getI2()));
        assertTrue(Double.isNaN(branch1.getPermanentLimit1()));
        assertTrue(Double.isNaN(branch1.getPermanentLimit2()));
    }

    @Test
    void testLineActivePowerLimits() {
        //FIXME: to be completed with new operational limits design.
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertEquals(302.444, branch1.getP1() * PerUnit.SB, 10E-3);
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertEquals(-300.434, branch2.getP2() * PerUnit.SB, 10E-3);
    }

    @Test
    void testDanglingLineActivePowerLimits() {
        //FIXME: to be completed with new operational limits design.
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch = lfNetwork.getBranchById("DL");
        assertEquals(54.815, branch.getP1() * PerUnit.SB, 10E-3);
        assertTrue(Double.isNaN(branch.getP2()));
    }

    @Test
    void testLegActivePowerLimits() {
        //FIXME: to be completed with new operational limits design.
        Network network = ThreeWindingsTransformerNetworkFactory.create();
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false);
        AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters);
        engine.run();
        LfBranch branch1 = lfNetwork.getBranchById("3WT_leg_1");
        assertEquals(1049.827, branch1.getP1() * PerUnit.SB, 10E-3);
        assertTrue(Double.isNaN(branch1.getP2()));
    }
}
