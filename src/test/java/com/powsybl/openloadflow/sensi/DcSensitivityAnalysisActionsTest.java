/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.contingency.strategy.condition.TrueCondition;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
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
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.ALL_CONTINGENCIES);
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
                .setOperatorStrategiesCalculationMode(SensitivityOperatorStrategiesCalculationMode.ALL_CONTINGENCIES);
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
}
