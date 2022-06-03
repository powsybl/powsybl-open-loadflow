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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    private static final int MAX_INCREMENT = 100;

    @Override
    public String getType() {
        return "Incremental transformer voltage control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        // All transformer voltage control are disabled for the first equation system resolution.
        for (LfBranch branch : getControllerBranches(context.getNetwork())) {
            branch.getVoltageControl().ifPresent(voltageControl -> branch.setVoltageControlEnabled(false));
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
                        Double targetDeadband = controller.getTransformerVoltageControlTargetDeadband().orElse(null);
                        if (targetDeadband != null && difference > targetDeadband) {
                            double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                    .calculateSensi(sensitivities, controllerBranchIndex[controller.getNum()]);
                            double deltaR = difference / sensitivity;
                            Pair<Boolean, Double> result = controller.getPiModel().updateTapPositionR(deltaR, MAX_INCREMENT);
                            if (result.getLeft()) {
                                status.setValue(OuterLoopStatus.UNSTABLE);
                            }
                        }
                    } else {
                        // several transformers control the same bus.
                        boolean hasChanged = true;
                        while (hasChanged) {
                            hasChanged = false;
                            for (LfBranch controller : controllers) {
                                Double targetDeadband = controller.getTransformerVoltageControlTargetDeadband().orElse(null);
                                if (targetDeadband != null && difference > targetDeadband) {
                                    double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                            .calculateSensi(sensitivities, controllerBranchIndex[controller.getNum()]);
                                    double deltaR = difference / sensitivity;
                                    Pair<Boolean, Double> result = controller.getPiModel().updateTapPositionR(deltaR, 1);
                                    difference -= result.getRight() * sensitivity;
                                    if (result.getLeft()) {
                                        hasChanged = true;
                                        status.setValue(OuterLoopStatus.UNSTABLE);
                                    }
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
