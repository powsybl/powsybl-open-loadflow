/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc.fastdc;

import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.action.SwitchAction;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.SwitchContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfPhaseTapChangerAction;
import com.powsybl.openloadflow.network.action.LfSwitchAction;
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
    private Network nbNetworkRef;
    private Network nbNetwork;

    @BeforeEach
    void setUp() {
        dcParameters = new DcLoadFlowParameters();
        fourBusNetworkRef = FourBusNetworkFactory.create();
        fourBusNetwork = FourBusNetworkFactory.create();

        pstNetworkRef = PhaseControlFactory.createWithOneT2wtTwoLines();
        pstNetwork = PhaseControlFactory.createWithOneT2wtTwoLines();

        nbNetworkRef = NodeBreakerNetworkFactory.create();
        nbNetwork = NodeBreakerNetworkFactory.create();
    }

    private static double[] calculateFlows(LfNetwork lfNetwork, DenseMatrix flowStates, Set<String> disconnectedBranchIds) {
        double[] flows = new double[lfNetwork.getBranches().size()];
        for (LfBranch branch : lfNetwork.getBranches()) {
            if (disconnectedBranchIds.contains(branch.getId())) {
                flows[branch.getNum()] = Double.NaN;
            } else {
                if (branch.getP1() instanceof EquationTerm<?, ?> p1) {
                    flows[branch.getNum()] = p1.calculateSensi(flowStates, 0);
                }
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
            List<ComputedBranchContingencyElement> contingencyElements = List.of(new ComputedBranchContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
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
            List<ComputedBranchContingencyElement> contingencyElements = List.of(new ComputedBranchContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
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
            List<ComputedBranchContingencyElement> contingencyElements = List.of(new ComputedBranchContingencyElement(new BranchContingency("l23"), lfNetwork, context.getEquationSystem()));
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

            List<ComputedBranchContingencyElement> contingencyElements = List.of(new ComputedBranchContingencyElement(new BranchContingency("L1"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            List<LfAction> actions = List.of(new LfPhaseTapChangerAction(new PhaseTapChangerTapPositionAction("PS1", "PS1", false, newTapPosition), lfNetwork));
            List<ComputedElement> actionElements = List.of(new ComputedTapPositionChangeElement(new TapPositionChange(lfNetwork.getBranchById("PS1"), newTapPosition, false),
                context.getEquationSystem()));
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

    @Test
    void testSwitchContingency() {
        nbNetworkRef.getSwitch("C").setOpen(true);
        double[] flowsRef = calculateFlows(nbNetworkRef);

        LfTopoConfig topoConfig = new LfTopoConfig();
        dcParameters.getNetworkParameters()
                .setBreakers(true)
                .setMinImpedance(true);
        LfNetwork lfNetwork = LfNetwork.load(nbNetwork, new LfNetworkLoaderImpl(), topoConfig, dcParameters.getNetworkParameters(), ReportNode.NO_OP).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context)
                    .run();

            List<ComputedBranchContingencyElement> contingencyElements = List.of(new ComputedBranchContingencyElement(new SwitchContingency("C"), lfNetwork, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(contingencyElements);

            List<LfAction> actions = List.of();
            List<ComputedElement> actionElements = List.of();
            ComputedElement.setComputedElementIndexes(actionElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, actionElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] flowStatesArray = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, actions, ReportNode.NO_OP);
            var flowStates = new DenseMatrix(flowStatesArray.length, 1, flowStatesArray);
            engine.toPostContingencyAndOperatorStrategyStates(flowStates);
            var flows = calculateFlows(lfNetwork, flowStates, Set.of("C", "B3"));
            assertArrayEquals(flowsRef, flows, LoadFlowAssert.DELTA_POWER);
        }
    }

    /**
     * Verifies that {@link ComputedHvdcAcEmulationElement} plugs correctly into the Woodbury engine.
     * <p>
     * The HVDC droop coupling k·(φ1 − φ2) is mathematically a virtual branch with susceptance k.
     * Removing it via the Woodbury formula must yield the same AC-line flows as a reference DC load
     * flow where the HVDC converter stations have been physically disconnected.
     */
    @Test
    void testHvdcAcEmulationContingency() {
        // Reference: physically disconnect the HVDC converter stations and run a standard DC LF.
        // Only the AC lines carry flow; there is no droop coupling between b3 and b4.
        Network hvdcNetworkRef = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        hvdcNetworkRef.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180).withP0(0f).withEnabled(true).add();
        hvdcNetworkRef.getHvdcLine("hvdc34").getConverterStation1().getTerminal().disconnect();
        hvdcNetworkRef.getHvdcLine("hvdc34").getConverterStation2().getTerminal().disconnect();
        double[] flowsRef = calculateFlows(hvdcNetworkRef);

        // Main network: HVDC with AC emulation active (droop coupling in the B-matrix).
        Network hvdcNetwork = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        hvdcNetwork.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180).withP0(0f).withEnabled(true).add();

        LfNetwork lfNetwork = LfNetwork.load(hvdcNetwork, new LfNetworkLoaderImpl(), dcParameters.getNetworkParameters()).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context).run();

            // Build the HVDC contingency element: represents removing the virtual branch
            // with susceptance k = droop * 180/π between bus3 and bus4.
            LfHvdc hvdc = lfNetwork.getHvdcById("hvdc34");
            List<ComputedHvdcAcEmulationElement> hvdcElements = List.of(new ComputedHvdcAcEmulationElement(hvdc, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(hvdcElements);

            // Pre-compute the sensitivity of all angles to injecting ±1 at the HVDC buses.
            DenseMatrix hvdcContingencyStates = ComputedElement.calculateElementsStates(context, hvdcElements);

            // The Woodbury engine applies the rank-1 correction that removes the droop coupling.
            WoodburyEngine engine = new WoodburyEngine(dcParameters.getEquationSystemCreationParameters(), hvdcElements, hvdcContingencyStates);

            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] dx = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, Collections.emptyList(), ReportNode.NO_OP);
            DenseMatrix flowStates = new DenseMatrix(dx.length, 1, dx);
            engine.toPostContingencyStates(flowStates);

            // AC-line flows after Woodbury correction must match the reference (no HVDC coupling).
            assertArrayEquals(flowsRef, calculateFlows(lfNetwork, flowStates, Collections.emptySet()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testActionOpenSwitch() {
        nbNetworkRef.getSwitch("C").setOpen(true);
        double[] flowsRef = calculateFlows(nbNetworkRef);

        LfTopoConfig topoConfig = new LfTopoConfig();
        dcParameters.getNetworkParameters()
                .setBreakers(true)
                .setMinImpedance(true);
        LfNetwork lfNetwork = LfNetwork.load(nbNetwork, new LfNetworkLoaderImpl(), topoConfig, dcParameters.getNetworkParameters(), ReportNode.NO_OP).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            new DcLoadFlowEngine(context)
                    .run();

            List<ComputedBranchContingencyElement> contingencyElements = Collections.emptyList();

            List<LfAction> actions = List.of(new LfSwitchAction(new SwitchAction("open C", "C", true), lfNetwork));
            List<ComputedElement> actionElements = List.of(ComputedSwitchBranchElement.create(lfNetwork.getBranchById("C"), false, context.getEquationSystem()));
            ComputedElement.setComputedElementIndexes(actionElements);

            DenseMatrix contingenciesStates = ComputedElement.calculateElementsStates(context, contingencyElements);
            DenseMatrix actionsStates = ComputedElement.calculateElementsStates(context, actionElements);
            WoodburyEngine engine = new WoodburyEngine(context.getParameters().getEquationSystemCreationParameters(), contingencyElements, contingenciesStates, actionElements, actionsStates);
            DisabledNetwork disabledNetwork = new DisabledNetwork();
            double[] flowStatesArray = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(context, disabledNetwork, actions, ReportNode.NO_OP);
            var flowStates = new DenseMatrix(flowStatesArray.length, 1, flowStatesArray);
            engine.toPostContingencyAndOperatorStrategyStates(flowStates);
            var flows = calculateFlows(lfNetwork, flowStates, Set.of("C", "B3"));
            assertArrayEquals(flowsRef, flows, LoadFlowAssert.DELTA_POWER);
        }
    }
}
