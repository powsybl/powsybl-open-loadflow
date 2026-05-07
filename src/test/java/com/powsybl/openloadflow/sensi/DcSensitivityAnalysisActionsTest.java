/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.action.SwitchAction;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.CommonTestConfig;
import com.powsybl.openloadflow.network.ConnectedComponentNetworkFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.network.NodeBreakerNetworkFactory;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class DcSensitivityAnalysisActionsTest extends AbstractSensitivityAnalysisTest {

    DcSensitivityAnalysisActionsTest(CommonTestConfig commonTestConfig) {
        super(commonTestConfig);
    }

    @Test
    void testUsingContingencies() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23", List.of(new BranchContingency("l23"))),
                                                  new Contingency("l23+l14", List.of(new BranchContingency("l23"), new BranchContingency("l14"))));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                network.getBranchStream().toList());

        List<OperatorStrategy> operatorStrategies = Collections.emptyList();
        List<Action> actions = Collections.emptyList();
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions));

        var contSimpleState = SensitivityState.postContingency("l23");
        var contDoubleState = SensitivityState.postContingency("l23+l14");
        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(5, result.getValues(contSimpleState).size());
        assertEquals(5, result.getValues(contDoubleState).size());

        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contSimpleState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contDoubleState));

        // sensi N
        assertEquals(0.05d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.35d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.25d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.15d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.1d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.5d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // sensi N-1
        assertEquals(0.1333d, result.getSensitivityValue(contSimpleState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.6d, result.getSensitivityValue(contSimpleState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contSimpleState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N-1
        assertEquals(0.666d, result.getFunctionReferenceValue(contSimpleState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, result.getFunctionReferenceValue(contSimpleState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contSimpleState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1.666d, result.getFunctionReferenceValue(contSimpleState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.333d, result.getFunctionReferenceValue(contSimpleState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // sensi N-2
        assertEquals(0d, result.getSensitivityValue(contDoubleState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.6d, result.getSensitivityValue(contDoubleState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contDoubleState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.2d, result.getSensitivityValue(contDoubleState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.2d, result.getSensitivityValue(contDoubleState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N-2
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contDoubleState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1d, result.getFunctionReferenceValue(contDoubleState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contDoubleState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1d, result.getFunctionReferenceValue(contDoubleState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(3d, result.getFunctionReferenceValue(contDoubleState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testUsingContingencyAndOperatorStrategy() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true)
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                network.getBranchStream().toList());

        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("open l14",
                ContingencyContext.all(),
                new TrueCondition(), List.of("open l14")));
        List<Action> actions = List.of(new TerminalsConnectionAction("open l14", "l14", true));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions));

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(5, result.getValues(SensitivityState.postContingency("l23")).size());
        assertEquals(5, result.getValues(new SensitivityState("l23", "open l14")).size());

        var contSimpleState = SensitivityState.postContingency("l23");
        var contAndOpStratState = new SensitivityState("l23", "open l14");
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contSimpleState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contAndOpStratState));

        // should give results equivalent to the previous test with a double contingency

        // sensi N
        assertEquals(0.05d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.35d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.25d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.15d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.1d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.5d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // sensi N-1
        assertEquals(0.1333d, result.getSensitivityValue(contSimpleState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.6d, result.getSensitivityValue(contSimpleState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contSimpleState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N-1
        assertEquals(0.666d, result.getFunctionReferenceValue(contSimpleState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, result.getFunctionReferenceValue(contSimpleState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contSimpleState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1.666d, result.getFunctionReferenceValue(contSimpleState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.333d, result.getFunctionReferenceValue(contSimpleState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // sensi curative
        assertEquals(0d, result.getSensitivityValue(contAndOpStratState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.6d, result.getSensitivityValue(contAndOpStratState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contAndOpStratState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.2d, result.getSensitivityValue(contAndOpStratState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.2d, result.getSensitivityValue(contAndOpStratState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow curative
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contAndOpStratState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1d, result.getFunctionReferenceValue(contAndOpStratState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contAndOpStratState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1d, result.getFunctionReferenceValue(contAndOpStratState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(3d, result.getFunctionReferenceValue(contAndOpStratState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReconnectContingencyLine() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true)
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                network.getBranchStream().toList());

        // the operator strategy is to reconnect the contingency line l23
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("reclose l23",
                                                                                 ContingencyContext.all(),
                                                                                 new TrueCondition(), List.of("reclose l23")));
        List<Action> actions = List.of(new TerminalsConnectionAction("reclose l23", "l23", false));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions));

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(5, result.getValues(SensitivityState.postContingency("l23")).size());
        assertEquals(5, result.getValues(new SensitivityState("l23", "reclose l23")).size());

        var contSimpleState = SensitivityState.postContingency("l23");
        var contAndOpStratState = new SensitivityState("l23", "reclose l23");
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contSimpleState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contAndOpStratState));

        // sensi N
        assertEquals(0.05d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.35d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.25d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.15d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.1d, result.getSensitivityValue(SensitivityState.PRE_CONTINGENCY, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.25d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.5d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // sensi N-1
        assertEquals(0.1333d, result.getSensitivityValue(contSimpleState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.6d, result.getSensitivityValue(contSimpleState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contSimpleState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.0666d, result.getSensitivityValue(contSimpleState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N-1
        assertEquals(0.666d, result.getFunctionReferenceValue(contSimpleState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, result.getFunctionReferenceValue(contSimpleState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contSimpleState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
        assertEquals(-1.666d, result.getFunctionReferenceValue(contSimpleState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(2.333d, result.getFunctionReferenceValue(contSimpleState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // as we reconnect the contingency line, sensi and reference values should be the same as in pre-contingency test

        // sensi curative
        assertEquals(0.05d, result.getSensitivityValue(contAndOpStratState, "g2", "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.35d, result.getSensitivityValue(contAndOpStratState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.25d, result.getSensitivityValue(contAndOpStratState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.15d, result.getSensitivityValue(contAndOpStratState, "g2", "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(-0.1d, result.getSensitivityValue(contAndOpStratState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow curative
        assertEquals(0.25d, result.getFunctionReferenceValue(contAndOpStratState, "l14", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getFunctionReferenceValue(contAndOpStratState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.25d, result.getFunctionReferenceValue(contAndOpStratState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.25d, result.getFunctionReferenceValue(contAndOpStratState, "l34", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.5d, result.getFunctionReferenceValue(contAndOpStratState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReconnectingSmallComponent() {
        // this case is not supported yet by Woodbury DC Sensitivity
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLinesWithAdditionnalGens();
        network.getLine("l24").disconnect();
        network.getLine("l35").disconnect();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters()
                .setLoadFlowParameters(new LoadFlowParameters().setDc(true))
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                network.getBranchStream().toList());

        // the operator strategy is to reconnect the contingency line l35
        List<OperatorStrategy> operatorStrategies = List.of(new OperatorStrategy("reclose l35",
                ContingencyContext.all(),
                new TrueCondition(), List.of("reclose l35")));
        List<Action> actions = List.of(new TerminalsConnectionAction("reclose l35", "l35", false));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions));

        assertEquals(8, result.getPreContingencyValues().size());
        assertEquals(8, result.getValues(SensitivityState.postContingency("l23")).size());
        assertEquals(8, result.getValues(new SensitivityState("l23", "reclose l35")).size());

        var contSimpleState = SensitivityState.postContingency("l23");
        var contAndOpStratState = new SensitivityState("l23", "reclose l35");
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contSimpleState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contAndOpStratState));

        // sensi N-1
        assertEquals(-0.4d, result.getSensitivityValue(contSimpleState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.4d, result.getSensitivityValue(contSimpleState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contSimpleState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow N-1
        assertEquals(-2.4d, result.getFunctionReferenceValue(contSimpleState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4d, result.getFunctionReferenceValue(contSimpleState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contSimpleState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));

        // sensi curative
        assertEquals(-0.4d, result.getSensitivityValue(contAndOpStratState, "g2", "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0.4d, result.getSensitivityValue(contAndOpStratState, "g2", "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);
        assertEquals(0d, result.getSensitivityValue(contAndOpStratState, "g2", "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_SENSITIVITY_VALUE);

        // reference flow curative
        assertEquals(-2.4d, result.getFunctionReferenceValue(contAndOpStratState, "l12", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.4d, result.getFunctionReferenceValue(contAndOpStratState, "l13", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contAndOpStratState, "l23", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
    }

    @Test
    void testSwitchAction() {
        Network network = NodeBreakerNetworkFactory.create();
        network.getVoltageLevel("VL1").getNodeBreakerView().newBreaker()
                .setId("B19")
                .setNode1(1)
                .setNode2(9)
                .add();
        network.getVoltageLevel("VL2").getNodeBreakerView().newBreaker()
                .setId("B29")
                .setNode1(0)
                .setNode2(9)
                .add();
        network.newLine()
                .setId("L2_bis")
                .setVoltageLevel1("VL1")
                .setNode1(9)
                .setVoltageLevel2("VL2")
                .setNode2(9)
                .setR(3.0)
                .setX(33.0)
                .setB1(386E-6 / 2)
                .setB2(386E-6 / 2)
                .add();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL1_0", true)
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.CONTINGENCIES_AND_OPERATOR_STRATEGIES);

        List<Contingency> contingencies = List.of(new Contingency("L2_bis", new BranchContingency("L2_bis")));
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("G")),
                network.getBranchStream().toList());

        // the operator strategy is to open breaker C
        OperatorStrategy osOpenC = new OperatorStrategy("open C",
                ContingencyContext.all(),
                new TrueCondition(), List.of("open C"));
        OperatorStrategy osCloseC = new OperatorStrategy("close C",
                ContingencyContext.all(),
                new TrueCondition(), List.of("close C"));
        List<OperatorStrategy> operatorStrategies = List.of(osOpenC, osCloseC);
        List<Action> actions = List.of(new SwitchAction("open C", "C", true),
                new SwitchAction("close C", "C", false));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, new SensitivityAnalysisRunParameters()
                .setContingencies(contingencies)
                .setParameters(sensiParameters)
                .setOperatorStrategies(operatorStrategies)
                .setActions(actions));
        var contState = SensitivityState.postContingency("L2_bis");
        var contAndOpenCState = new SensitivityState("L2_bis", "open C");
        var contAndCloseCState = new SensitivityState("L2_bis", "close C");
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contAndOpenCState));
        assertSame(SensitivityAnalysisResult.Status.SUCCESS, result.getStateStatus(contAndCloseCState));

        // reference flow N, 200MW on each
        assertEquals(200d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "L1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(200d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "L2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(200d, result.getFunctionReferenceValue(SensitivityState.PRE_CONTINGENCY, "L2_bis", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);

        // reference flow N-K, without L2_bus, 300MW on each remaining lines
        assertEquals(300d, result.getFunctionReferenceValue(contState, "L1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(300d, result.getFunctionReferenceValue(contState, "L2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contState, "L2_bis", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));

        // reference flow on curative C opened, all the flow goes though L2: 600 MW
        assertEquals(0, result.getFunctionReferenceValue(contAndOpenCState, "L1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(600d, result.getFunctionReferenceValue(contAndOpenCState, "L2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contAndOpenCState, "L2_bis", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));

        // reference flow on curative C closed, nothing happen same state as on N-K state
        assertEquals(300d, result.getFunctionReferenceValue(contAndCloseCState, "L1", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertEquals(300d, result.getFunctionReferenceValue(contAndCloseCState, "L2", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1), LoadFlowAssert.DELTA_POWER);
        assertTrue(Double.isNaN(result.getFunctionReferenceValue(contAndCloseCState, "L2_bis", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1)));
    }
}
