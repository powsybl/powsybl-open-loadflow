/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfPhaseTapChangerAction;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class WoodburyEngineTest {

    private DcLoadFlowParameters dcParameters;
    private Network fourBusNetworkRef;
    private Network fourBusNetwork;
    private Network pstNetworkRef;
    private Network pstNetwork;

    @BeforeEach
    void setUp() {
        dcParameters = new DcLoadFlowParameters();
        fourBusNetworkRef = FourBusNetworkFactory.create();
        fourBusNetwork = FourBusNetworkFactory.create();

        pstNetworkRef = PhaseControlFactory.createWithOneT2wtTwoLines();
        pstNetwork = PhaseControlFactory.createWithOneT2wtTwoLines();
    }

    private static double[] calculateFlows(LfNetwork lfNetwork, DenseMatrix flowStates, Set<String> disconnectedBranchIds) {
        double[] flows = new double[lfNetwork.getBranches().size()];
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (disconnectedBranchIds.contains(branch.getId())) {
                flows[branch.getNum()] = Double.NaN;
            } else {
                @SuppressWarnings("unchecked")
                var p1 = (EquationTerm<DcVariableType, DcEquationType>) branch.getP1();
                flows[branch.getNum()] = p1.calculateSensi(flowStates, 0);
            }
        }
        return Arrays.stream(flows)
                .filter(d -> !Double.isNaN(d))
                .toArray();
    }

    private double[] calculateFlows(Network network) {
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            dcParameters.getEquationSystemCreationParameters().setForcePhaseControlOffAndAddAngle1Var(true);
            new DcLoadFlowEngine(context)
                    .run();

            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] dx = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, Collections.emptyList(), ReportNode.NO_OP);
            DenseMatrix flowStates = new DenseMatrix(dx.length, 1, dx);
            return calculateFlows(lfNetwork, flowStates, Collections.emptySet());
        }
    }

    @Test
    void testContingency() {
        fourBusNetworkRef.getLine("l23").disconnect();
        double[] flowsRef = calculateFlows(fourBusNetworkRef);

        LfNetwork lfNetwork = LfNetwork.load(fourBusNetwork, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context)
                    .run();
            List<ComputedContingencyElement> contingencyElements = List.of(new ComputedContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] dx = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, Collections.emptyList(), ReportNode.NO_OP);
            DenseMatrix flowStates = new DenseMatrix(dx.length, 1, dx);
            engine.toPostContingencyStates(flowStates);
            assertArrayEquals(flowsRef, calculateFlows(lfNetwork, flowStates, Set.of("l23")), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testContingencyAndLineDisconnection() {
        fourBusNetworkRef.getLine("l23").disconnect();
        fourBusNetworkRef.getLine("l14").disconnect();
        double[] flowsRef = calculateFlows(fourBusNetworkRef);

        LfNetwork lfNetwork = LfNetwork.load(fourBusNetwork, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context)
                    .run();
            List<ComputedContingencyElement> contingencyElements = List.of(new ComputedContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            List<ComputedElement> actionElements = List.of(ComputedSwitchBranchElement.create(lfNetwork.getBranchById("l14"), false, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(actionElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, actionElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] flowStatesArray = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, Collections.emptyList(), ReportNode.NO_OP);
            var flowStates = new DenseMatrix(flowStatesArray.length, 1, flowStatesArray);
            engine.toPostContingencyAndOperatorStrategyStates(flowStates);
            assertArrayEquals(flowsRef, calculateFlows(lfNetwork, flowStates, Set.of("l23", "l14")), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testContingencyAndLineReconnection() {
        double[] flowsRef = calculateFlows(fourBusNetworkRef);

        LfNetwork lfNetwork = LfNetwork.load(fourBusNetwork, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context)
                    .run();
            List<ComputedContingencyElement> contingencyElements = List.of(new ComputedContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            List<ComputedElement> actionElements = List.of(ComputedSwitchBranchElement.create(lfNetwork.getBranchById("l23"), true, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(actionElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, actionElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] flowStatesArray = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, Collections.emptyList(), ReportNode.NO_OP);
            var flowStates = new DenseMatrix(flowStatesArray.length, 1, flowStatesArray);
            engine.toPostContingencyAndOperatorStrategyStates(flowStates);
            assertArrayEquals(flowsRef, calculateFlows(lfNetwork, flowStates, Collections.emptySet()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testContingencyAndPstTapChange() {
        int newTapPosition = 0;
        pstNetworkRef.getLine("L1").disconnect();
        pstNetworkRef.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(newTapPosition);
        double[] flowsRef = calculateFlows(pstNetworkRef);

        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.addBranchIdWithPtcToRetain("PS1");
        LfNetwork lfNetwork = LfNetwork.load(pstNetwork, new LfNetworkLoaderImpl(), topoConfig, dcParameters.getNetworkParameters(), ReportNode.NO_OP).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            // we need to add phase shift angle variables to have Woodbury working with phase shifter tap update
            context.getParameters().getEquationSystemCreationParameters().setForcePhaseControlOffAndAddAngle1Var(true);
            new DcLoadFlowEngine(context)
                    .run();

            List<ComputedContingencyElement> contingencyElements = List.of(new ComputedContingencyElement(new BranchContingency("L1"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            List<LfAction> actions = List.of(new LfPhaseTapChangerAction(new PhaseTapChangerTapPositionAction("PS1", "PS1", false, newTapPosition), lfNetwork));
            List<ComputedElement> actionElements = List.of(new ComputedTapPositionChangeElement(new TapPositionChange(lfNetwork.getBranchById("PS1"), newTapPosition, false), context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(actionElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, actionElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] flowStatesArray = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, actions, ReportNode.NO_OP);
            var flowStates = new DenseMatrix(flowStatesArray.length, 1, flowStatesArray);
            engine.toPostContingencyAndOperatorStrategyStates(flowStates);
            // we need to update the phase shift in the model that that the equation term tap is also updated and
            // calculateSensi in calculateFlows gives the correct value
            lfNetwork.getBranchById("PS1").getPiModel().setTapPosition(newTapPosition);
            var flows = calculateFlows(lfNetwork, flowStates, Set.of("L1"));
            assertArrayEquals(flowsRef, flows, LoadFlowAssert.DELTA_POWER);
        }
    }
}
