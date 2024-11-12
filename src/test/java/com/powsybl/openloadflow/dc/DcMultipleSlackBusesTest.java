/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
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

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
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

    @BeforeEach
    void setUp() {
        network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        parameters = new LoadFlowParameters()
                .setDc(true)
                .setUseReactiveLimits(false)
                .setDistributedSlack(false);
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setMaxSlackBusCount(2);
    }

    @Test
    void dcMultiSlackTest() {
        parametersExt.setReferenceBusSelectionMode(ReferenceBusSelectionMode.GENERATOR_REFERENCE_PRIORITY);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        List<LoadFlowResult.SlackBusResult> slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());

        Bus nGen = network.getBusBreakerView().getBus("NGEN");
        Line l12A = network.getLine("NHV1_NHV2_1");
        Line l12B = network.getLine("NHV1_NHV2_2");
        TwoWindingsTransformer t2wtGen = network.getTwoWindingsTransformer("NGEN_NHV1");
        TwoWindingsTransformer t2wtLoad = network.getTwoWindingsTransformer("NHV2_NLOAD");

        assertAngleEquals(0.0, nGen);
        assertActivePowerEquals(301.75, l12A.getTerminal1());
        assertActivePowerEquals(301.75, l12B.getTerminal1());
        assertActivePowerEquals(600, t2wtLoad.getTerminal1());
        assertActivePowerEquals(607, t2wtGen.getTerminal1());

        assertEquals(List.of("VLHV1_0", "VLHV2_0"), result.getComponentResults().get(0).getSlackBusResults().stream().map(r -> r.getId()).toList());
        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

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
        network.getLine("NHV1_NHV2_1")
                .setR(0)
                .setX(0);
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
        LoadFlowResult.ComponentResult componentResult = result.getComponentResults().get(0);
        List<LoadFlowResult.SlackBusResult> slackBusResults = componentResult.getSlackBusResults();
        assertEquals(2, slackBusResults.size());
        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);

        Line l12A = network.getLine("NHV1_NHV2_1");
        Line l12B = network.getLine("NHV1_NHV2_2");
        TwoWindingsTransformer t2wtGen = network.getTwoWindingsTransformer("NGEN_NHV1");
        TwoWindingsTransformer t2wtLoad = network.getTwoWindingsTransformer("NHV2_NLOAD");

        assertActivePowerEquals(603.5, l12A.getTerminal1());
        assertActivePowerEquals(0.0, l12B.getTerminal1());
        assertActivePowerEquals(600, t2wtLoad.getTerminal1());
        assertActivePowerEquals(607, t2wtGen.getTerminal1());

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
        TwoWindingsTransformer t2wtLoad = network.getTwoWindingsTransformer("NHV2_NLOAD");
        Load load = network.getLoad("LOAD");

        assertActivePowerEquals(-603.5, t2wtLoad.getTerminal2());
        assertActivePowerEquals(600.0, load.getTerminal());

        assertEquals(-3.5, slackBusResults.get(0).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.5, slackBusResults.get(1).getActivePowerMismatch(), LoadFlowAssert.DELTA_POWER);
    }

}
