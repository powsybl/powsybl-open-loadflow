/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.action.*;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.LoadContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.network.PhaseControlFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.strategy.OperatorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Pierre Arvy {@literal <pierre.arvy at artelys.com>}
 */
class WoodburyDcSecurityAnalysisWithActionsTest extends AbstractOpenSecurityAnalysisTest {

    private LoadFlowParameters parameters;
    private SecurityAnalysisParameters securityAnalysisParameters;

    @BeforeEach
    public void setUpWoodburyDcSa() {
        securityAnalysisParameters = new SecurityAnalysisParameters();
        // configure sa to use Woodbury dc sa
        parameters = new LoadFlowParameters();
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFastSaDcPhaseTapChangerTapPositionChangeAdmittanceOnlyAlphaNull(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        // no alpha modification with pst actions
        network.getTwoWindingsTransformer("PS1")
                .getPhaseTapChanger()
                .getStep(0)
                .setAlpha(0);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pstRelChange", "PS1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstRelChange")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRelL2 = resultRel.getNetworkResult().getBranchResult("L2");
        BranchResult brRelPS1 = resultRel.getNetworkResult().getBranchResult("PS1");

        // Apply contingency by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal1().getP(), brRelL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brRelL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brRelPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brRelPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFastSaDcPhaseTapChangerTapPositionChangeAdmittanceOnlyAlphaNonNull(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        // no alpha modification with pst actions
        network.getTwoWindingsTransformer("PS1")
                .getPhaseTapChanger()
                .getStep(1)
                .setAlpha(-5);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pstRelChange", "PS1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstRelChange")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRelL2 = resultRel.getNetworkResult().getBranchResult("L2");
        BranchResult brRelPS1 = resultRel.getNetworkResult().getBranchResult("PS1");

        // Apply contingency by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal1().getP(), brRelL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brRelL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brRelPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brRelPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSaDcPhaseTapChangerTapPositionChangeAlphaOnly(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        network.getTwoWindingsTransformer("PS1")
                .getPhaseTapChanger()
                .getStep(0)
                .setX(100);

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pstRelChange", "PS1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstRelChange")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRelL2 = resultRel.getNetworkResult().getBranchResult("L2");
        BranchResult brRelPS1 = resultRel.getNetworkResult().getBranchResult("PS1");

        // Apply contingency by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal1().getP(), brRelL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brRelL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brRelPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brRelPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    // TODO Does not work in DC mode - to fix in a separate PR
    @Test
    void testSaDcPhaseTapChangerTapPositionChangeWithConnectivityBreak() {
        Network network = PhaseControlFactory.createNetworkWith3Buses();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L23", new BranchContingency("L23")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pstRelChange", "PS1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L23"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("L23"), new TrueCondition(), List.of("pstRelChange")));

        OpenLoadFlowParameters.create(parameters)
                .setSlackBusId("VL2_0")
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME);
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");
        BranchResult brAbsL12 = resultAbs.getNetworkResult().getBranchResult("L12");

        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRelPS1 = resultRel.getNetworkResult().getBranchResult("PS1");
        BranchResult brRelL12 = resultAbs.getNetworkResult().getBranchResult("L12");

        // Apply contingency by hand
        network.getLine("L23").getTerminal1().disconnect();
        network.getLine("L23").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L12
        assertEquals(network.getLine("L12").getTerminal1().getP(), brAbsL12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L12").getTerminal2().getP(), brAbsL12.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L12").getTerminal1().getP(), brRelL12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L12").getTerminal2().getP(), brRelL12.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brRelPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brRelPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSaDcPhaseTapChangerTapPositionChange(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        // Apply contingency by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFastSaDcOneContingencyTwoTapPositionChange(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithTwoT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pst2Change", "PS2", false, 2));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pst1Change", "pst2Change")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapChange");
        BranchResult brL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");
        BranchResult brPS2 = resultAbs.getNetworkResult().getBranchResult("PS2");

        // Apply contingency by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        // Apply remedial actions
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);
        network.getTwoWindingsTransformer("PS2").getPhaseTapChanger().setTapPosition(2);

        loadFlowRunner.run(network, parameters);

        // Compare results on remaining branches
        assertEquals(network.getLine("L2").getTerminal1().getP(), brL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brL2.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS2").getTerminal1().getP(), brPS2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS2").getTerminal2().getP(), brPS2.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFastSaDcTwoContingenciesOneTapPositionChange(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithTwoT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1+PS2", List.of(new BranchContingency("L1"), new BranchContingency("PS2"))));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1+PS2"), new TrueCondition(), List.of("pst1Change")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapChange");
        BranchResult brL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        // Apply contingencies by hand
        network.getLine("L1").getTerminal1().disconnect();
        network.getLine("L1").getTerminal2().disconnect();
        network.getTwoWindingsTransformer("PS2").getTerminal1().disconnect();
        network.getTwoWindingsTransformer("PS2").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on remaining branches
        assertEquals(network.getLine("L2").getTerminal1().getP(), brL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brL2.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFastSaDcN2ContingencyOneTapPositionChange(boolean dcFastMode) {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        // add load which will be lost by contingency
        network.getVoltageLevel("VL2").newLoad()
                .setId("LD3")
                .setConnectableBus("B2")
                .setBus("B2")
                .setP0(50.0)
                .setQ0(25.0)
                .add();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1+LD3", List.of(new BranchContingency("L1"), new LoadContingency("LD3"))));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstChange", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1+LD3"), new TrueCondition(), List.of("pstChange")));

        securityAnalysisParameters.getExtension(OpenSecurityAnalysisParameters.class)
                .setDcFastMode(dcFastMode);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapChange");
        BranchResult brAbsL2 = resultAbs.getNetworkResult().getBranchResult("L2");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        // Apply contingencies by hand
        network.getLine("L1").disconnect();
        network.getLoad("LD3").disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastDcSaWithActionNotOnPst() {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("contingencyLD2", List.of(new LoadContingency("LD2"))));
        List<Action> actions = List.of(new GeneratorActionBuilder().withId("genActionG1").withGeneratorId("G1").withActivePowerRelativeValue(true).withActivePowerValue(1).build());
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("contingencyLD2"), new TrueCondition(), List.of("genActionG1")));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters, operatorStrategies, actions, ReportNode.NO_OP));
        assertTrue(thrown.getCause().getMessage().contains("For now, only PhaseTapChangerTapPositionAction, TerminalsConnectionAction and SwitchAction are allowed in WoodburyDcSecurityAnalysis"));
    }

    @Test
    void testFastSaDcLineDisconnectionAction() {
        Network network = FourBusNetworkFactory.create();
        List<Contingency> contingencies = Stream.of("l14")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        List<Action> actions = List.of(new TerminalsConnectionAction("openLine", "l13", true));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyL1", ContingencyContext.specificContingency("l14"), new TrueCondition(), List.of("openLine")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        OperatorStrategyResult resultStratL1 = getOperatorStrategyResult(result, "strategyL1");
        BranchResult brl12 = resultStratL1.getNetworkResult().getBranchResult("l12");
        BranchResult brl23 = resultStratL1.getNetworkResult().getBranchResult("l23");
        BranchResult brl34 = resultStratL1.getNetworkResult().getBranchResult("l34");

        assertEquals(2.0, brl12.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(3.0, brl23.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0, brl34.getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastSaDcTransformerDisconnectionAction() {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new TerminalsConnectionAction("openPS1", "PS1", true));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyOpenPS1", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("openPS1")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        // Verify pst disconnection is well handled in Woodbury computation, when alpha of opened pst is null
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        assertEquals(100.0, getOperatorStrategyResult(result, "strategyOpenPS1").getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);

        // Same when alpha of opened pst is not null
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);
        result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        assertEquals(100.0, getOperatorStrategyResult(result, "strategyOpenPS1").getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    // TODO : this test breaks connectivity and should not work ! So, it seems to work as expected
    @Test
    void testFastSaDcTransformerDisconnectionActionBreakingConnectivity() {
        Network network = PhaseControlFactory.createNetworkWith3Buses();
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("LD3", new LoadContingency("LD3")));
        List<Action> actions = List.of(new TerminalsConnectionAction("openL23", "L23", true));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyOpenL23", ContingencyContext.specificContingency("LD3"), new TrueCondition(), List.of("openL23")));

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        assertEquals(100.0, getOperatorStrategyResult(result, "strategyOpenL23").getNetworkResult().getBranchResult("PS1").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastSaDcLineConnectionAction() {
        Network network = FourBusNetworkFactory.create();
        network.getLine("l23").getTerminal1().disconnect();
        network.getLine("l23").getTerminal2().disconnect();
        List<Contingency> contingencies = Stream.of("l14")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        List<Action> actions = List.of(new TerminalsConnectionAction("closeLine", "l23", false));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyL1", ContingencyContext.specificContingency("l14"), new TrueCondition(), List.of("closeLine")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        OperatorStrategyResult dcStrategyResult = getOperatorStrategyResult(result, "strategyL1");

        assertEquals(0.333, dcStrategyResult.getNetworkResult().getBranchResult("l12").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.333, dcStrategyResult.getNetworkResult().getBranchResult("l23").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.0, dcStrategyResult.getNetworkResult().getBranchResult("l34").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastSaDcTransformerConnectionAction() {
        // TODO : problem to update a value that is not present here... Big change expected
        // FIXME : when a t2wt is added by the closing of an action, seems very problematic...
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new TerminalsConnectionAction("closePS1", "PS1", false));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyClosePS1", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("closePS1")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(false);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

        network.getTwoWindingsTransformer("PS1").getTerminal1().disconnect();
        network.getTwoWindingsTransformer("PS1").getTerminal2().disconnect();
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        assertEquals(50.0, getOperatorStrategyResult(result, "strategyClosePS1").getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDcSecurityAnalysisWithOperatorStrategy() {
        Network network = NodeBreakerNetworkFactory.create3Bars();
        network.getSwitch("C1").setOpen(true);
        network.getLineStream().forEach(line -> {
            if (line.getCurrentLimits1().isPresent()) {
                line.getCurrentLimits1().orElseThrow().setPermanentLimit(310);
            }
            if (line.getCurrentLimits2().isPresent()) {
                line.getCurrentLimits2().orElseThrow().setPermanentLimit(310);
            }
        });

        List<Contingency> contingencies = Stream.of("L3")
                .map(id -> new Contingency(id, new BranchContingency(id)))
                .collect(Collectors.toList());

        // FIXME : add the cases with connectivity modification due to the actions
        List<Action> actions = List.of(new SwitchAction("action1", "C1", false));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyL3", ContingencyContext.specificContingency("L3"), new TrueCondition(), List.of("action1")));
        List<StateMonitor> monitors = createAllBranchesMonitors(network);

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(false);
        parameters.setDc(true);
        setSlackBusId(parameters, "VL2_0");
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setLoadFlowParameters(parameters);

        // verify pre contingency results
        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);
        assertEquals(400.0, result.getPreContingencyResult().getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.0, result.getPreContingencyResult().getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.0, result.getPreContingencyResult().getNetworkResult().getBranchResult("L3").getP1(), LoadFlowAssert.DELTA_POWER);

        // compare post contingency/action results with load flow
        network.getLine("L3").disconnect();
        network.getSwitch("C1").setOpen(false);
        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), getOperatorStrategyResult(result, "strategyL3").getNetworkResult().getBranchResult("L2").getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L1").getTerminal1().getP(), getOperatorStrategyResult(result, "strategyL3").getNetworkResult().getBranchResult("L1").getP1(), LoadFlowAssert.DELTA_POWER);
    }
}
