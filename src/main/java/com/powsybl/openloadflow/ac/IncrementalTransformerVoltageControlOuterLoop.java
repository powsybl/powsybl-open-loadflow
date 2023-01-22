/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    private static final int MAX_TAP_SHIFT = 3;
    private static final int MAX_DIRECTION_CHANGE = 2;
    private static final double MIN_TARGET_DEADBAND_KV = 0.1; // Kv

    private static final class ControllerContext {

        private final MutableInt directionChangeCount = new MutableInt();

        private PiModel.AllowedDirection allowedDirection = PiModel.AllowedDirection.BOTH;

        public MutableInt getDirectionChangeCount() {
            return directionChangeCount;
        }

        private PiModel.AllowedDirection getAllowedDirection() {
            return allowedDirection;
        }

        private void setAllowedDirection(PiModel.AllowedDirection allowedDirection) {
            this.allowedDirection = Objects.requireNonNull(allowedDirection);
        }
    }

    private static final class ContextData {

        private final Map<String, ControllerContext> controllersContexts = new HashMap<>();

        private Map<String, ControllerContext> getControllersContexts() {
            return controllersContexts;
        }
    }

    @Override
    public String getType() {
        return "Incremental transformer voltage control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        var contextData = new ContextData();
        context.setData(contextData);

        // All transformer voltage control are disabled as in this outer loop voltage adjustment is not
        // done into the equation system
        for (LfBranch branch : getControllerBranches(context.getNetwork())) {
            branch.getVoltageControl().ifPresent(voltageControl -> branch.setVoltageControlEnabled(false));
            contextData.getControllersContexts().put(branch.getId(), new ControllerContext());
        }
    }

    private static void updateAllowedDirection(ControllerContext controllerContext, PiModel.Direction direction) {
        if (controllerContext.getDirectionChangeCount().getValue() <= MAX_DIRECTION_CHANGE) {
            if (!controllerContext.getAllowedDirection().equals(direction.getAllowedDirection())) {
                // both vs increase or decrease
                // increase vs decrease
                // decrease vs increase
                controllerContext.getDirectionChangeCount().increment();
            }
            controllerContext.setAllowedDirection(direction.getAllowedDirection());
        }
    }

    static class SensitivityContext {

        private final DenseMatrix sensitivities;

        private final int[] controllerBranchIndex;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            controllerBranchIndex = createControllerBranchIndex(network, controllerBranches);
            sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, j);
        }

        private static int[] createControllerBranchIndex(LfNetwork network, List<LfBranch> controllerBranches) {
            int[] controllerBranchIndex = new int[network.getBranches().size()];
            for (int i = 0; i < controllerBranches.size(); i++) {
                LfBranch controllerBranch = controllerBranches.get(i);
                controllerBranchIndex[controllerBranch.getNum()] = i;
            }
            return controllerBranchIndex;
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
            for (LfBranch controllerBranch : controllerBranches) {
                equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], 1d));
            }
            j.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getCalculatedV(LfBus controlledBus) {
            return (EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV();
        }

        double calculateSensiRV(LfBranch controllerBranch, LfBus controlledBus) {
            return getCalculatedV(controlledBus)
                    .calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
        }
    }

    private static double getHalfTargetDeadband(TransformerVoltageControl voltageControl) {
        double minTargetDeadband = MIN_TARGET_DEADBAND_KV / voltageControl.getControlled().getNominalV();
        return voltageControl.getControllers().stream()
                .flatMap(controller -> controller.getTransformerVoltageControlTargetDeadband().stream())
                .filter(targetDeadband -> targetDeadband > minTargetDeadband)
                .min(Double::compareTo)
                .orElse(minTargetDeadband) / 2;
    }

    private boolean adjustWithOneController(LfBranch controllerBranch, LfBus controlledBus, ContextData contextData, SensitivityContext sensitivities,
                                            double diffV) {
        // only one transformer controls a bus
        var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
        double sensitivity = sensitivities.calculateSensiRV(controllerBranch, controlledBus);
        PiModel piModel = controllerBranch.getPiModel();
        int previousTapPosition = piModel.getTapPosition();
        double deltaR1 = diffV / sensitivity;
        return piModel.updateTapPositionR1(deltaR1, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).map(direction -> {
            updateAllowedDirection(controllerContext, direction);
            LOGGER.debug("Controller branch '{}' change tap from {} to {} (full range: {})", controllerBranch.getId(),
                    previousTapPosition, piModel.getTapPosition(), piModel.getTapPositionRange());
            return direction;
        }).isPresent();
    }

    private boolean adjustWithSeveralControllers(List<LfBranch> controllerBranches, LfBus controlledBus, ContextData contextData,
                                                 SensitivityContext sensitivityContext, double diffV, double halfTargetDeadband) {
        MutableBoolean adjusted = new MutableBoolean(false);

        List<Integer> previousTapPositions = controllerBranches.stream()
                .map(controllerBranch -> controllerBranch.getPiModel().getTapPosition())
                .collect(Collectors.toList());

        // several transformers control the same bus, to give to chance to all controllers to adjust controlled bus
        // voltage and to help distributing tap changes among all controllers, we try to adjust voltage by allowing
        // one tap change at a time for each controller
        MutableDouble remainingDiffV = new MutableDouble(diffV);
        MutableBoolean hasChanged = new MutableBoolean(true);
        while (hasChanged.booleanValue()) {
            hasChanged.setValue(false);
            for (LfBranch controllerBranch : controllerBranches) {
                if (Math.abs(remainingDiffV.getValue()) > halfTargetDeadband) {
                    var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
                    double sensitivity = sensitivityContext.calculateSensiRV(controllerBranch, controlledBus);
                    PiModel piModel = controllerBranch.getPiModel();
                    double previousR1 = piModel.getR1();
                    double deltaR1 = remainingDiffV.doubleValue() / sensitivity;
                    piModel.updateTapPositionR1(deltaR1, 1, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                        updateAllowedDirection(controllerContext, direction);
                        remainingDiffV.add(-(piModel.getR1() - previousR1) * sensitivity);
                        hasChanged.setValue(true);
                        adjusted.setValue(true);
                    });
                }
            }
        }

        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            PiModel piModel = controllerBranch.getPiModel();
            int previousTapPosition = previousTapPositions.get(i);
            if (piModel.getTapPosition() != previousTapPosition) {
                LOGGER.debug("Controller branch '{}' (controlled bus '{}') change tap from {} to {} (full range: {})",
                        controllerBranch.getId(), controlledBus.getId(), previousTapPosition,
                        piModel.getTapPosition(), piModel.getTapPositionRange());
            }
        }

        return adjusted.booleanValue();
    }

    private static double getDiffV(TransformerVoltageControl voltageControl) {
        double targetV = voltageControl.getTargetValue();
        double v = voltageControl.getControlled().getV();
        return targetV - v;
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getAcLoadFlowContext();
        var contextData = (ContextData) context.getData();

        List<LfBranch> controllerBranches = getControllerBranches(network);
        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerBranches,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        List<String> controlledBusesOutsideOfDeadband = new ArrayList<>();
        List<String> controlledBusesAdjusted = new ArrayList<>();

        List<LfBus> controlledBuses = network.getBuses().stream()
                .filter(LfBus::isTransformerVoltageControlled)
                .collect(Collectors.toList());

        controlledBuses.forEach(controlledBus -> {
            TransformerVoltageControl voltageControl = controlledBus.getTransformerVoltageControl().orElseThrow();
            double diffV = getDiffV(voltageControl);
            double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
            if (Math.abs(diffV) > halfTargetDeadband) {
                controlledBusesOutsideOfDeadband.add(controlledBus.getId());
                List<LfBranch> controllers = voltageControl.getControllers();
                LOGGER.trace("Controlled bus '{}' ({} controllers) is outside of its deadband (half is {} KV) and could need a voltage adjustment of {} KV",
                        controlledBus.getId(), controllers.size(), halfTargetDeadband * controlledBus.getNominalV(), diffV * controlledBus.getNominalV());
                boolean adjusted;
                if (controllers.size() == 1) {
                    adjusted = adjustWithOneController(controllers.get(0), controlledBus, contextData, sensitivityContext, diffV);
                } else {
                    adjusted = adjustWithSeveralControllers(controllers, controlledBus, contextData, sensitivityContext, diffV, halfTargetDeadband);
                }
                if (adjusted) {
                    controlledBusesAdjusted.add(controlledBus.getId());
                    status.setValue(OuterLoopStatus.UNSTABLE);
                }
            }
        });

        if (!controlledBusesOutsideOfDeadband.isEmpty() && LOGGER.isInfoEnabled()) {
            Map<String, Double> largestMismatches = controlledBuses.stream()
                    .map(controlledBus -> Pair.of(controlledBus.getId(), Math.abs(getDiffV(controlledBus.getTransformerVoltageControl().orElseThrow()) * controlledBus.getNominalV())))
                    .sorted((p1, p2) -> Double.compare(p2.getRight(), p1.getRight()))
                    .limit(3) // 3 largest
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (key1, key2) -> key1, LinkedHashMap::new));
            LOGGER.info("{} controlled bus voltages are outside of their target deadband, largest ones are: {}",
                    controlledBusesOutsideOfDeadband.size(), largestMismatches);
        }

        if (!controlledBusesAdjusted.isEmpty()) {
            LOGGER.info("{} controlled bus voltages have been adjusted by changing at least one tap",
                    controlledBusesAdjusted.size());
        }

        return status.getValue();
    }
}
