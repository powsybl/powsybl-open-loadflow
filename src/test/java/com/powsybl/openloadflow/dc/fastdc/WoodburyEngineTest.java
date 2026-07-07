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
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.action.LfAction;
import com.powsybl.openloadflow.network.action.LfActionUtils;
import com.powsybl.openloadflow.network.action.LfPhaseTapChangerAction;
import com.powsybl.openloadflow.network.action.LfSwitchAction;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            ComputedHvdcAcEmulationElement hvdcElement = new ComputedHvdcAcEmulationElement(hvdc, context.getEquationSystem());
            List<ComputedHvdcAcEmulationElement> hvdcElements = List.of(hvdcElement);
            ComputedElement.setComputedElementIndexes(hvdcElements);

            // the HVDC coupling behaves as a virtual branch between its two buses; it is not part of the AC graph
            assertEquals(hvdc, hvdcElement.getLfElement());
            assertNotNull(hvdcElement.getEquation());
            hvdcElement.setLocalIndex(0);
            assertEquals(0, hvdcElement.getLocalIndex());
            // tripping the HVDC does not modify AC connectivity, so this is a no-op
            assertDoesNotThrow(() -> hvdcElement.applyToConnectivity(null));

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

    /**
     * A pure phase-shift tap change (only the phase angle alpha varies across taps; r, x and rho are constant) does not
     * modify the branch power, so it must NOT produce a Woodbury {@link ComputedTapPositionChangeElement}: its diagonal
     * term {@code 1 / (oldPower - newPower)} would be infinite and corrupt the alpha solve, which surfaced as NaN
     * function references in DC sensitivity analysis. The phase shift is instead fully handled through the target vector.
     * A tap change that does modify the branch impedance must still produce an element.
     */
    @Test
    void testPurePhaseShiftTapActionProducesNoWoodburyElement() {
        // PS1 steps have different reactances (x = 50, 100, 200): moving the tap changes the branch power, so the
        // action must produce a Woodbury element
        assertFalse(computedTapChangeElements(pstNetworkRef, 1, 0).isEmpty());

        // derive a pure phase shifter from PS1 by giving every tap step the same impedance (only alpha keeps varying)
        PhaseTapChanger ptc = pstNetwork.getTwoWindingsTransformer("PS1").getPhaseTapChanger();
        ptc.getStep(0).setX(100);
        ptc.getStep(2).setX(100);
        assertTrue(computedTapChangeElements(pstNetwork, 1, 0).isEmpty(),
                "a pure phase-shift tap action must not produce a Woodbury element");
    }

    private Map<LfAction, List<ComputedElement>> computedTapChangeElements(Network network, int fromTap, int toTap) {
        network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().setTapPosition(fromTap);
        LfTopoConfig topoConfig = new LfTopoConfig();
        topoConfig.addBranchIdWithPtcToRetain("PS1");
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), topoConfig, dcParameters.getNetworkParameters(), ReportNode.NO_OP).getFirst();
        try (DcLoadFlowContext context = new DcLoadFlowContext(lfNetwork, dcParameters)) {
            context.getParameters().getEquationSystemCreationParameters().setForcePhaseControlOffAndAddAngle1Var(true);
            new DcLoadFlowEngine(context).run();
            Map<String, LfAction> lfActions = LfActionUtils.createLfActions(lfNetwork,
                    Set.of(new PhaseTapChangerTapPositionAction("pst", "PS1", false, toTap)), network);
            return ComputedElement.createActionElementsIndexByLfAction(lfActions, context.getEquationSystem());
        }
    }
}
