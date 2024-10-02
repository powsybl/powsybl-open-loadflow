/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro.done;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.VoltagePerReactivePowerControlAdder;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.AbstractLoadFlowNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *    g1     g2    g3
 *    |      |     |
 *    b1     b2    b3
 *    |      |     |
 *    -------b3-----
 *           |
 *           ld
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class SwitchPqPvTest extends AbstractLoadFlowNetworkFactory {

    private Network network;
    private Bus b1;
    private Bus b2;
    private Bus b3;
    private Bus b4;
    private Generator g1;
    private Generator g2;
    private Generator g3;
    private LoadFlow.Runner loadFlowRunner;
    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        network = Network.create("switch-pq-pv-test", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        VoltageLevel vl3 = s.newVoltageLevel()
                .setId("vl3")
                .setNominalV(20)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b3 = vl3.getBusBreakerView().newBus()
                .setId("b3")
                .add();
        VoltageLevel vl4 = s.newVoltageLevel()
                .setId("vl4")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b4 = vl4.getBusBreakerView().newBus()
                .setId("b4")
                .add();
        vl4.newLoad()
                .setId("ld")
                .setBus("b4")
                .setConnectableBus("b4")
                .setP0(300)
                .setQ0(200)
                .add();
        g1 = b1.getVoltageLevel()
                .newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(17)
                .setVoltageRegulatorOn(true)
                .add();
        g1.newMinMaxReactiveLimits()
                .setMinQ(-179)
                .setMaxQ(1000)
                .add();
        g2 = b2.getVoltageLevel()
                .newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(21)
                .setVoltageRegulatorOn(true)
                .add();
        g2.newMinMaxReactiveLimits()
                .setMinQ(-1000)
                .setMaxQ(411)
                .add();
        g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(20)
                .setVoltageRegulatorOn(true)
                .add();
        g3.newMinMaxReactiveLimits()
                .setMinQ(-1000)
                .setMaxQ(30)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr14")
                .setBus1(b1.getId())
                .setConnectableBus1(b1.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(399)
                .setR(1)
                .setX(100)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr24")
                .setBus1(b2.getId())
                .setConnectableBus1(b2.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(397)
                .setR(0.5)
                .setX(20)
                .add();
        s.newTwoWindingsTransformer()
                .setId("tr34")
                .setBus1(b3.getId())
                .setConnectableBus1(b3.getId())
                .setBus2(b4.getId())
                .setConnectableBus2(b4.getId())
                .setRatedU1(20.5)
                .setRatedU2(397)
                .setR(0.5)
                .setX(10)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED)
                .setAcSolverType(AcSolverType.KNITRO);
    }

    @Test
    void test() {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        // bus 1 and 3 switch PQ at first outer loop, then at next outer loop bus 3 go back PV
        assertVoltageEquals(17.032769, b1); // PQ => v != 17
        assertVoltageEquals(21, b2); // PV
        assertVoltageEquals(20, b3); // PV
    }

    @Test
    void testWithSlope() {
        g3.remove();
        double value = b3.getVoltageLevel().getNominalV() * b3.getVoltageLevel().getNominalV();
        StaticVarCompensator svc3 = b3.getVoltageLevel()
                .newStaticVarCompensator()
                .setId("svc3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setVoltageSetpoint(20)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setBmax(30 / value)
                .setBmin(-1000 / value)
                .add();
        g3 = b3.getVoltageLevel()
                .newGenerator()
                .setId("g3")
                .setBus("b3")
                .setConnectableBus("b3")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetQ(0)
                .setVoltageRegulatorOn(false)
                .add();
        g3.newMinMaxReactiveLimits()
                .setMinQ(0)
                .setMaxQ(0)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        // bus 1 and 3 switch PQ at first outer loop, then at next outer loop bus 3 go back PV
        assertVoltageEquals(17.032769, b1); // PQ => v != 17
        assertVoltageEquals(21, b2); // PV
        assertVoltageEquals(20, b3); // PV

        parametersExt.setVoltagePerReactivePowerControl(true);
        svc3.newExtension(VoltagePerReactivePowerControlAdder.class).withSlope(0.00001).add();
        LoadFlowResult result2 = loadFlowRunner.run(network, parameters);
        assertTrue(result2.isFullyConverged());
        // bus 1 and 3 switch PQ at first outer loop, then at next outer loop bus 3 does not go back PV
        assertVoltageEquals(17.034003, b1); // PQ => v != 17
        assertVoltageEquals(21, b2); // PV
        assertEquals(20.00140, b3.getV(), 10E-3); // remains PQ because of slope
    }
}
