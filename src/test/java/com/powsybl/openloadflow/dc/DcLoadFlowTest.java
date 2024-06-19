/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.openloadflow.util.PerUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.usefultoys.slf4j.LoggerFactory;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Sylvain Leclerc {@literal <sylvain.leclerc at rte-france.com>}
 */
class DcLoadFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowTest.class);

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    private OpenLoadFlowProvider loadFlowProvider;

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters()
                .setDc(true);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST);
        loadFlowProvider = new OpenLoadFlowProvider(new DenseMatrixFactory());
        loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);
    }

    /**
     * Check behaviour of the load flow for simple manipulations on eurostag example 1 network.
     * - line opening
     * - load change
     */
    @Test
    void tuto1Test() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Line line1 = network.getLine("NHV1_NHV2_1");
        Line line2 = network.getLine("NHV1_NHV2_2");

        assertTrue(Double.isNaN(line1.getTerminal1().getP()));
        assertTrue(Double.isNaN(line1.getTerminal2().getP()));
        assertTrue(Double.isNaN(line2.getTerminal1().getP()));
        assertTrue(Double.isNaN(line2.getTerminal2().getP()));

        loadFlowRunner.run(network, parameters);

        assertEquals(300, line1.getTerminal1().getP(), 0.01);
        assertEquals(-300, line1.getTerminal2().getP(), 0.01);
        assertEquals(300, line2.getTerminal1().getP(), 0.01);
        assertEquals(-300, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();

        loadFlowRunner.run(network, parameters);

        assertTrue(Double.isNaN(line1.getTerminal1().getP()));
        assertEquals(0, line1.getTerminal2().getP(), 0);
        assertEquals(600, line2.getTerminal1().getP(), 0.01);
        assertEquals(-600, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().connect();
        network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();

        loadFlowRunner.run(network, parameters);

        assertEquals(0, line1.getTerminal1().getP(), 0);
        assertTrue(Double.isNaN(line1.getTerminal2().getP()));
        assertEquals(600, line2.getTerminal1().getP(), 0.01);
        assertEquals(-600, line2.getTerminal2().getP(), 0.01);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        network.getLoad("LOAD").setP0(450);

        loadFlowRunner.run(network, parameters);

        assertTrue(Double.isNaN(line1.getTerminal1().getP()));
        assertTrue(Double.isNaN(line1.getTerminal2().getP()));
        assertEquals(450, line2.getTerminal1().getP(), 0.01);
        assertEquals(-450, line2.getTerminal2().getP(), 0.01);
    }

    @Test
    void fourBusesTest() {
        Network network = FourBusNetworkFactory.create();

        loadFlowRunner.run(network, parameters);

        Line l14 = network.getLine("l14");
        Line l12 = network.getLine("l12");
        Line l23 = network.getLine("l23");
        Line l34 = network.getLine("l34");
        Line l13 = network.getLine("l13");

        assertEquals(0.25, l14.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l14.getTerminal2().getP(), 0.01);
        assertEquals(0.25, l12.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l12.getTerminal2().getP(), 0.01);
        assertEquals(1.25, l23.getTerminal1().getP(), 0.01);
        assertEquals(-1.25, l23.getTerminal2().getP(), 0.01);
        assertEquals(-1.25, l34.getTerminal1().getP(), 0.01);
        assertEquals(1.25, l34.getTerminal2().getP(), 0.01);
        assertEquals(1.5, l13.getTerminal1().getP(), 0.01);
        assertEquals(-1.5, l13.getTerminal2().getP(), 0.01);
    }

    @Test
    void phaseShifterTest() {
        Network network = PhaseShifterTestCaseFactory.create();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);

        loadFlowRunner.run(network, parameters);

        assertEquals(50, l1.getTerminal1().getP(), 0.01);
        assertEquals(-50, l1.getTerminal2().getP(), 0.01);
        assertEquals(50, l2.getTerminal1().getP(), 0.01);
        assertEquals(-50, l2.getTerminal2().getP(), 0.01);
        assertEquals(50, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-50, ps1.getTerminal2().getP(), 0.01);

        ps1.getPhaseTapChanger().setTapPosition(2);

        loadFlowRunner.run(network, parameters);

        assertEquals(18.5, l1.getTerminal1().getP(), 0.01);
        assertEquals(-18.5, l1.getTerminal2().getP(), 0.01);
        assertEquals(81.5, l2.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, l2.getTerminal2().getP(), 0.01);
        assertEquals(81.5, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, ps1.getTerminal2().getP(), 0.01);

        // check we have same result if we consider phase shift as a variable with a fixed value
        loadFlowProvider.setForcePhaseControlOffAndAddAngle1Var(true);

        loadFlowRunner.run(network, parameters);

        assertEquals(18.5, l1.getTerminal1().getP(), 0.01);
        assertEquals(-18.5, l1.getTerminal2().getP(), 0.01);
        assertEquals(81.5, l2.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, l2.getTerminal2().getP(), 0.01);
        assertEquals(81.5, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, ps1.getTerminal2().getP(), 0.01);
    }

    @Test
    void nonImpedantBranchTest() {
        Network network = PhaseShifterTestCaseFactory.create();
        network.getLine("L2").setX(0).setR(0);
        parameters.getExtension(OpenLoadFlowParameters.class).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);
        loadFlowRunner.run(network, parameters);
        assertEquals(66.6666, network.getLine("L2").getTerminal1().getP(), 0.01);
        assertEquals(33.3333, network.getLine("L1").getTerminal1().getP(), 0.01);

        parameters.getExtension(OpenLoadFlowParameters.class).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE);
        loadFlowRunner.run(network, parameters);
        assertEquals(66.6666, network.getLine("L2").getTerminal1().getP(), 0.01);
        assertEquals(33.3333, network.getLine("L1").getTerminal1().getP(), 0.01);
    }

    @Test
    void multiCcTest() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getVoltageLevel("VL12").newGenerator()
                .setId("gvl12")
                .setBus("B12")
                .setConnectableBus("B12")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(1)
                .setTargetP(0)
                .setTargetQ(0)
                .setVoltageRegulatorOn(false)
                .add();
        for (Line l : List.of(network.getLine("L13-14-1"),
                              network.getLine("L6-13-1"),
                              network.getLine("L6-12-1"))) {
            l.getTerminal1().disconnect();
            l.getTerminal2().disconnect();
        }
        // bus 12 and 13 are out of main connected component
        parameters.setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.ALL);
        loadFlowRunner.run(network, parameters);

        // check angle is zero for the 2 slack buses
        LoadFlowAssert.assertAngleEquals(0, network.getBusView().getBus("VL1_0"));
        LoadFlowAssert.assertAngleEquals(0, network.getBusView().getBus("VL12_0"));
    }

    @Test
    void lineWithDifferentNominalVoltageTest() {

        parameters.setDcUseTransformerRatio(true);
        Network network = FourBusNetworkFactory.create();

        loadFlowRunner.run(network, parameters);

        Line l14 = network.getLine("l14");
        Line l12 = network.getLine("l12");
        Line l23 = network.getLine("l23");
        Line l34 = network.getLine("l34");
        Line l13 = network.getLine("l13");

        assertEquals(0.25, l14.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l14.getTerminal2().getP(), 0.01);
        assertEquals(0.25, l12.getTerminal1().getP(), 0.01);
        assertEquals(-0.25, l12.getTerminal2().getP(), 0.01);
        assertEquals(1.25, l23.getTerminal1().getP(), 0.01);
        assertEquals(-1.25, l23.getTerminal2().getP(), 0.01);
        assertEquals(-1.25, l34.getTerminal1().getP(), 0.01);
        assertEquals(1.25, l34.getTerminal2().getP(), 0.01);
        assertEquals(1.5, l13.getTerminal1().getP(), 0.01);
        assertEquals(-1.5, l13.getTerminal2().getP(), 0.01);

        network.getBusBreakerView().getBus("b1").getVoltageLevel().setNominalV(2d);
        loadFlowRunner.run(network, parameters);
        assertEquals(0d, l14.getTerminal1().getP(), 0.01);
        assertEquals(0d, l14.getTerminal2().getP(), 0.01);
        assertEquals(0d, l12.getTerminal1().getP(), 0.01);
        assertEquals(0d, l12.getTerminal2().getP(), 0.01);
        assertEquals(1d, l23.getTerminal1().getP(), 0.01);
        assertEquals(-1d, l23.getTerminal2().getP(), 0.01);
        assertEquals(-1d, l34.getTerminal1().getP(), 0.01);
        assertEquals(1d, l34.getTerminal2().getP(), 0.01);
        assertEquals(2d, l13.getTerminal1().getP(), 0.01);
        assertEquals(-2d, l13.getTerminal2().getP(), 0.01);
    }

    @Test
    void shuntCompensatorActivePowerZero() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        var sc = network.getVoltageLevel("VLLOAD").newShuntCompensator()
                .setId("SC")
                .setBus("NLOAD")
                .setSectionCount(1)
                .newLinearModel()
                    .setBPerSection(0.111)
                    .setMaximumSectionCount(1)
                .add()
                .add();
        loadFlowRunner.run(network, parameters);
        assertActivePowerEquals(0, sc.getTerminal());
    }

    @Test
    void testDisabledNonImpedantBranch() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        Switch c1 = network.getSwitch("C1");
        c1.setOpen(true);

        LoadFlowParameters parameters = new LoadFlowParameters()
                .setDc(true);
        loadFlowRunner.run(network, parameters);

        assertActivePowerEquals(400.0, network.getLine("L1").getTerminal1());
        assertActivePowerEquals(100.0, network.getLine("L2").getTerminal1());
        assertActivePowerEquals(100.0, network.getLine("L3").getTerminal1());

        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters()
                .setLoadFlowModel(LoadFlowModel.DC)
                .setBreakers(true);
        DcLoadFlowParameters dcLoadFlowParameters = new DcLoadFlowParameters()
                .setNetworkParameters(lfNetworkParameters)
                .setMatrixFactory(new DenseMatrixFactory())
                .setDistributedSlack(true)
                .setBalanceType(parameters.getBalanceType())
                .setSetVToNan(false)
                .setMaxOuterLoopIterations(1);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.getSwitchesToClose().add(c1);
        try (LfNetworkList lfNetworks = Networks.load(network, lfNetworkParameters, topoConfig, ReportNode.NO_OP)) {
            LfNetwork largestNetwork = lfNetworks.getLargest().orElseThrow();
            largestNetwork.getBranchById("C1").setDisabled(true);
            try (DcLoadFlowContext context = new DcLoadFlowContext(largestNetwork, dcLoadFlowParameters)) {
                new DcLoadFlowEngine(context).run();
            }
            // should be the same as with previous LF
            assertEquals(400.0, largestNetwork.getBranchById("L1").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
            assertEquals(100.0, largestNetwork.getBranchById("L2").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
            assertEquals(100.0, largestNetwork.getBranchById("L3").getP1().eval() * PerUnit.SB, LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void outerLoopPhaseShifterTest() {
        Network network = PhaseShifterTestCaseFactory.create();
        Line l1 = network.getLine("L1");
        Line l2 = network.getLine("L2");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        ps1.getPhaseTapChanger().getStep(0).setAlpha(-5);
        ps1.getPhaseTapChanger().getStep(2).setAlpha(5);
        ps1.getPhaseTapChanger().setTargetDeadband(10);
        ps1.getPhaseTapChanger().setRegulationMode(PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL);
        ps1.getPhaseTapChanger().setRegulating(true);

        parameters.setPhaseShifterRegulationOn(false);

        loadFlowRunner.run(network, parameters);

        assertEquals(50, l1.getTerminal1().getP(), 0.01);
        assertEquals(-50, l1.getTerminal2().getP(), 0.01);
        assertEquals(50, l2.getTerminal1().getP(), 0.01);
        assertEquals(-50, l2.getTerminal2().getP(), 0.01);
        assertEquals(50, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-50, ps1.getTerminal2().getP(), 0.01);

        parameters.setPhaseShifterRegulationOn(true);
        ps1.getPhaseTapChanger().setRegulationValue(-80);

        loadFlowRunner.run(network, parameters);

        assertEquals(2, ps1.getPhaseTapChanger().getTapPosition());
        assertEquals(18.5, l1.getTerminal1().getP(), 0.01);
        assertEquals(-18.5, l1.getTerminal2().getP(), 0.01);
        assertEquals(81.5, l2.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, l2.getTerminal2().getP(), 0.01);
        assertEquals(81.5, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-81.5, ps1.getTerminal2().getP(), 0.01);

        ps1.getPhaseTapChanger().setRegulationTerminal(ps1.getTerminal1());
        ps1.getPhaseTapChanger().setTapPosition(0);
        ps1.getPhaseTapChanger().setRegulationValue(50);

        loadFlowRunner.run(network, parameters);

        assertEquals(1, ps1.getPhaseTapChanger().getTapPosition());
        assertEquals(50, l1.getTerminal1().getP(), 0.01);
        assertEquals(-50, l1.getTerminal2().getP(), 0.01);
        assertEquals(50, l2.getTerminal1().getP(), 0.01);
        assertEquals(-50, l2.getTerminal2().getP(), 0.01);
        assertEquals(50, ps1.getTerminal1().getP(), 0.01);
        assertEquals(-50, ps1.getTerminal2().getP(), 0.01);
    }

    @Test
    void testDcApproxIgnoreG() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Line line1 = network.getLine("NHV1_NHV2_1");
        // to get asymmetric flows
        line1.setR(line1.getR() * 1.1);
        line1.setX(line1.getX() * 1.05);
        Line line2 = network.getLine("NHV1_NHV2_2");

        loadFlowRunner.run(network, parameters);

        assertEquals(292.682, line1.getTerminal1().getP(), 0.01);
        assertEquals(-292.682, line1.getTerminal2().getP(), 0.01);
        assertEquals(307.317, line2.getTerminal1().getP(), 0.01);
        assertEquals(-307.317, line2.getTerminal2().getP(), 0.01);

        parametersExt.setDcApproximationType(DcApproximationType.IGNORE_G);
        loadFlowRunner.run(network, parameters);

        assertEquals(292.563, line1.getTerminal1().getP(), 0.01);
        assertEquals(-292.563, line1.getTerminal2().getP(), 0.01);
        assertEquals(307.436, line2.getTerminal1().getP(), 0.01);
        assertEquals(-307.436, line2.getTerminal2().getP(), 0.01);
    }
}
