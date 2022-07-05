/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.network.*;
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

    private static final int MAX_TAP_SHIFT = 5;

    private static final class ControllerContext {

        private PiModel.AllowedDirection allowedDirection = PiModel.AllowedDirection.BOTH;

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
                .forEach(bus -> {
                    TransformerVoltageControl voltageControl = bus.getTransformerVoltageControl().orElseThrow();
                    double targetV = voltageControl.getTargetValue();
                    double voltage = voltageControl.getControlled().getV();
                    double difference = targetV - voltage;
                    List<LfBranch> controllers = voltageControl.getControllers();
                    if (controllers.size() == 1) {
                        // only one transformer controls a bus
                        LfBranch controller = controllers.get(0);
                        var controllerContext = contextData.getControllersContexts().get(controller.getId());
                        Double targetDeadband = controller.getTransformerVoltageControlTargetDeadband().orElse(null);
                        if (targetDeadband != null && Math.abs(difference) > targetDeadband / 2) {
                            double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                    .calculateSensi(sensitivities, controllerBranchIndex[controller.getNum()]);
                            double previousR1 = controller.getPiModel().getR1();
                            double deltaR1 = difference / sensitivity;
                            controller.getPiModel().updateTapPositionR1(deltaR1, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                                controllerContext.setAllowedDirection(direction.getAllowedDirection());
                                LOGGER.info("Round voltage ratio of '{}': {} -> {}", controller.getId(), previousR1, controller.getPiModel().getR1());
                                status.setValue(OuterLoopStatus.UNSTABLE);
                            });
                        } else {
                            LOGGER.info("Controller branch '{}' is in its deadband: deadband {} vs voltage difference {}", controller.getId(), targetDeadband, Math.abs(difference));
                        }
                    } else {
                        // several transformers control the same bus.
                        boolean hasChanged = true;
                        while (hasChanged) {
                            hasChanged = false;
                            for (LfBranch controller : controllers) {
                                var controllerContext = contextData.getControllersContexts().get(controller.getId());
                                Double targetDeadband = controller.getTransformerVoltageControlTargetDeadband().orElse(null);
                                if (targetDeadband != null && Math.abs(difference) > targetDeadband / 2) {
                                    double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                            .calculateSensi(sensitivities, controllerBranchIndex[controller.getNum()]);
                                    double previousR1 = controller.getPiModel().getR1();
                                    double deltaR1 = difference / sensitivity;
                                    PiModel.Direction direction = controller.getPiModel().updateTapPositionR1(deltaR1, 1, controllerContext.getAllowedDirection()).orElse(null);
                                    if (direction != null) {
                                        controllerContext.setAllowedDirection(direction.getAllowedDirection());
                                        difference -= (controller.getPiModel().getR1() - previousR1) * sensitivity;
                                        hasChanged = true;
                                        status.setValue(OuterLoopStatus.UNSTABLE);
                                        LOGGER.info("[Shared control] round voltage ratio of '{}': {} -> {}", controller.getId(), previousR1, controller.getPiModel().getR1());
                                    }
                                } else {
                                    LOGGER.info("Controller branch '{}' is in its deadband: deadband {} vs voltage difference {}", controller.getId(), targetDeadband, Math.abs(difference));
                                }
                            }
                        }
                    }
                });
        return status.getValue();
    }

    private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                          EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                          JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
        for (LfBranch controllerBranch : controllerBranches) {
            Variable<AcVariableType> rho1 = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1);
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1).ifPresent(equation -> {
                var term = equation.getTerms().get(0);
                rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], term.der(rho1));
            });
        }
        j.solveTransposed(rhs);
        return rhs;
    }
}
