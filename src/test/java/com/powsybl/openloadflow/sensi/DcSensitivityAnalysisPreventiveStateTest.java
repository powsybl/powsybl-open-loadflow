/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.*;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class DcSensitivityAnalysisPreventiveStateTest extends AbstractSensitivityAnalysisTest {

    DcSensitivityAnalysisPreventiveStateTest(CommonTestConfig config) {
        super(config);
    }

    @ParameterizedTest
    @EnumSource(value = LoadFlowParameters.BalanceType.class, names = {
        "PROPORTIONAL_TO_GENERATION_P_MAX", "PROPORTIONAL_TO_GENERATION_P", "PROPORTIONAL_TO_LOAD"
    })
    void preventiveSwitchesRestoreDistributedSlackAndDoNotDependOnStrategyOrder(LoadFlowParameters.BalanceType balanceType) {
        Network network = NodeBreakerNetworkFactory.create();
        var vl = network.getVoltageLevel("VL2");
        vl.getNodeBreakerView().newInternalConnection().setNode1(0).setNode2(4).add();
        vl.newGenerator().setId("G2").setNode(4).setMinP(0).setMaxP(500).setTargetP(100)
                .setTargetQ(40).setVoltageRegulatorOn(true).setTargetV(400).add();
        var parameters = createParameters(true, "VL1_0", true);
        parameters.getLoadFlowParameters().setBalanceType(balanceType);
        List<Action> actions = List.of(new SwitchAction("openC", "C", true),
                new SwitchAction("openB1", "B1", true), new SwitchAction("closeC", "C", false));
        List<OperatorStrategy> strategies = actions.stream().map(action -> strategy(action.getId(), action)).toList();
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().toList(), network.getBranchStream().toList());

        SensitivityAnalysisResult first = runStrategies(network, factors, parameters, strategies, actions);
        List<OperatorStrategy> reversed = new ArrayList<>(strategies);
        Collections.reverse(reversed);
        SensitivityAnalysisResult reordered = runStrategies(network, factors, parameters, reversed, actions);
        SensitivityAnalysisResult repeated = runStrategies(network, factors, parameters, strategies, actions);

        for (Action action : actions) {
            Network reference = NetworkSerDe.copy(network);
            SwitchAction switchAction = (SwitchAction) action;
            reference.getSwitch(switchAction.getSwitchId()).setOpen(switchAction.isOpen());
            var expected = sensiRunner.run(reference, factors, new SensitivityAnalysisRunParameters().setParameters(parameters));
            var state = new SensitivityState(null, action.getId());
            // A retained opened switch gives a zero reference; a standalone calculation can classify
            // the corresponding disconnected branch function as undefined. Compare energized functions.
            List<SensitivityFactor> connectedFactors = factors.stream()
                    .filter(factor -> reference.getBranch(factor.getFunctionId()).getTerminal1().isConnected()).toList();
            assertValues(expected, SensitivityState.PRE_CONTINGENCY, first, state, connectedFactors);
            assertValues(first, state, reordered, state, factors);
            assertValues(first, state, repeated, state, factors);
        }
        assertEquals(603.77, network.getGenerator("G").getTargetP());
        assertEquals(100, network.getGenerator("G2").getTargetP());
        assertEquals(600, network.getLoad("LD").getP0());
        assertEquals(200, network.getLoad("LD").getQ0());
    }

    @ParameterizedTest
    @ValueSource(strings = {"generator", "load", "phase shifter", "islanding"})
    void preventiveActionsMatchIndependentlyAppliedActions(String kind) {
        Network network;
        Action action;
        Consumer<Network> apply;
        String injectionId;
        boolean distributedSlack;
        if ("islanding".equals(kind)) {
            network = FourBusNetworkFactory.createWithTwoRadialBusesReconnectableByOneBranchEach();
            network.getLine("l35").getTerminal1().connect();
            network.getLine("l35").getTerminal2().connect();
            action = new TerminalsConnectionAction("isolate", "l35", true);
            apply = n -> {
                n.getLine("l35").getTerminal1().disconnect();
                n.getLine("l35").getTerminal2().disconnect();
            };
            injectionId = "g2";
            distributedSlack = true;
        } else {
            network = MetrixTutorialSixBusesFactory.create();
            injectionId = "SO_G1";
            // Existing DC action semantics apply injection deltas after slack distribution.
            // Fixed slack gives the same reference as changing the IIDM target before calculation.
            distributedSlack = false;
            switch (kind) {
                case "generator" -> {
                    action = new GeneratorActionBuilder().withId("change").withGeneratorId("SO_G1")
                            .withActivePowerRelativeValue(false).withActivePowerValue(300).build();
                    apply = n -> n.getGenerator("SO_G1").setTargetP(300);
                }
                case "load" -> {
                    action = new LoadActionBuilder().withId("change").withLoadId("SO_L")
                            .withRelativeValue(false).withActivePowerValue(400).build();
                    apply = n -> n.getLoad("SO_L").setP0(400);
                }
                case "phase shifter" -> {
                    action = new PhaseTapChangerTapPositionAction("change", "NE_NO_1", false, 1);
                    apply = n -> n.getTwoWindingsTransformer("NE_NO_1").getPhaseTapChanger().setTapPosition(1);
                }
                default -> throw new IllegalArgumentException(kind);
            }
        }
        var parameters = createParameters(true);
        parameters.getLoadFlowParameters().setDistributedSlack(distributedSlack);
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator(injectionId)),
                network.getBranchStream().filter(branch -> !"l35".equals(branch.getId()) && !"l46".equals(branch.getId())).toList());
        // Repeat a strategy around a reference-state strategy to expose leaked internal state.
        List<OperatorStrategy> strategies = List.of(strategy("first", action),
                new OperatorStrategy("reference", ContingencyContext.none(), new TrueCondition(), List.of()),
                strategy("last", action));
        var result = runStrategies(network, factors, parameters, strategies, List.of(action));
        var unchanged = sensiRunner.run(NetworkSerDe.copy(network), factors,
                new SensitivityAnalysisRunParameters().setParameters(parameters));
        Network reference = NetworkSerDe.copy(network);
        apply.accept(reference);
        var expected = sensiRunner.run(reference, factors, new SensitivityAnalysisRunParameters().setParameters(parameters));

        assertValues(expected, SensitivityState.PRE_CONTINGENCY, result, new SensitivityState(null, "first"), factors);
        assertValues(expected, SensitivityState.PRE_CONTINGENCY, result, new SensitivityState(null, "last"), factors);
        assertValues(unchanged, SensitivityState.PRE_CONTINGENCY, result, new SensitivityState(null, "reference"), factors);
    }

    private SensitivityAnalysisResult runStrategies(Network network, List<SensitivityFactor> factors,
            SensitivityAnalysisParameters parameters, List<OperatorStrategy> strategies, List<Action> actions) {
        return sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setOperatorStrategies(strategies).setActions(actions).setParameters(new SensitivityAnalysisParameters().setLoadFlowParameters(parameters.getLoadFlowParameters())
                        .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES)));
    }

    private static OperatorStrategy strategy(String id, Action action) {
        return new OperatorStrategy(id, ContingencyContext.none(), new TrueCondition(), List.of(action.getId()));
    }

    private static void assertValues(SensitivityAnalysisResult expected, SensitivityState expectedState,
            SensitivityAnalysisResult actual, SensitivityState actualState, List<SensitivityFactor> factors) {
        for (SensitivityFactor factor : factors) {
            assertEquals(expected.getFunctionReferenceValue(expectedState, factor.getFunctionId(), factor.getFunctionType()),
                    actual.getFunctionReferenceValue(actualState, factor.getFunctionId(), factor.getFunctionType()),
                    LoadFlowAssert.DELTA_POWER, factor.getFunctionId() + " reference");
            assertEquals(expected.getSensitivityValue(expectedState, factor.getVariableId(), factor.getFunctionId(),
                            factor.getFunctionType(), factor.getVariableType()),
                    actual.getSensitivityValue(actualState, factor.getVariableId(), factor.getFunctionId(),
                            factor.getFunctionType(), factor.getVariableType()),
                    LoadFlowAssert.DELTA_SENSITIVITY_VALUE, factor.getFunctionId() + " sensitivity");
        }
    }
}
