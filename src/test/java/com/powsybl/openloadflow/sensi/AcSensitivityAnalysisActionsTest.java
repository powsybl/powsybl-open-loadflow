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
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AcSensitivityAnalysisActionsTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testUsingContingencyAndOperatorStrategy() {
        Network network = FourBusNetworkFactory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(false, "b1_vl_0", true)
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
    }
}
