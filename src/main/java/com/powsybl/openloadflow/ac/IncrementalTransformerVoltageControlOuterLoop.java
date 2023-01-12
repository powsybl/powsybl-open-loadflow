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
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    private static final int MAX_TAP_SHIFT = 3;
    private static final int MAX_DIRECTION_CHANGE = 2;

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

        // All transformer voltage control are disabled for the first equation system resolution.
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

    private void adjustWithOneController(LfBranch controllerBranch, LfBus controlledBus, ContextData contextData, int[] controllerBranchIndex,
                                         DenseMatrix sensitivities, double diffV, MutableObject<OuterLoopStatus> status) {
        // only one transformer controls a bus
        var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
        Double targetDeadband = controllerBranch.getTransformerVoltageControlTargetDeadband().orElse(null);
        if (checkTargetDeadband(targetDeadband, diffV)) {
            double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV())
                    .calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
            double previousR1 = controllerBranch.getPiModel().getR1();
            double deltaR1 = diffV / sensitivity;
            controllerBranch.getPiModel().updateTapPositionR1(deltaR1, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                updateAllowedDirection(controllerContext, direction);
                LOGGER.debug("Increment voltage ratio of '{}': {} -> {}", controllerBranch.getId(), previousR1, controllerBranch.getPiModel().getR1());
                status.setValue(OuterLoopStatus.UNSTABLE);
            });
        } else {
            LOGGER.trace("Controller branch '{}' is in its deadband: deadband {} vs voltage difference {}", controllerBranch.getId(), targetDeadband, Math.abs(diffV));
        }
    }

    private void adjustWithSeveralControllers(List<LfBranch> controllerBranches, LfBus controlledBus, ContextData contextData, int[] controllerBranchIndex,
                                              DenseMatrix sensitivities, double diffV, MutableObject<OuterLoopStatus> status) {
        // several transformers control the same bus
        double remainingDiffV = diffV;
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = false;
            for (LfBranch controllerBranch : controllerBranches) {
                var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
                Double targetDeadband = controllerBranch.getTransformerVoltageControlTargetDeadband().orElse(null);
                if (checkTargetDeadband(targetDeadband, remainingDiffV)) {
                    double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) controlledBus.getCalculatedV())
                            .calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
                    double previousR1 = controllerBranch.getPiModel().getR1();
                    double deltaR1 = remainingDiffV / sensitivity;
                    PiModel.Direction direction = controllerBranch.getPiModel().updateTapPositionR1(deltaR1, 1, controllerContext.getAllowedDirection()).orElse(null);
                    if (direction != null) {
                        updateAllowedDirection(controllerContext, direction);
                        remainingDiffV -= (controllerBranch.getPiModel().getR1() - previousR1) * sensitivity;
                        hasChanged = true;
                        status.setValue(OuterLoopStatus.UNSTABLE);
                        LOGGER.debug("[Shared control] increment voltage ratio of '{}': {} -> {}", controllerBranch.getId(), previousR1, controllerBranch.getPiModel().getR1());
                    }
                } else {
                    LOGGER.trace("Controller branch '{}' is in its deadband: deadband {} vs voltage difference {}", controllerBranch.getId(), targetDeadband, Math.abs(diffV));
                }
            }
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        LfNetwork network = context.getNetwork();
        List<LfBranch> controllerBranches = getControllerBranches(network);
        int[] controllerBranchIndex = new int[network.getBranches().size()];
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            controllerBranchIndex[controllerBranch.getNum()] = i;
        }

        DenseMatrix sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex,
                                                               context.getAcLoadFlowContext().getEquationSystem(),
                                                               context.getAcLoadFlowContext().getJacobianMatrix());

        var contextData = (ContextData) context.getData();

        network.getBuses().stream()
                .filter(LfBus::isTransformerVoltageControlled)
                .forEach(controlledBus -> {
                    TransformerVoltageControl voltageControl = controlledBus.getTransformerVoltageControl().orElseThrow();
                    double targetV = voltageControl.getTargetValue();
                    double v = voltageControl.getControlled().getV();
                    double diffV = targetV - v;
                    List<LfBranch> controllers = voltageControl.getControllers();
                    if (controllers.size() == 1) {
                        adjustWithOneController(controllers.get(0), controlledBus, contextData, controllerBranchIndex, sensitivities, diffV, status);
                    } else {
                        adjustWithSeveralControllers(controllers, controlledBus, contextData, controllerBranchIndex, sensitivities, diffV, status);
                    }
                });
        return status.getValue();
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
}
