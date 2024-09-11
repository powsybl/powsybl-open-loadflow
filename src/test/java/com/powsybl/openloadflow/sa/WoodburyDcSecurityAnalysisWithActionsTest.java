/*
 *
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.powsybl.openloadflow.sa;

import com.powsybl.action.Action;
import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.MyNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.condition.TrueCondition;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.OperatorStrategyResult;
import com.powsybl.security.strategy.OperatorStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WoodburyDcSecurityAnalysisWithActionsTest extends AbstractOpenSecurityAnalysisTest {

    @Test
    void testSaDcPhaseTapChangerTapPositionAction() {
        Network network = MetrixTutorialSixBusesFactory.create();
        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        List<Contingency> contingencies = List.of(new Contingency("S_SO_1", new BranchContingency("S_SO_1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "NE_NO_1", false, 1),
                new PhaseTapChangerTapPositionAction("pstRelChange", "NE_NO_1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("S_SO_1"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("S_SO_1"), new TrueCondition(), List.of("pstRelChange")));

        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(false);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.create();
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

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.create();
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

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.create();
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

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
    void testSaDcPhaseTapChangerTapPositionChange2() {
        Network network = MyNetworkFactory.create2();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L23", new BranchContingency("L23")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pstRelChange", "PS1", true, -1));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L23"), new TrueCondition(), List.of("pstAbsChange")),
                new OperatorStrategy("strategyTapRelChange", ContingencyContext.specificContingency("L23"), new TrueCondition(), List.of("pstRelChange")));

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(true);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.create();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pstAbsChange", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapAbsChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pstAbsChange")));

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDistributedSlack(false);
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.createWithTwoPhaseTapChangers();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1", new BranchContingency("L1")));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0),
                new PhaseTapChangerTapPositionAction("pst2Change", "PS2", false, 2));
        List<OperatorStrategy> operatorStrategies = List.of(
                new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1"), new TrueCondition(), List.of("pst1Change", "pst2Change")));

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
        Network network = MyNetworkFactory.createWithTwoPhaseTapChangers();

        List<StateMonitor> monitors = createAllBranchesMonitors(network);
        List<Contingency> contingencies = List.of(new Contingency("L1+PS2", List.of(new BranchContingency("L1"), new BranchContingency("PS2"))));
        List<Action> actions = List.of(new PhaseTapChangerTapPositionAction("pst1Change", "PS1", false, 0));
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("strategyTapChange", ContingencyContext.specificContingency("L1+PS2"), new TrueCondition(), List.of("pst1Change")));

        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        LoadFlowParameters parameters = new LoadFlowParameters();
        parameters.setDc(true);
        securityAnalysisParameters.setLoadFlowParameters(parameters);
        OpenSecurityAnalysisParameters openSecurityAnalysisParameters = new OpenSecurityAnalysisParameters();
        openSecurityAnalysisParameters.setDcFastMode(true);
        securityAnalysisParameters.addExtension(OpenSecurityAnalysisParameters.class, openSecurityAnalysisParameters);

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
}
