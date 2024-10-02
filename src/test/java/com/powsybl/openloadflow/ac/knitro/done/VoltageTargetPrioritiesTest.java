/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.knitro.done;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.VoltageControl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
class VoltageTargetPrioritiesTest {

    @Test
    void voltageTargetPriorities() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Bus genBus = network.getBusBreakerView().getBus("NGEN");
        Bus loadBus = network.getBusBreakerView().getBus("NLOAD");

        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowParameters parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcSolverType(AcSolverType.KNITRO);

        network.getTwoWindingsTransformer("NHV2_NLOAD")
                .getRatioTapChanger()
                .setRegulationTerminal(network.getTwoWindingsTransformer("NGEN_NHV1").getTerminal1())
                .setTargetV(25.115);

        loadBus.getVoltageLevel().newShuntCompensator()
                .setId("SC")
                .setBus(loadBus.getId())
                .setConnectableBus(loadBus.getId())
                .setSectionCount(1)
                .newLinearModel()
                .setBPerSection(3.25 * Math.pow(10, -3))
                .setMaximumSectionCount(1)
                .add()
                .setVoltageRegulatorOn(true)
                .setRegulatingTerminal(network.getTwoWindingsTransformer("NGEN_NHV1").getTerminal1())
                .setTargetV(23.75)
                .setTargetDeadband(0)
                .add();

        parameters.setTransformerVoltageControlOn(true)
                .setShuntCompensatorVoltageControlOn(true);

        // Generator has target priority by default
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(24.5, genBus);

        // Transformer has target priority
        parametersExt.setVoltageTargetPriorities(List.of(VoltageControl.Type.TRANSFORMER.name(), VoltageControl.Type.GENERATOR.name(), VoltageControl.Type.SHUNT.name()));
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(25.115, genBus);

        // Shunt has target priority
        parametersExt.setVoltageTargetPriorities(List.of(VoltageControl.Type.SHUNT.name(), VoltageControl.Type.GENERATOR.name(), VoltageControl.Type.TRANSFORMER.name()));
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        assertVoltageEquals(23.75, genBus);
    }

}
