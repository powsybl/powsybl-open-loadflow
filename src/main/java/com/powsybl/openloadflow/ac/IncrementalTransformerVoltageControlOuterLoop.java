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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
            branch.getVoltageControl().ifPresent(voltageControl -> {
                branch.setVoltageControlEnabled(false);
            });
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        return changeTapPositions(context.getNetwork(), context.getAcLoadFlowContext().getEquationSystem(), context.getAcLoadFlowContext().getJacobianMatrix());
    }

    private OuterLoopStatus changeTapPositions(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                               JacobianMatrix<AcVariableType, AcEquationType> j) {
        AtomicReference<OuterLoopStatus> status = new AtomicReference<>();
        status.set(OuterLoopStatus.STABLE);

        DenseMatrix sensitivities = getSensitivityValues(getControllerBranches(network), equationSystem, j);

        network.getBuses().stream().filter(LfBus::isTransformerVoltageControlled)
                .filter(bus -> bus.getTransformerVoltageControl().isPresent())
                .forEach(bus -> {
                    TransformerVoltageControl voltageControl = bus.getTransformerVoltageControl().get();
                    double targetV = voltageControl.getTargetValue();
                    double voltage = voltageControl.getControlled().getV();
                    double difference = targetV - voltage;
                    List<LfBranch> controllers = voltageControl.getControllers();
                    if (controllers.size() == 1) {
                        // only one transformer controls a bus
                        LfBranch controller = controllers.get(0);
                        if (controller.getTransformerVoltageControlTargetDeadband().isPresent()) {
                            if (difference > controller.getTransformerVoltageControlTargetDeadband().get()) {
                                double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                        .calculateSensi(sensitivities, getControllerBranches(network).indexOf(controller));
                                PiModel piModel = controller.getPiModel();
                                double deltaR = difference / sensitivity;
                                Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, MAX_INCREMENT);
                                if (result.getLeft()) {
                                    status.set(OuterLoopStatus.UNSTABLE);
                                }
                            }
                        }
                    } else {
                        // several transformers control the same bus.
                        boolean hasChanged = true;
                        while (hasChanged) {
                            hasChanged = false;
                            for (LfBranch controller : controllers) {
                                if (controller.getTransformerVoltageControlTargetDeadband().isPresent()) {
                                    if (difference > controller.getTransformerVoltageControlTargetDeadband().get()) {
                                        double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                                .calculateSensi(sensitivities, getControllerBranches(network).indexOf(controller));
                                        PiModel piModel = controller.getPiModel();
                                        double deltaR = difference / sensitivity;
                                        Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, 1);
                                        difference = difference - result.getRight() * sensitivity;
                                        if (result.getLeft()) {
                                            hasChanged = true;
                                            status.set(OuterLoopStatus.UNSTABLE);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
        return status.get();
    }

    DenseMatrix getSensitivityValues(List<LfBranch> controllerBranches, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
        for (int i = 0; i < controllerBranches.size(); i++) {
            LfBranch controllerBranch = controllerBranches.get(i);
            Variable<AcVariableType> rho1 = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_RHO1);
            int row = i;
            equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_RHO1).ifPresent(equation -> {
                var term = equation.getTerms().get(0);
                rhs.set(equation.getColumn(), row, term.der(rho1));
            });
        }
        j.solveTransposed(rhs);
        return rhs;
    }
}
