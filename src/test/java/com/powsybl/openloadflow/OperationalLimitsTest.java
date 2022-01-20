/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.DanglingLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
class OperationalLimitsTest extends AbstractLoadFlowNetworkFactory {

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    public static final double DELTA = 10E-3;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        parametersExt = new OpenLoadFlowParameters();
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    void testLineCurrentLimits() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedCurrentLimits());
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertTrue(branch1.getI1().eval() < getLimitValueFromAcceptableDuration(branch1, Integer.MAX_VALUE, Branch.Side.ONE, LimitType.CURRENT));
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertTrue(branch2.getI2().eval() < getLimitValueFromAcceptableDuration(branch2, Integer.MAX_VALUE, Branch.Side.TWO, LimitType.CURRENT));
        LfBranch branch3 = lfNetwork.getBranchById("NGEN_NHV1");
        assertEquals(Double.NaN, getLimitValueFromAcceptableDuration(branch3, Integer.MAX_VALUE, Branch.Side.ONE, LimitType.CURRENT));
        assertEquals(6.329, branch3.getI1().eval(), DELTA);
        LfBranch branch4 = lfNetwork.getBranchById("NHV2_NLOAD");
        assertEquals(Double.NaN, getLimitValueFromAcceptableDuration(branch4, Integer.MAX_VALUE, Branch.Side.TWO, LimitType.CURRENT));
        assertEquals(6.428324, branch4.getI2().eval(), DELTA);
        assertEquals(7.239972, getLimitValueFromAcceptableDuration(branch2, 1200, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertEquals(7.898151, getLimitValueFromAcceptableDuration(branch2, 60, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertEquals(9.872689, getLimitValueFromAcceptableDuration(branch1, 0, Branch.Side.TWO, LimitType.CURRENT), DELTA);
        assertEquals(7.239972, getLimitValueFromAcceptableDuration(branch1, 600, Branch.Side.TWO, LimitType.CURRENT), DELTA);
        assertEquals(7.898151, getLimitValueFromAcceptableDuration(branch1, 60, Branch.Side.TWO, LimitType.CURRENT), DELTA);
        assertEquals(9.872689, getLimitValueFromAcceptableDuration(branch1, 0, Branch.Side.TWO, LimitType.CURRENT), DELTA);
    }

    private double getLimitValueFromAcceptableDuration(LfBranch branch, int acceptableDuration, Branch.Side side, LimitType type) {
        return (side == Branch.Side.ONE ? branch.getLimits1(type) : branch.getLimits2(type)).stream()
                                                                                             .filter(l -> l.getAcceptableDuration() == acceptableDuration)
                                                                                             .map(LfBranch.LfLimit::getValue)
                                                                                             .findFirst().orElse(Double.NaN);
    }

    @Test
    void testDanglingLineCurrentLimits() {
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch = lfNetwork.getBranchById("DL");
        assertEquals(0.626, branch.getI1().eval(), DELTA);
        assertTrue(Double.isNaN(branch.getI2().eval()));
        assertEquals(Double.NaN, getLimitValueFromAcceptableDuration(branch, Integer.MAX_VALUE, Branch.Side.TWO, LimitType.CURRENT), DELTA);
        assertEquals(0.173205, getLimitValueFromAcceptableDuration(branch, 1200, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertEquals(0.207846, getLimitValueFromAcceptableDuration(branch, 600, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertEquals(0.242487, getLimitValueFromAcceptableDuration(branch, 0, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertTrue(branch.getLimits2(LimitType.CURRENT).isEmpty());
    }

    @Test
    void testLegCurrentLimits() {
        Network network = ThreeWindingsTransformerNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new NameSlackBusSelector("VL_33_0"));
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch1 = lfNetwork.getBranchById("3WT_leg_1");
        assertEquals(1.144, branch1.getI1().eval(), DELTA);
        assertTrue(Double.isNaN(branch1.getI2().eval()));
        assertEquals(Double.NaN, getLimitValueFromAcceptableDuration(branch1, Integer.MAX_VALUE, Branch.Side.ONE, LimitType.CURRENT), DELTA);
        assertEquals(Double.NaN, getLimitValueFromAcceptableDuration(branch1, Integer.MAX_VALUE, Branch.Side.TWO, LimitType.CURRENT), DELTA);
        assertTrue(branch1.getLimits1(LimitType.CURRENT).isEmpty());
        assertTrue(branch1.getLimits2(LimitType.CURRENT).isEmpty());
    }

    @Test
    void testLineActivePowerLimits() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedLimits());
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertEquals(302.444, branch1.getP1().eval() * PerUnit.SB, 10E-3);
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertEquals(-300.434, branch2.getP2().eval() * PerUnit.SB, 10E-3);
        assertEquals(11, getLimitValueFromAcceptableDuration(branch1, 600, Branch.Side.TWO, LimitType.ACTIVE_POWER), DELTA);
        assertEquals(12, getLimitValueFromAcceptableDuration(branch1, 60, Branch.Side.TWO, LimitType.ACTIVE_POWER), DELTA);
        assertEquals(15, getLimitValueFromAcceptableDuration(branch1, 0, Branch.Side.TWO, LimitType.ACTIVE_POWER), DELTA);
    }

    @Test
    void testLineApparentPowerLimits() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.createWithFixedLimits());
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch1 = lfNetwork.getBranchById("NHV1_NHV2_1");
        assertEquals(302.444, branch1.getP1().eval() * PerUnit.SB, 10E-3);
        LfBranch branch2 = lfNetwork.getBranchById("NHV1_NHV2_2");
        assertEquals(-300.434, branch2.getP2().eval() * PerUnit.SB, 10E-3);
        assertEquals(11, getLimitValueFromAcceptableDuration(branch1, 600, Branch.Side.TWO, LimitType.APPARENT_POWER), DELTA);
        assertEquals(12, getLimitValueFromAcceptableDuration(branch1, 60, Branch.Side.TWO, LimitType.APPARENT_POWER), DELTA);
        assertEquals(15, getLimitValueFromAcceptableDuration(branch1, 0, Branch.Side.TWO, LimitType.APPARENT_POWER), DELTA);
    }

    @Test
    void testDanglingLineActivePowerLimits() {
        //FIXME: to be completed with new operational limits design.
        Network network = DanglingLineNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new MostMeshedSlackBusSelector());
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch = lfNetwork.getBranchById("DL");
        assertEquals(54.815, branch.getP1().eval() * PerUnit.SB, 10E-3);
        assertTrue(Double.isNaN(branch.getP2().eval()));
    }

    @Test
    void testLegActivePowerLimits() {
        //FIXME: to be completed with new operational limits design.
        Network network = ThreeWindingsTransformerNetworkFactory.create();
        List<LfNetwork> lfNetworks = Networks.load(network, new NameSlackBusSelector("VL_33_0"));
        assertEquals(1, lfNetworks.size());
        LfNetwork lfNetwork = lfNetworks.get(0);
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, new DenseMatrixFactory(), parameters, parametersExt, false, Reporter.NO_OP);
        try (var context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            new AcloadFlowEngine(context)
                    .run();
        }
        LfBranch branch1 = lfNetwork.getBranchById("3WT_leg_1");
        assertEquals(116.251, branch1.getP1().eval() * PerUnit.SB, DELTA);
        assertTrue(Double.isNaN(branch1.getP2().eval()));
    }
}
