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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Anne Tilloy <anne.tilloy at rte-france.com>
 */
public class IncrementalTransformerVoltageControlOuterLoop extends AbstractTransformerVoltageControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalTransformerVoltageControlOuterLoop.class);

    public static final double EPS = Math.pow(10, -2);

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
        OuterLoopStatus status =  changeTapPositions(controllerBranches, context.getAcLoadFlowContext().getEquationSystem(), context.getAcLoadFlowContext().getJacobianMatrix());
        return status;
    }

    private OuterLoopStatus changeTapPositions(List<LfBranch> controllerBranches, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                               JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix sensitivities = getSensitivityValues(controllerBranches, equationSystem, j);
        sensitivities.print(System.out);
        boolean success = false;
        for (LfBranch controllerBranch : controllerBranches) {
            Optional<TransformerVoltageControl> transformerVoltageControl = controllerBranch.getVoltageControl();
            if (transformerVoltageControl.isPresent()) {
                double targetV = transformerVoltageControl.get().getTargetValue();
                double voltage = transformerVoltageControl.get().getControlled().getV();
                double difference = targetV - voltage;
                double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) transformerVoltageControl.get().getControlled().getCalculatedV())
                        .calculateSensi(sensitivities, controllerBranches.indexOf(controllerBranch));
                System.out.println(sensitivity);
                PiModel piModel = controllerBranch.getPiModel();
                if (difference > 0) {
                    // we need to increase the voltage at controlled bus.
                    success = sensitivity > 0 ? piModel.increaseTapPosition() : piModel.decreaseTapPosition();
                } else if (difference < 0) {
                    // we need to decrease the voltage at controlled bus.
                    success = sensitivity > 0 ? piModel.decreaseTapPosition() : piModel.increaseTapPosition();
                }
            }
        }
        return success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
    }

    DenseMatrix getSensitivityValues(List<LfBranch> controllerBranches, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), controllerBranches.size());
        for (LfBranch branch : controllerBranches) {
            Variable var = equationSystem.getVariable(branch.getNum(), AcVariableType.BRANCH_RHO1);
            for (Equation<AcVariableType, AcEquationType> equation : equationSystem.getSortedEquationsToSolve().keySet()) {
                equation.getTerms().stream().filter(term -> term.getVariables().stream().filter(v -> v.equals(var)).findAny().isPresent()).forEach(t -> {
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
