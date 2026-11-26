/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.contingency.*;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.test.CommonTestConfig;
import com.powsybl.openloadflow.test.LoadFlowAssert;
import com.powsybl.openloadflow.test.networks.HvdcNetworkFactory;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DC sensitivity analysis with HVDC lines in AC emulation: the linear droop law couples the two buses of the HVDC line
 * like a virtual branch. The results are checked against ordinary DC load flows run on the corresponding networks.
 * Saturation of the droop control is not handled (no outer loop in DC sensitivity analysis).
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class DcSensitivityAnalysisHvdcAcEmulationTest extends AbstractSensitivityAnalysisTest {

    private static final List<String> SIX_BUSES_BRANCHES = List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56");

    DcSensitivityAnalysisHvdcAcEmulationTest(CommonTestConfig config) {
        super(config);
    }

    /**
     * Six buses network with the HVDC line hvdc34 (b3 - b4) in AC emulation, in parallel with the AC line l25.
     * The HVDC active power limit is large enough so that the droop control never saturates.
     */
    private static Network createSixBusesNetwork(double p0) {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        HvdcLine hvdcLine = network.getHvdcLine("hvdc34");
        hvdcLine.setMaxP(1000);
        hvdcLine.newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0((float) p0)
                .withEnabled(true)
                .add();
        return network;
    }

    /**
     * Network with the HVDC line hvdc23 in AC emulation (p0 = 200 MW), an open switch s2 and parallel lines l12 / l12Bis.
     */
    private static Network createSwitchNetwork() {
        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesAndSwitch(HvdcConverterStation.HvdcType.VSC);
        network.getHvdcLine("hvdc23").setMaxP(2000);
        return network;
    }

    /**
     * Parameters for the switch network: the operated switch is retained as a minimum impedance branch in the sensitivity
     * analysis, and the lines of this network have a very low reactance, so the minimum impedance is lowered for the
     * flows to match the DC load flow where the closed switch merges its two buses.
     */
    private static SensitivityAnalysisParameters createSwitchNetworkParameters() {
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1", true);
        sensiParameters.setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);
        OpenLoadFlowParameters.get(sensiParameters.getLoadFlowParameters()).setLowImpedanceThreshold(1e-11);
        return sensiParameters;
    }

    private static List<SensitivityFactor> createFactors(Network network, List<String> branchIds, String... generatorIds) {
        List<Branch> branches = branchIds.stream().map(network::getBranch).toList();
        return createFactorMatrix(List.of(generatorIds).stream().map(network::getGenerator).toList(), branches);
    }

    private static OperatorStrategy strategy(String id, Action... actions) {
        return new OperatorStrategy(id, ContingencyContext.none(), new TrueCondition(), List.of(actions).stream().map(Action::getId).toList());
    }

    private static OperatorStrategy strategy(String id, String contingencyId, Action... actions) {
        return new OperatorStrategy(id, ContingencyContext.specificContingency(contingencyId), new TrueCondition(), List.of(actions).stream().map(Action::getId).toList());
    }

    private static Network applyToCopy(Network network, Consumer<Network> change) {
        Network copy = NetworkSerDe.copy(network);
        change.accept(copy);
        return copy;
    }

    private static void disconnect(Network network, String... connectableIds) {
        for (String id : connectableIds) {
            network.getConnectable(id).getTerminals().forEach(t -> t.disconnect());
        }
    }

    private void runDcLoadFlow(Network network, LoadFlowParameters parameters) {
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());
    }

    /**
     * Check that the reference flows of a state match the flows of a DC load flow run on the given network.
     */
    private void assertReferenceFlowsMatch(Network reference, LoadFlowParameters parameters, SensitivityAnalysisResult result,
                                           SensitivityState state, List<String> branchIds) {
        runDcLoadFlow(reference, parameters);
        for (String branchId : branchIds) {
            assertEquals(reference.getBranch(branchId).getTerminal1().getP(),
                    result.getFunctionReferenceValue(state, branchId, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1),
                    LoadFlowAssert.DELTA_POWER, branchId + " reference flow in state " + state);
        }
    }

    /**
     * Check that the sensitivities to the active power of a generator match a finite difference of DC load flows. The slack
     * is not distributed, so the finite difference is a +1 MW on the generator compensated by the slack bus, like the
     * sensitivity analysis.
     */
    private void assertSensitivitiesMatchFiniteDifference(Network reference, LoadFlowParameters parameters, SensitivityAnalysisResult result,
                                                          SensitivityState state, String generatorId, List<String> branchIds) {
        runDcLoadFlow(reference, parameters);
        Network shifted = applyToCopy(reference, n -> n.getGenerator(generatorId).setTargetP(n.getGenerator(generatorId).getTargetP() + 1));
        runDcLoadFlow(shifted, parameters);
        for (String branchId : branchIds) {
            double expected = shifted.getBranch(branchId).getTerminal1().getP() - reference.getBranch(branchId).getTerminal1().getP();
            assertEquals(expected,
                    result.getSensitivityValue(state, generatorId, branchId, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER),
                    LoadFlowAssert.DELTA_SENSITIVITY_VALUE, generatorId + " -> " + branchId + " sensitivity in state " + state);
        }
    }

    private static void assertSameValues(SensitivityAnalysisResult expected, SensitivityState expectedState,
                                         SensitivityAnalysisResult actual, SensitivityState actualState, List<SensitivityFactor> factors) {
        for (SensitivityFactor factor : factors) {
            assertEquals(expected.getFunctionReferenceValue(expectedState, factor.getFunctionId(), factor.getFunctionType()),
                    actual.getFunctionReferenceValue(actualState, factor.getFunctionId(), factor.getFunctionType()),
                    LoadFlowAssert.DELTA_POWER, factor.getFunctionId() + " reference");
            assertEquals(expected.getSensitivityValue(expectedState, factor.getVariableId(), factor.getFunctionId(), factor.getFunctionType(), factor.getVariableType()),
                    actual.getSensitivityValue(actualState, factor.getVariableId(), factor.getFunctionId(), factor.getFunctionType(), factor.getVariableType()),
                    LoadFlowAssert.DELTA_SENSITIVITY_VALUE, factor.getFunctionId() + " sensitivity");
        }
    }

    @ParameterizedTest(name = "p0 = {0} MW")
    @ValueSource(doubles = {0, 50})
    void testBaseCaseMatchesDcLoadFlow(double p0) {
        Network network = createSixBusesNetwork(p0);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters));

        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        assertTrue(lfParameters.isHvdcAcEmulation());
        assertReferenceFlowsMatch(NetworkSerDe.copy(network), lfParameters, result, SensitivityState.PRE_CONTINGENCY, SIX_BUSES_BRANCHES);
        assertSensitivitiesMatchFiniteDifference(NetworkSerDe.copy(network), lfParameters, result, SensitivityState.PRE_CONTINGENCY, "g5", SIX_BUSES_BRANCHES);

        // the droop coupling (b3 - b4) is a parallel path to l25: the results differ from the fixed set point mode
        SensitivityAnalysisParameters sensiParametersWithoutEmulation = createParameters(true, "b1_vl_0", false);
        sensiParametersWithoutEmulation.getLoadFlowParameters().setHvdcAcEmulation(false);
        SensitivityAnalysisResult resultWithoutEmulation = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParametersWithoutEmulation));
        assertNotEquals(resultWithoutEmulation.getBranchFlow1SensitivityValue("g5", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER),
                result.getBranchFlow1SensitivityValue("g5", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), 1e-3);
        assertNotEquals(resultWithoutEmulation.getBranchFlow1FunctionReferenceValue("l25"), result.getBranchFlow1FunctionReferenceValue("l25"), 1e-3);
    }

    @Test
    void testBaseCaseWithDistributedSlackMatchesDcLoadFlow() {
        Network network = createSixBusesNetwork(30);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters));

        assertReferenceFlowsMatch(NetworkSerDe.copy(network), sensiParameters.getLoadFlowParameters(), result, SensitivityState.PRE_CONTINGENCY, SIX_BUSES_BRANCHES);
    }

    @Test
    void testBranchContingenciesMatchDcLoadFlow() {
        Network network = createSixBusesNetwork(50);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");
        List<Contingency> contingencies = List.of(Contingency.line("l12"), Contingency.line("l45"), new Contingency("l12+l56", new LineContingency("l12"), new LineContingency("l56")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies));

        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        for (Contingency contingency : contingencies) {
            assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(SensitivityState.postContingency(contingency.getId())));
            List<String> lostBranchIds = contingency.getElements().stream().map(ContingencyElement::getId).toList();
            Network reference = applyToCopy(network, n -> disconnect(n, lostBranchIds.toArray(new String[0])));
            List<String> remainingBranchIds = SIX_BUSES_BRANCHES.stream().filter(id -> !lostBranchIds.contains(id)).toList();
            assertReferenceFlowsMatch(reference, lfParameters, result, SensitivityState.postContingency(contingency.getId()), remainingBranchIds);
            assertSensitivitiesMatchFiniteDifference(reference, lfParameters, result, SensitivityState.postContingency(contingency.getId()), "g5", remainingBranchIds);
        }
    }

    @Test
    void testHvdcLineContingencyMatchesDcLoadFlow() {
        Network network = createSixBusesNetwork(50);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");
        List<Contingency> contingencies = List.of(Contingency.hvdcLine("hvdc34"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(contingencies));

        // when the HVDC line is lost, the droop coupling and the constant term p0 of the droop law disappear
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        Network reference = applyToCopy(network, n -> disconnect(n, "cs3", "cs4"));
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(SensitivityState.postContingency("hvdc34")));
        assertReferenceFlowsMatch(reference, lfParameters, result, SensitivityState.postContingency("hvdc34"), SIX_BUSES_BRANCHES);
        assertSensitivitiesMatchFiniteDifference(reference, lfParameters, result, SensitivityState.postContingency("hvdc34"), "g5", SIX_BUSES_BRANCHES);
    }

    @Test
    void testMixedBranchAndHvdcContingencies() {
        // a branch contingency and an HVDC line contingency in the same analysis must give the same results as when run alone
        Network network = createSixBusesNetwork(50);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g5");
        Contingency branchContingency = Contingency.line("l12");
        Contingency hvdcContingency = Contingency.hvdcLine("hvdc34");

        SensitivityAnalysisResult branchAlone = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters).setContingencies(List.of(branchContingency)));
        SensitivityAnalysisResult hvdcAlone = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters).setContingencies(List.of(hvdcContingency)));
        SensitivityAnalysisResult mixed = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters)
                .setContingencies(List.of(branchContingency, hvdcContingency)));

        List<SensitivityFactor> remainingFactors = factors.stream().filter(f -> !"l12".equals(f.getFunctionId())).toList();
        assertSameValues(branchAlone, SensitivityState.postContingency("l12"), mixed, SensitivityState.postContingency("l12"), remainingFactors);
        assertSameValues(hvdcAlone, SensitivityState.postContingency("hvdc34"), mixed, SensitivityState.postContingency("hvdc34"), factors);
    }

    @ParameterizedTest(name = "isolated side has {0}")
    @ValueSource(strings = {"nothing", "generation"})
    void testIslandingContingencyMatchesDcLoadFlow(String isolatedSide) {
        Network network = createSixBusesNetwork(50);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");
        // b4 (converter station cs4 only) is isolated by losing l45 and l46; the whole b4-b5-b6 side (with g5) is isolated
        // by losing l25
        Contingency contingency = "nothing".equals(isolatedSide)
                ? new Contingency("isolate b4", new LineContingency("l45"), new LineContingency("l46"))
                : Contingency.line("l25");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(List.of(contingency)));

        // one bus of the HVDC line is lost: the line transmits no power anymore, like in DC security analysis
        List<String> lostBranchIds = contingency.getElements().stream().map(ContingencyElement::getId).toList();
        Network reference = applyToCopy(network, n -> {
            disconnect(n, lostBranchIds.toArray(new String[0]));
            disconnect(n, "cs3", "cs4");
        });
        List<String> mainComponentBranchIds = "nothing".equals(isolatedSide) ? List.of("l12", "l13", "l23", "l25", "l56") : List.of("l12", "l13", "l23");
        SensitivityState state = SensitivityState.postContingency(contingency.getId());
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(state));
        assertReferenceFlowsMatch(reference, sensiParameters.getLoadFlowParameters(), result, state, mainComponentBranchIds);
        if ("nothing".equals(isolatedSide)) {
            assertSensitivitiesMatchFiniteDifference(reference, sensiParameters.getLoadFlowParameters(), result, state, "g5", mainComponentBranchIds);
        }
    }

    @Test
    void testPreventiveSwitchStrategiesMatchDcLoadFlow() {
        Network network = createSwitchNetwork();
        List<String> branchIds = List.of("l12", "l12Bis", "l34", "l14");
        SensitivityAnalysisParameters sensiParameters = createSwitchNetworkParameters();
        List<SensitivityFactor> factors = createFactors(network, branchIds, "g1");
        Action closeS2 = new SwitchAction("closeS2", "s2", false);
        Action openL12 = new TerminalsConnectionAction("openL12", "l12", true);
        List<Action> actions = List.of(closeS2, openL12);
        List<OperatorStrategy> strategies = List.of(strategy("closeS2", closeS2), strategy("openL12", openL12), strategy("both", closeS2, openL12));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setOperatorStrategies(strategies)
                .setActions(actions));
        List<OperatorStrategy> reversedStrategies = new ArrayList<>(strategies);
        Collections.reverse(reversedStrategies);
        SensitivityAnalysisResult reversedResult = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setOperatorStrategies(reversedStrategies)
                .setActions(actions));

        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        assertReferenceFlowsMatch(NetworkSerDe.copy(network), lfParameters, result, SensitivityState.PRE_CONTINGENCY, branchIds);
        assertReferenceFlowsMatch(applyToCopy(network, n -> n.getSwitch("s2").setOpen(false)), lfParameters, result,
                new SensitivityState(null, "closeS2"), branchIds);
        assertReferenceFlowsMatch(applyToCopy(network, n -> disconnect(n, "l12")), lfParameters, result,
                new SensitivityState(null, "openL12"), List.of("l12Bis", "l34", "l14"));
        assertReferenceFlowsMatch(applyToCopy(network, n -> {
            n.getSwitch("s2").setOpen(false);
            disconnect(n, "l12");
        }), lfParameters, result, new SensitivityState(null, "both"), List.of("l12Bis", "l34", "l14"));

        // results do not depend on the strategies order
        for (OperatorStrategy strategy : strategies) {
            SensitivityState state = new SensitivityState(null, strategy.getId());
            assertSameValues(result, state, reversedResult, state, factors.stream().filter(f -> !"l12".equals(f.getFunctionId())).toList());
        }

        // the network is left unchanged
        assertTrue(network.getSwitch("s2").isOpen());
        assertTrue(network.getLine("l12").getTerminal1().isConnected());
        assertEquals(400, network.getGenerator("g1").getTargetP());
    }

    @Test
    void testContingencyWithOperatorStrategyMatchesDcLoadFlow() {
        Network network = createSwitchNetwork();
        List<String> branchIds = List.of("l12", "l12Bis", "l34", "l14");
        SensitivityAnalysisParameters sensiParameters = createSwitchNetworkParameters();
        List<SensitivityFactor> factors = createFactors(network, branchIds, "g1");
        Action closeS2 = new SwitchAction("closeS2", "s2", false);
        Contingency contingency = Contingency.line("l12");
        OperatorStrategy strategy = strategy("closeS2AfterL12", contingency.getId(), closeS2);

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setParameters(sensiParameters)
                .setContingencies(List.of(contingency))
                .setOperatorStrategies(List.of(strategy))
                .setActions(List.of(closeS2)));

        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        List<String> remainingBranchIds = List.of("l12Bis", "l34", "l14");
        assertReferenceFlowsMatch(applyToCopy(network, n -> disconnect(n, "l12")), lfParameters, result,
                SensitivityState.postContingency("l12"), remainingBranchIds);
        assertReferenceFlowsMatch(applyToCopy(network, n -> {
            disconnect(n, "l12");
            n.getSwitch("s2").setOpen(false);
        }), lfParameters, result, new SensitivityState("l12", "closeS2AfterL12"), remainingBranchIds);
    }

    @Test
    void testActivePowerLimitExceededKeepsLinearModel() {
        // the DC sensitivity analysis has no outer loop: when the droop law gives an active power above the HVDC limit,
        // the linear model is kept (a warning is logged), unlike the DC load flow that saturates the HVDC line
        Network network = createSixBusesNetwork(50);
        Network limitedNetwork = createSixBusesNetwork(50);
        limitedNetwork.getHvdcLine("hvdc34").setMaxP(0.1);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<SensitivityFactor> factors = createFactors(network, SIX_BUSES_BRANCHES, "g1", "g5");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters));
        SensitivityAnalysisResult limitedResult = sensiRunner.run(limitedNetwork, factors, new SensitivityAnalysisRunParameters().setParameters(sensiParameters));

        assertSameValues(result, SensitivityState.PRE_CONTINGENCY, limitedResult, SensitivityState.PRE_CONTINGENCY, factors);
    }
}
