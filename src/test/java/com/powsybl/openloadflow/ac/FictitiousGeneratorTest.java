/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
class FictitiousGeneratorTest {

    private Network network;
    private LoadFlow.Runner loadFlowRunner;
    private Bus b1;
    private Bus b2;
    private VoltageLevel vl2;

    private LoadFlowParameters parameters;
    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    public void setUp() {
        network = Network.create("fictitiousGeneratorTest", "code");
        Substation s = network.newSubstation()
                .setId("s")
                .add();
        VoltageLevel vl1 = s.newVoltageLevel()
                .setId("vl1")
                .setNominalV(220)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();

        vl1.newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(200)
                .setTargetP(100)
                .setTargetV(221)
                .setVoltageRegulatorOn(true)
                .add();
        vl2 = s.newVoltageLevel()
                .setId("vl2")
                .setNominalV(220)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        b2 = vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();

        // A fictitious generator can generate power
        // but always controls voltage even when not started
        vl2.newGenerator()
                .setId("g2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(100)
                .setMaxP(200)
                .setTargetP(0)
                .setTargetV(224)
                .setVoltageRegulatorOn(true)
                .setFictitious(true)
                .add();

        vl2.newLoad()
                .setId("ld2")
                .setBus("b2")
                .setConnectableBus("b2")
                .setP0(99.9)
                .setQ0(80)
                .add();
        network.newLine()
                .setId("l1")
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .setR(1)
                .setX(1)
                .add();

        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters);
    }

    @Test
    void testFictitiousGeneratorWithDefaultParameters() {
        assertEquals(OpenLoadFlowParameters.FictitiousGeneratorVoltageControlCheckMode.FORCED, parametersExt.getFictitiousGeneratorVoltageControlCheckMode());

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertVoltageEquals(221, b1);
        assertVoltageEquals(224, b2);
    }

    @Test
    void testFictitiousGeneratorNormalMode() {
        parametersExt.setFictitiousGeneratorVoltageControlCheckMode(OpenLoadFlowParameters.FictitiousGeneratorVoltageControlCheckMode.NORMAL);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertVoltageEquals(221, b1);
        assertVoltageEquals(220.18, b2);  // No voltage control on bus - voltage decreases with active power flow
    }

    @Test
    void testCondenser() {
        // A condenser controls voltage but generates no power
        network.getGenerator("g2").remove();
        vl2.newGenerator()
                .setId("condenser")
                .setBus("b2")
                .setConnectableBus("b2")
                .setEnergySource(EnergySource.THERMAL)
                .setMinP(0)
                .setMaxP(0)
                .setTargetP(0)
                .setTargetV(224)
                .setVoltageRegulatorOn(true)
                .setCondenser(true)
                .add();

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertVoltageEquals(221, b1);
        assertVoltageEquals(224, b2);  // No voltage control on bus - voltage decreases with active power flow

        // This parameter has no effect on condensers
        parametersExt.setFictitiousGeneratorVoltageControlCheckMode(OpenLoadFlowParameters.FictitiousGeneratorVoltageControlCheckMode.NORMAL);

        result = loadFlowRunner.run(network, parameters);

        assertTrue(result.isFullyConverged());
        assertVoltageEquals(221, b1);
        assertVoltageEquals(224, b2);  // No voltage control on bus - voltage decreases with active power flow
    }
}
