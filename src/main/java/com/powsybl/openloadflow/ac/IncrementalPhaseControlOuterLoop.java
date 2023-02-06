/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class IncrementalPhaseControlOuterLoop extends AbstractPhaseControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalPhaseControlOuterLoop.class);

    private static final int MAX_DIRECTION_CHANGE = 2;
    private static final int MAX_TAP_SHIFT = Integer.MAX_VALUE;

    @Override
    public String getType() {
        return "Incremental phase control";
    }

    @Override
    public void initialize(OuterLoopContext context) {
        var contextData = new IncrementalContextData();
        context.setData(contextData);

        List<LfBranch> controllerBranches = getControllerBranches(context.getNetwork());
        for (LfBranch controllerBranch : controllerBranches) {
            contextData.getControllersContexts().put(controllerBranch.getId(), new IncrementalContextData.ControllerContext(MAX_DIRECTION_CHANGE));
        }

        fixPhaseShifterNecessaryForConnectivity(context.getNetwork(), controllerBranches);
    }

    static class SensitivityContext {

        private final EquationSystem<AcVariableType, AcEquationType> equationSystem;

        private final DenseMatrix sensitivities;

        private final int[] controllerBranchIndex;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                  JacobianMatrix<AcVariableType, AcEquationType> j) {
            this.equationSystem = Objects.requireNonNull(equationSystem);
            controllerBranchIndex = LfBranch.createIndex(network, controllerBranches);
            sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, j);
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<AcVariableType, AcEquationType> equationSystem,
                                                              JacobianMatrix<AcVariableType, AcEquationType> j) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
            for (LfBranch controllerBranch : controllerBranches) {
                equationSystem.getEquation(controllerBranch.getNum(), AcEquationType.BRANCH_TARGET_ALPHA1)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], Math.toRadians(1d)));
            }
            j.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getI1(LfBranch controlledBranch) {
            return (EquationTerm<AcVariableType, AcEquationType>) controlledBranch.getI1();
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<AcVariableType, AcEquationType> getI2(LfBranch controlledBranch) {
            return (EquationTerm<AcVariableType, AcEquationType>) controlledBranch.getI2();
        }

        double calculateSensitivityFromA2I(LfBranch controllerBranch, LfBranch controlledBranch, EquationTerm<AcVariableType, AcEquationType> i) {
            double sensi = i.calculateSensi(sensitivities, controllerBranchIndex[controllerBranch.getNum()]);
            if (controllerBranch == controlledBranch) {
                var a1Var = equationSystem.getVariable(controllerBranch.getNum(), AcVariableType.BRANCH_ALPHA1);
                sensi += Math.toRadians(i.der(a1Var));
            }
            return sensi;
        }

        double calculateSensitivityFromA2I1(LfBranch controllerBranch, LfBranch controlledBranch) {
            return calculateSensitivityFromA2I(controllerBranch, controlledBranch, getI1(controlledBranch));
        }

        double calculateSensitivityFromA2I2(LfBranch controllerBranch, LfBranch controlledBranch) {
            return calculateSensitivityFromA2I(controllerBranch, controlledBranch, getI2(controlledBranch));
        }
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        final MutableObject<OuterLoopStatus> status = new MutableObject<>(OuterLoopStatus.STABLE);

        var contextData = (IncrementalContextData) context.getData();

        LfNetwork network = context.getNetwork();

        List<LfBranch> controllerBranches = getControllerBranches(network);

        // find list of phase controls that are in current limiter
        List<DiscretePhaseControl> currentLimiterPhaseControls = controllerBranches.stream()
                .flatMap(branch -> branch.getDiscretePhaseControl().stream())
                .filter(phaseControl -> phaseControl.getMode() == DiscretePhaseControl.Mode.LIMITER)
                .collect(Collectors.toList());

        var sensitivityContext = new SensitivityContext(network,
                                                        controllerBranches,
                                                        context.getAcLoadFlowContext().getEquationSystem(),
                                                        context.getAcLoadFlowContext().getJacobianMatrix());

        for (DiscretePhaseControl phaseControl : currentLimiterPhaseControls) {
            LfBranch controllerBranch = phaseControl.getController();
            LfBranch controlledBranch = phaseControl.getControlled();
            double sensiA2I = sensitivityContext.calculateSensitivityFromA2I1(controllerBranch, controlledBranch);
            double i1 = controllerBranch.getI1().eval();
            if (i1 > phaseControl.getTargetValue()) {
                var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
                double ib = PerUnit.ib(controllerBranch.getBus1().getNominalV());
                double di = phaseControl.getTargetValue() - i1;
                double da = Math.toRadians(di / sensiA2I);
                LOGGER.trace("Controlled branch '{}' current is {} A and above target value {} A, a phase shift of {} ° is required",
                        controlledBranch.getId(), i1 * ib, phaseControl.getTargetValue() * ib, Math.toDegrees(da));
                PiModel piModel = controllerBranch.getPiModel();

                int oldTapPosition = piModel.getTapPosition();
                Range<Integer> tapPositionRange = piModel.getTapPositionRange();
                piModel.updateTapPositionToReachNewA1(da, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresentOrElse(direction -> {
                    controllerContext.updateAllowedDirection(direction);
                    status.setValue(OuterLoopStatus.UNSTABLE);
                }, () -> {
                        // as we are above target value, we have to try to move at least to one tap position
                        // even if current tap position is the closest one to the target value (but still above)
                        if (controllerContext.getAllowedDirection() == AllowedDirection.DECREASE
                                && piModel.getTapPosition() > tapPositionRange.getMinimum()) {
                            piModel.setTapPosition(piModel.getTapPosition() - 1);
                            status.setValue(OuterLoopStatus.UNSTABLE);
                        } else if (controllerContext.getAllowedDirection() == AllowedDirection.INCREASE
                                && piModel.getTapPosition() < tapPositionRange.getMaximum()) {
                            piModel.setTapPosition(piModel.getTapPosition() + 1);
                            status.setValue(OuterLoopStatus.UNSTABLE);
                        }
                    });

                if (piModel.getTapPosition() != oldTapPosition) {
                    LOGGER.debug("Controller branch '{}' change tap from {} to {} (full range: {})", controllerBranch.getId(),
                            oldTapPosition, piModel.getTapPosition(), tapPositionRange);
                }
            }
        }

        return status.getValue();
    }
}
