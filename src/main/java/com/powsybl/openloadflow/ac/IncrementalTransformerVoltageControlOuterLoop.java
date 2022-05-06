/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    private static final int MAX_INCREMENT = 100;

    List<LfBranch> controllerBranches = new ArrayList<>();

    @Override
    public String getType() {
        return "Incremental transformer voltage control";
    }

    @Override
    public void initialize(LfNetwork network) {
        // All transformer voltage control are disabled for the first equation system resolution.
        for (LfBranch branch : network.getBranches()) {
            branch.getVoltageControl().ifPresent(voltageControl -> {
                branch.setVoltageControlEnabled(false);
                controllerBranches.add(branch);
            });
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        return changeTapPositions(context.getNetwork(), context.getAcLoadFlowContext().getEquationSystem(), context.getAcLoadFlowContext().getJacobianMatrix());
    }

    private OuterLoopStatus changeTapPositions(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                               JacobianMatrix<AcVariableType, AcEquationType> j) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        DenseMatrix sensitivities = getSensitivityValues(controllerBranches, equationSystem, j);

        for (LfBus bus : network.getBuses()) {
            if (bus.isTransformerVoltageControlled()) {
                if (bus.getTransformerVoltageControl().isPresent()) {
                    TransformerVoltageControl voltageControl = bus.getTransformerVoltageControl().get();
                    double targetV = voltageControl.getTargetValue();
                    double voltage = voltageControl.getControlled().getV();
                    double difference = targetV - voltage;
                    List<LfBranch> controllers = voltageControl.getControllers();
                    if (controllers.size() == 1) {
                        double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) bus.getCalculatedV())
                                .calculateSensi(sensitivities, controllers.indexOf(controllers.get(0)));
                        PiModel piModel = controllers.get(0).getPiModel();
                        double deltaR = difference / sensitivity;
                        Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, MAX_INCREMENT);
                        if (result.getLeft()) {
                            status = OuterLoopStatus.UNSTABLE;
                        }
                    } else {
                        // several transformers control the same bus.
                        boolean hasChanged = true;
                        while (hasChanged) {
                            for (LfBranch controller : controllers) {
                                double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) voltageControl.getControlled().getCalculatedV())
                                        .calculateSensi(sensitivities, controllerBranches.indexOf(controller));
                                PiModel piModel = controller.getPiModel();
                                double deltaR = difference / sensitivity;
                                Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, 1);
                                difference = difference - result.getRight() * sensitivity;
                                if (result.getLeft()) {
                                    hasChanged = true;
                                    status = OuterLoopStatus.UNSTABLE;
                                } else {
                                    hasChanged = false;
                                }
                            }
                        }
                    }
                }
            }
        }

        return status;
    }

    DenseMatrix getSensitivityValues(List<LfBranch> controllerBranches, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), controllerBranches.size());
        for (LfBranch branch : controllerBranches) {
            Variable var = equationSystem.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1);
            for (Equation<AcVariableType, AcEquationType> equation : equationSystem.getSortedEquationsToSolve().keySet()) {
                equation.getTerms().stream().filter(term -> term.getVariables().stream().anyMatch(v -> v.equals(var))).forEach(t -> {
                    if (equation.getType().equals(AcEquationType.BRANCH_TARGET_RHO1)) {
                        rhs.set(equation.getColumn(), controllerBranches.indexOf(branch), t.der(var));
                    }
                });
            }
        }
        j.solveTransposed(rhs);
        return rhs;
    }
}
