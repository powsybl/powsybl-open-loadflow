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

    public static final double EPS = Math.pow(10, -2);

    private static final int MAXSWITCH = 100;

    // Wether the outer loop is stable or not.
    // It is unstable if the tap position of at least one transfomer has changed
    private boolean unstable = false;

    // In case of several transformers controling the same bus voltage, indicates if one of this transformer has changed tap position in current iteration of the do-while loop.
    private boolean haschanged = false;

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
        unstable = false;
        OuterLoopStatus status =  changeTapPositions(controllerBranches, context.getAcLoadFlowContext().getEquationSystem(), context.getAcLoadFlowContext().getJacobianMatrix());
        return status;
    }

    private OuterLoopStatus changeTapPositions(List<LfBranch> controllerBranches, EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                               JacobianMatrix<AcVariableType, AcEquationType> j) {
        DenseMatrix sensitivities = getSensitivityValues(controllerBranches, equationSystem, j);

        // fill map of controlled buses
        LinkedHashMap<LfBus, List<LfBranch>> controlledBuses = new LinkedHashMap<LfBus, List<LfBranch>>();
        for (LfBranch controllerBranch : controllerBranches) {
            Optional<TransformerVoltageControl> transformerVoltageControl = controllerBranch.getVoltageControl();
            if (transformerVoltageControl.isPresent()) {
                LfBus controlledBus = transformerVoltageControl.get().getControlled();
                if (controlledBuses.containsKey(controlledBus)) {
                    controlledBuses.get(controlledBus).add(controllerBranch);
                } else {
                    List<LfBranch> controllerBrchs = new ArrayList<LfBranch>();
                    controllerBrchs.add(controllerBranch);
                    controlledBuses.put(transformerVoltageControl.get().getControlled(), controllerBrchs);
                }
            }
        }

        controlledBuses.forEach((controlledBus, controllerBrchs) -> {
            LfBranch controllerBranch0 = controllerBrchs.get(0);
            Optional<TransformerVoltageControl> transformerVoltageControl = controllerBranch0.getVoltageControl();
            double targetV = transformerVoltageControl.get().getTargetValue();
            double voltage = transformerVoltageControl.get().getControlled().getV();
            double difference = targetV - voltage;
            if (controllerBrchs.size() == 1) {
                // One Transformer controls the bus voltage
                double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) transformerVoltageControl.get().getControlled().getCalculatedV())
                        .calculateSensi(sensitivities, controllerBranches.indexOf(controllerBranch0));
                PiModel piModel = controllerBranch0.getPiModel();
                double deltaR = difference / sensitivity;
                Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, MAXSWITCH);
                if (result.getLeft()) {
                    unstable = true;
                }
            } else {
                // Several transformers control the same bus voltage
                do {
                    haschanged = false;
                    for (LfBranch lfBranch : controllerBrchs) {
                        double sensitivity = ((EquationTerm<AcVariableType, AcEquationType>) transformerVoltageControl.get().getControlled().getCalculatedV())
                                .calculateSensi(sensitivities, controllerBranches.indexOf(lfBranch));
                        PiModel piModel = lfBranch.getPiModel();
                        double deltaR = difference / sensitivity;
                        Pair<Boolean, Double> result = piModel.updateTapPositionR(deltaR, 1);
                        difference = difference - result.getRight() * sensitivity;
                        if (result.getLeft()) {
                            haschanged = true;
                            unstable = true;
                        }
                    }
                } while (haschanged);
            }
        });

        return unstable ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
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
