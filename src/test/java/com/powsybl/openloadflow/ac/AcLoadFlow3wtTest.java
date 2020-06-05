/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three windings transformer test case.
 *
 *  g1            ld2
 *  |             |
 *  b1-----OO-----b2
 *         O
 *         |
 *         b3
 *         |
 *         sc3
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlow3wtTest {

    private Network network;
    private Substation s;
    private Bus bus1;
    private Bus bus2;
    private Bus bus3;
    private ThreeWindingsTransformer twt;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private Network createNetwork() {
        Network network = Network.create("vsc", "test");

        s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setConnectableBus("b1")
                .setBus("b1")
                .setTargetP(161)
                .setTargetV(405)
                .setMinP(0)
                .setMaxP(500)
                .setVoltageRegulatorOn(true)
                .add();

        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(225)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld2")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(161)
                .setQ0(74)
                .add();

        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        bus3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        vl3.newShuntCompensator()
                .setId("sc3")
                .setConnectableBus("b3")
                .setBus("b3")
                .setbPerSection(-0.16)
                .setMaximumSectionCount(1)
                .setCurrentSectionCount(0)
                .add();

        twt = s.newThreeWindingsTransformer()
                .setId("3wt")
                .newLeg1()
                    .setVoltageLevel("vl1")
                    .setConnectableBus("b1")
                    .setBus("b1")
                    .setRatedU(380)
                    .setR(0.08)
                    .setX(47.3)
                    .setG(0)
                    .setB(0)
                .add()
                .newLeg2()
                    .setVoltageLevel("vl2")
                    .setConnectableBus("b2")
                    .setBus("b2")
                    .setRatedU(225)
                    .setR(0.4)
                    .setX(-7.7)
                .add()
                .newLeg3()
                    .setVoltageLevel("vl3")
                    .setConnectableBus("b3")
                    .setBus("b3")
                    .setRatedU(20)
                    .setR(4.98)
                    .setX(133.5)
                .add()
            .add();

        return network;
    }

    @BeforeEach
    public void setUp() {
        network = createNetwork();
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters();
        parameters.setNoGeneratorReactiveLimits(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector())
                .setDistributedSlack(false);
        this.parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
    }

    @Test
    public void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());

        assertVoltageEquals(405, bus1);
        LoadFlowAssert.assertAngleEquals(0, bus1);
        assertVoltageEquals(235.132, bus2);
        LoadFlowAssert.assertAngleEquals(-2.259241, bus2);
        assertVoltageEquals(20.834, bus3);
        LoadFlowAssert.assertAngleEquals(-2.721885, bus3);
        assertActivePowerEquals(161.095, twt.getLeg1().getTerminal());
        assertReactivePowerEquals(81.884, twt.getLeg1().getTerminal());
        assertActivePowerEquals(-161, twt.getLeg2().getTerminal());
        assertReactivePowerEquals(-74, twt.getLeg2().getTerminal());
        assertActivePowerEquals(0, twt.getLeg3().getTerminal());
        assertReactivePowerEquals(0, twt.getLeg3().getTerminal());
    }

    @Test
    public void testWithRatioTapChangers() {
        // create a ratio tap changer on leg 1 and check that voltages on leg 2 and 3 have changed compare to previous
        // test
        twt.getLeg1().newRatioTapChanger()
                .setLoadTapChangingCapabilities(false)
                .setTapPosition(0)
                .beginStep()
                    .setR(5)
                    .setX(10)
                    .setG(0)
                    .setB(0)
                    .setRho(0.9)
                .endStep()
            .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertVoltageEquals(405, bus1);
        assertVoltageEquals(209.886, bus2);
        assertVoltageEquals(18.582, bus3);
    }

    @Test
    public void testWithPhaseTapChangers() {
        // create a phase tap changer at leg 2 with a zero phase shifting
        PhaseTapChanger ptc = twt.getLeg2().newPhaseTapChanger()
                .setTapPosition(0)
                .beginStep()
                .setR(0)
                .setX(0)
                .setG(0)
                .setB(0)
                .setRho(1)
                .setAlpha(0)
                .endStep()
                .add();
        // create a transformer between bus 1 / bus2 in parallel of leg1 / leg2
        TwoWindingsTransformer twtParallel = s.newTwoWindingsTransformer()
                .setId("2wt")
                .setVoltageLevel1("vl1")
                .setBus1("b1")
                .setConnectableBus1("b1")
                .setVoltageLevel2("vl2")
                .setBus2("b2")
                .setConnectableBus2("b2")
                .setRatedU1(390)
                .setRatedU2(220)
                .setR(4)
                .setX(80)
                .setG(0)
                .setB(0)
                .add();
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(21.97, twtParallel.getTerminal1());
        assertActivePowerEquals(-139.088, twt.getLeg2().getTerminal());

        // set the phase shifting to 10 degree and check active flow change
        ptc.getStep(0).setAlpha(10);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isOk());
        assertActivePowerEquals(121.691, twtParallel.getTerminal1());
        assertActivePowerEquals(-40.456, twt.getLeg2().getTerminal());
    }
}
