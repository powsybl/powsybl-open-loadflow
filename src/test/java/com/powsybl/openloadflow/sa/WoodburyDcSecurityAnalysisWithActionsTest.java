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
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.PhaseControlFactory;
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

    @Test
    void testSaDcPhaseTapChangerTapPositionAction() {
        Network network = MetrixTutorialSixBusesFactory.create();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("S_SO_1", new BranchContingency("S_SO_1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "NE_NO_1", false, 1),
                new PhaseTapChangerTapPositionAction("pstRelChange", "NE_NO_1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("S_SO_1"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("S_SO_1"), new TrueCondition(), List.of("pstRelChange")));

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbs = resultAbs.getNetworkResult().getBranchResult("S_SO_2");
        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRel = resultRel.getNetworkResult().getBranchResult("S_SO_2");

        // Apply contingency by hand
        network.getLine("S_SO_1").getTerminal1().disconnect();
        network.getLine("S_SO_1").getTerminal2().disconnect();
        // Apply remedial action
        int originalTapPosition = network.getTwoWindingsTransformer("NE_NO_1").getPhaseTapChanger().getTapPosition();
        network.getTwoWindingsTransformer("NE_NO_1").getPhaseTapChanger().setTapPosition(1);
        loadFlowRunner.run(network, parameters);
        // Compare results
        assertEquals(network.getLine("S_SO_2").getTerminal1().getP(), brAbs.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getBranch("S_SO_2").getTerminal2().getP(), brAbs.getP2(), LoadFlowAssert.DELTA_POWER);

        // Check the second operator strategy: relative change
        network.getTwoWindingsTransformer("NE_NO_1").getPhaseTapChanger().setTapPosition(originalTapPosition - 1);
        loadFlowRunner.run(network, parameters);
        // Compare results
        assertEquals(network.getLine("S_SO_2").getTerminal1().getP(), brRel.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getBranch("S_SO_2").getTerminal2().getP(), brRel.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastSaDcPhaseTapChangerTapPositionChangeAdmittanceOnlyAlphaNull() {
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
        OpenLoadFlowParameters.create(parameters).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);

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

    @Test
    void testFastSaDcPhaseTapChangerTapPositionChangeAdmittanceOnlyAlphaNonNull() {
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
        OpenLoadFlowParameters.create(parameters).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);

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

    @Test
    void testSaDcPhaseTapChangerTapPositionChangeAlphaOnly() {
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
        OpenLoadFlowParameters.create(parameters).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);

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

        SecurityAnalysisResult result = runSecurityAnalysis(network, contingencies, monitors, securityAnalysisParameters,
                operatorStrategies, actions, ReportNode.NO_OP);

        assertNotNull(result);

        OperatorStrategyResult resultAbs = getOperatorStrategyResult(result, "strategyTapAbsChange");
        BranchResult brAbsPS1 = resultAbs.getNetworkResult().getBranchResult("PS1");

        OperatorStrategyResult resultRel = getOperatorStrategyResult(result, "strategyTapRelChange");
        BranchResult brRelPS1 = resultRel.getNetworkResult().getBranchResult("PS1");

        // Apply contingency by hand
        network.getLine("L23").getTerminal1().disconnect();
        network.getLine("L23").getTerminal2().disconnect();
        // Apply remedial action
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(0);
        OpenLoadFlowParameters.create(parameters).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);

        loadFlowRunner.run(network, parameters);

        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brRelPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brRelPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSaDcPhaseTapChangerTapPositionChange() {
        Network network = PhaseControlFactory.createWithOneT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")));

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
        OpenLoadFlowParameters.create(parameters).setLowImpedanceBranchMode(OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE);

        loadFlowRunner.run(network, parameters);

        // Compare results on the line L2
        assertEquals(network.getLine("L2").getTerminal1().getP(), brAbsL2.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getLine("L2").getTerminal2().getP(), brAbsL2.getP2(), LoadFlowAssert.DELTA_POWER);
        // Compare results on the t2wt PS1
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal1().getP(), brAbsPS1.getP1(), LoadFlowAssert.DELTA_POWER);
        assertEquals(network.getTwoWindingsTransformer("PS1").getTerminal2().getP(), brAbsPS1.getP2(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFastSaDcOneContingencyTwoTapPositionChange() {
        Network network = PhaseControlFactory.createWithTwoT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pst2Change", "PS2", false, 2));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pst1Change", "pst2Change")));

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

    @Test
    void testFastSaDcTwoContingenciesOneTapPositionChange() {
        Network network = PhaseControlFactory.createWithTwoT2wtTwoLines();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1+PS2", List.of(new BranchContingency("L1"), new BranchContingency("PS2"))));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1+PS2"), new TrueCondition(), List.of("pst1Change")));

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

    @Test
    void testFastSaDcN2ContingencyOneTapPositionChange() {
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
        assertTrue(thrown.getCause().getMessage().contains("For now, only PhaseTapChangerTapPositionAction and TerminalsConnectionAction are allowed in WoodburyDcSecurityAnalysis"));
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

    // TODO : same with t2wt

    // TODO : same with connectivity lost

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

    // TODO : same with t2wt

    // TODO : same with connectivity lost
}
