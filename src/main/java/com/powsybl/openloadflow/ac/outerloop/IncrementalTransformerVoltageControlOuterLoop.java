/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.lf.outerloop.IncrementalContextData;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcOuterLoopContext;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    public static final String NAME = "IncrementalTransformerVoltageControl";

    public static final int DEFAULT_MAX_TAP_SHIFT = 3;

    private static final int MAX_DIRECTION_CHANGE = 2;

    private final int maxTapShift;

    public IncrementalTransformerVoltageControlOuterLoop(int maxTapShift) {
        this.maxTapShift = maxTapShift;
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static List<LfBus> getControlledBusesOutOfDeadband(IncrementalContextData contextData) {
        return IncrementalContextData.getControlledBuses(contextData.getCandidateControlledBuses(), VoltageControl.Type.TRANSFORMER).stream()
                .filter(bus -> isOutOfDeadband(bus.getTransformerVoltageControl().orElseThrow()))
                .collect(Collectors.toList());
    }

    public static List<LfBranch> getControllerElementsOutOfDeadband(List<LfBus> controlledBusesOutOfDeadband) {
        return controlledBusesOutOfDeadband.stream()
                .flatMap(bus -> bus.getTransformerVoltageControl().orElseThrow().getMergedControllerElements().stream())
                .filter(Predicate.not(LfBranch::isDisabled))
                .collect(Collectors.toList());
    }

    public static List<LfBranch> getControllerElements(IncrementalContextData contextData) {
        return IncrementalContextData.getControllerElements(contextData.getCandidateControlledBuses(), VoltageControl.Type.TRANSFORMER);
    }

    @Override
    public void initialize(AcOuterLoopContext context) {
        var contextData = new IncrementalContextData(context.getNetwork(), VoltageControl.Type.TRANSFORMER);
        context.setData(contextData);

        // All transformer voltage control are disabled as in this outer loop voltage adjustment is not
        // done into the equation system
        for (LfBranch branch : getControllerElements(contextData)) {
            branch.getVoltageControl().ifPresent(voltageControl -> branch.setVoltageControlEnabled(false));
            contextData.getControllersContexts().put(branch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }
    }

    static class SensitivityContext {

        private final DenseMatrix sensitivities;

        private final int[] controllerBranchIndex;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            controllerBranchIndex = LfBranch.createIndex(network, controllerBranches);
            sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, j);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            int nRows = equationSystem.getIndex().getSortedEquationsToSolve().size();
            int nColumns = controllerBranches.size();

            DenseMatrix rhs = new DenseMatrix(nRows, nColumns);
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

        double calculateSensitivityFromRToV(LfBranch controllerBranch, LfBus controlledBus) {
            return getCalculatedV(controlledBus)
                    .calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
        }
    }

    private boolean adjustWithOneController(LfBranch controllerBranch, LfBus controlledBus, IncrementalContextData contextData, SensitivityContext sensitivities,
                                            double diffV, List<String> controlledBusesWithAllItsControllersToLimit) {
        // only one transformer controls a bus
        var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
        double sensitivity = sensitivities.calculateSensitivityFromRToV(controllerBranch, controlledBus);
        PiModel piModel = controllerBranch.getPiModel();
        int previousTapPosition = piModel.getTapPosition();
        double deltaR1 = diffV / sensitivity;
        return piModel.updateTapPositionToReachNewR1(deltaR1, maxTapShift, controllerContext.getAllowedDirection()).map(direction -> {
            controllerContext.updateAllowedDirection(direction);
            Range<Integer> tapPositionRange = piModel.getTapPositionRange();
            LOGGER.debug("Controller branch '{}' change tap from {} to {} (full range: {})", controllerBranch.getId(),
                    previousTapPosition, piModel.getTapPosition(), tapPositionRange);
            if (piModel.getTapPosition() == tapPositionRange.getMinimum()
                    || piModel.getTapPosition() == tapPositionRange.getMaximum()) {
                controlledBusesWithAllItsControllersToLimit.add(controlledBus.getId());
            }
            return direction;
        }).isPresent();
    }

    private boolean adjustWithSeveralControllers(List<LfBranch> controllerBranches, LfBus controlledBus, IncrementalContextData contextData,
                                                 SensitivityContext sensitivityContext, double diffV, double halfTargetDeadband,
                                                 List<String> controlledBusesWithAllItsControllersToLimit) {
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
                    double sensitivity = sensitivityContext.calculateSensitivityFromRToV(controllerBranch, controlledBus);
                    PiModel piModel = controllerBranch.getPiModel();
                    double previousR1 = piModel.getR1();
                    double deltaR1 = remainingDiffV.doubleValue() / sensitivity;
                    piModel.updateTapPositionToReachNewR1(deltaR1, 1, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                        controllerContext.updateAllowedDirection(direction);
                        remainingDiffV.add(-(piModel.getR1() - previousR1) * sensitivity);
                        hasChanged.setValue(true);
                        adjusted.setValue(true);
                    });
                }
            }
        }

        boolean allControllersToLimit = true;
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            PiModel piModel = controllerBranch.getPiModel();
            int previousTapPosition = previousTapPositions.get(i);
            Range<Integer> tapPositionRange = piModel.getTapPositionRange();
            if (piModel.getTapPosition() != previousTapPosition) {
                LOGGER.debug("Controller branch '{}' (controlled bus '{}') change tap from {} to {} (full range: {})",
                        controllerBranch.getId(), controlledBus.getId(), previousTapPosition,
                        piModel.getTapPosition(), tapPositionRange);
            }
            if (piModel.getTapPosition() != tapPositionRange.getMinimum()
                    && piModel.getTapPosition() != tapPositionRange.getMaximum()) {
                allControllersToLimit = false;
            }
        }
        if (allControllersToLimit) {
            controlledBusesWithAllItsControllersToLimit.add(controlledBus.getId());
        }

        return adjusted.booleanValue();
    }

    private static double getDiffV(TransformerVoltageControl voltageControl) {
        double targetV = voltageControl.getTargetValue();
        double v = voltageControl.getControlledBus().getV();
        return targetV - v;
    }

    private static boolean isOutOfDeadband(TransformerVoltageControl voltageControl) {
        double diffV = getDiffV(voltageControl);
        double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
        boolean outOfDeadband = Math.abs(diffV) > halfTargetDeadband;
        if (outOfDeadband) {
            List<LfBranch> controllers = voltageControl.getMergedControllerElements().stream()
                    .filter(b -> !b.isDisabled())
                    .collect(Collectors.toList());
            LOGGER.trace("Controlled bus '{}' ({} controllers) is outside of its deadband (half is {} kV) and could need a voltage adjustment of {} kV",
                    voltageControl.getControlledBus().getId(), controllers.size(), halfTargetDeadband * voltageControl.getControlledBus().getNominalV(),
                    diffV * voltageControl.getControlledBus().getNominalV());
        }
        return outOfDeadband;
    }

    @Override
    public OuterLoopStatus check(AcOuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        AcLoadFlowContext loadFlowContext = context.getLoadFlowContext();
        var contextData = (IncrementalContextData) context.getData();

        // filter out buses/branches which are outside their deadbands
        List<LfBus> controlledBusesOutsideOfDeadband = getControlledBusesOutOfDeadband(contextData);
        List<LfBranch> controllerBranchesOutsideOfDeadband = getControllerElementsOutOfDeadband(controlledBusesOutsideOfDeadband);

        // all branches are within their deadbands
        if (controllerBranchesOutsideOfDeadband.isEmpty()) {
            return status.getValue();
        }

        SensitivityContext sensitivityContext = new SensitivityContext(network, controllerBranchesOutsideOfDeadband,
                loadFlowContext.getEquationSystem(), loadFlowContext.getJacobianMatrix());

        // for synthetics logs
        List<String> controlledBusesAdjusted = new ArrayList<>();
        List<String> controlledBusesWithAllItsControllersToLimit = new ArrayList<>();

        controlledBusesOutsideOfDeadband.forEach(controlledBus -> {
            TransformerVoltageControl voltageControl = controlledBus.getTransformerVoltageControl().orElseThrow();
            double diffV = getDiffV(voltageControl);
            double halfTargetDeadband = getHalfTargetDeadband(voltageControl);
            List<LfBranch> controllers = voltageControl.getMergedControllerElements().stream()
                    .filter(b -> !b.isDisabled())
                    .collect(Collectors.toList());
            boolean adjusted;
            if (controllers.size() == 1) {
                adjusted = adjustWithOneController(controllers.get(0), controlledBus, contextData, sensitivityContext, diffV, controlledBusesWithAllItsControllersToLimit);
            } else {
                adjusted = adjustWithSeveralControllers(controllers, controlledBus, contextData, sensitivityContext, diffV, halfTargetDeadband, controlledBusesWithAllItsControllersToLimit);
            }
            if (adjusted) {
                controlledBusesAdjusted.add(controlledBus.getId());
                status.setValue(OuterLoopStatus.UNSTABLE);
            }
        });

        if (!controlledBusesOutsideOfDeadband.isEmpty() && LOGGER.isInfoEnabled()) {
            Map<String, Double> largestMismatches = controlledBusesOutsideOfDeadband.stream()
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
        if (!controlledBusesWithAllItsControllersToLimit.isEmpty()) {
            LOGGER.info("{} controlled buses have all its controllers to a tap limit: {}",
                    controlledBusesWithAllItsControllersToLimit.size(), controlledBusesWithAllItsControllersToLimit);
        }

        return status.getValue();
    }
}
