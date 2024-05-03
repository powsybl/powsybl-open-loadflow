/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class NotSameNumberVariableEquationIssueTest {

    private LoadFlow.Runner loadFlowRunner;

    @BeforeEach
    void setUp() {
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
    }

    /**
     *   (local regul)
     *       GEN
     *        |     L
     * NGEN xxxxx ----- xxxx NGEN2 (regul NLOAD)
     *        |
     *        8
     *        |
     *    xxxxxxxxx NHV1
     *    |       |
     *    |       |
     *    |       |
     *    xxxxxxxxx NHV2
     *        |
     *        8
     *        |
     *      xxxxx NLOAD
     */
    @Test
    void test() {
        var network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        VoltageLevel vlgen2 = network.getSubstation("P1").newVoltageLevel()
                .setId("VLGEN2")
                .setNominalV(24)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlgen2.getBusBreakerView().newBus()
                .setId("NGEN2")
                .add();
        vlgen2.newGenerator()
                .setId("GEN2")
                .setConnectableBus("NGEN2")
                .setBus("NGEN2")
                .setMinP(0)
                .setMaxP(100)
                .setTargetP(1)
                .setVoltageRegulatorOn(true)
                .setTargetV(148)
                .setRegulatingTerminal(network.getLoad("LOAD").getTerminal())
                .add();
        network.newLine()
                .setId("L")
                .setVoltageLevel1("VLGEN")
                .setVoltageLevel2("VLGEN2")
                .setConnectableBus1("NGEN")
                .setBus1("NGEN")
                .setConnectableBus2("NGEN2")
                .setBus2("NGEN2")
                .setR(0)
                .setX(0)
                .setG1(0)
                .setG2(0)
                .setB1(0)
                .setB2(0)
                .add();
        loadFlowRunner.run(network);
        assertVoltageEquals(24.554, network.getBusBreakerView().getBus("NGEN"));
        assertVoltageEquals(148.0, network.getBusBreakerView().getBus("NLOAD"));
    }
}
