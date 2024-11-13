/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.ReferenceBusSelectionMode;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
class DcMultipleSlackBusesTest {

    private Network network;

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;
    Bus genBus;
    Load load;
    private Line line1;
    private Line line2;
    private TwoWindingsTransformer genT2wt;
    private TwoWindingsTransformer loadT2wt;

    @BeforeEach
    void setUp() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setDc(true)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setMaxSlackBusCount(2);

        genBus = network.getBusBreakerView().getBus("NGEN");
        load = network.getLoad("LOAD");
        line1 = network.getLine("NHV1_NHV2_1");
        line2 = network.getLine("NHV1_NHV2_2");
        genT2wt = network.getTwoWindingsTransformer("NGEN_NHV1");
        loadT2wt = network.getTwoWindingsTransformer("NHV2_NLOAD");
    }

    @Test
    void dcMultiSlackTest() {
        parametersExt.setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        List<LoadFlowResult.SlackBusResult> slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());
        assertEquals(List.of("VLHV1_0", "VLHV2_0"), slackBusResults.stream().map(LoadFlowResult.SlackBusResult::getId).toList());
        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

        assertAngleEquals(0.0, genBus);
        assertActivePowerEquals(301.75, line1.getTerminal1());
        assertActivePowerEquals(301.75, line2.getTerminal1());
        assertActivePowerEquals(600, loadT2wt.getTerminal1());
        assertActivePowerEquals(607, genT2wt.getTerminal1());

        parameters.setDistributedSlack(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        componentResult = result.getComponentResults().get(0);
        slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());
        assertEquals(0, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void dcNonImpedantBranchTest() {
        line1.setR(0).setX(0); // "shortcuts" the line2

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        List<LoadFlowResult.SlackBusResult> slackBusResults = componentResult.getSlackBusResults();
        assertEquals(List.of("VLHV1_0", "VLHV2_0"), slackBusResults.stream().map(LoadFlowResult.SlackBusResult::getId).toList());
        assertEquals(2, slackBusResults.size());
        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

        assertActivePowerEquals(603.5, line1.getTerminal1());
        assertActivePowerEquals(0.0, line2.getTerminal1());
        assertActivePowerEquals(600, loadT2wt.getTerminal1());
        assertActivePowerEquals(607, genT2wt.getTerminal1());

        parameters.setDistributedSlack(true);
        result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        componentResult = result.getComponentResults().get(0);
        slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());
        assertEquals(0, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void dcMultiSlackWithLoadOnSlackBus() {
        parametersExt.setSlackBusSelectionMode(SlackBusSelectionMode.NAME);
        parametersExt.setSlackBusesIds(List.of("VLHV2", "VLLOAD"));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        List<LoadFlowResult.SlackBusResult> slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());
        assertEquals(List.of("VLHV2_0", "VLLOAD_0"), slackBusResults.stream().map(LoadFlowResult.SlackBusResult::getId).toList());

        assertActivePowerEquals(-603.5, loadT2wt.getTerminal2());
        assertActivePowerEquals(600.0, load.getTerminal());

        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }
}
