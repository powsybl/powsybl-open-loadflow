/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.IncrementalContextData;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.lf.outerloop.AbstractIncrementalPhaseControlOuterLoop;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.PerUnit;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcIncrementalPhaseControlOuterLoop
        extends AbstractIncrementalPhaseControlOuterLoop<DcVariableType, DcEquationType, DcLoadFlowParameters, DcLoadFlowContext, DcOuterLoopContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcIncrementalPhaseControlOuterLoop.class);

    @Override
    public String getType() {
        return "DC Incremental phase control";
    }

    public static class DcSensitivityContext {

        private final List<LfBranch> controllerBranches;

        private final EquationSystem<DcVariableType, DcEquationType> equationSystem;

        private final JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix;

        private final int[] controllerBranchIndex;

        private DenseMatrix sensitivities;

        public DcSensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
                                  EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                  JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix) {
            this.controllerBranches = Objects.requireNonNull(controllerBranches);
            this.equationSystem = Objects.requireNonNull(equationSystem);
            this.jacobianMatrix = Objects.requireNonNull(jacobianMatrix);
            controllerBranchIndex = LfBranch.createIndex(network, controllerBranches);
        }

        private DenseMatrix getSensitivities() {
            if (sensitivities == null) {
                sensitivities = calculateSensitivityValues(controllerBranches, controllerBranchIndex, equationSystem, jacobianMatrix);
            }
            return sensitivities;
        }

        private static DenseMatrix calculateSensitivityValues(List<LfBranch> controllerBranches, int[] controllerBranchIndex,
                                                              EquationSystem<DcVariableType, DcEquationType> equationSystem,
                                                              JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix) {
            DenseMatrix rhs = new DenseMatrix(equationSystem.getIndex().getSortedEquationsToSolve().size(), controllerBranches.size());
            for (LfBranch controllerBranch : controllerBranches) {
                equationSystem.getEquation(controllerBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], Math.toRadians(1d)));
            }
            jacobianMatrix.solveTransposed(rhs);
            return rhs;
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<DcVariableType, DcEquationType> getP1(LfBranch controlledBranch) {
            return (EquationTerm<DcVariableType, DcEquationType>) controlledBranch.getP1();
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<DcVariableType, DcEquationType> getP2(LfBranch controlledBranch) {
            return (EquationTerm<DcVariableType, DcEquationType>) controlledBranch.getP2();
        }

        double calculateSensitivityFromA2S(LfBranch controllerBranch, EquationTerm<DcVariableType, DcEquationType> s) {
            return s.calculateSensi(getSensitivities(), controllerBranchIndex[controllerBranch.getNum()]);
        }

        double calculateSensitivityFromA2P(LfBranch controllerBranch, LfBranch controlledBranch, ControlledSide controlledSide) {
            var p = controlledSide == ControlledSide.ONE ? getP1(controlledBranch) : getP2(controlledBranch);
            return calculateSensitivityFromA2S(controllerBranch, p);
        }
    }

    private static boolean checkActivePowerControlPhaseControls(DcSensitivityContext sensitivityContext, IncrementalContextData contextData,
                                                                List<TransformerPhaseControl> activePowerControlPhaseControls) {
        MutableBoolean updated = new MutableBoolean(false);

        for (TransformerPhaseControl phaseControl : activePowerControlPhaseControls) {
            LfBranch controllerBranch = phaseControl.getControllerBranch();
            LfBranch controlledBranch = phaseControl.getControlledBranch();
            var p = phaseControl.getControlledSide() == ControlledSide.ONE
                    ? controlledBranch.getP1() : controlledBranch.getP2();
            double pValue = p.eval();
            double halfTargetDeadband = getHalfTargetDeadband(phaseControl);
            if (Math.abs(pValue - phaseControl.getTargetValue()) > halfTargetDeadband) {
                var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
                double dp = phaseControl.getTargetValue() - pValue;
                double a2p = sensitivityContext.calculateSensitivityFromA2P(controllerBranch, controlledBranch, phaseControl.getControlledSide());
                if (Math.abs(a2p) > SENSI_EPS) {
                    double da = Math.toRadians(dp / a2p);
                    LOGGER.trace("Controlled branch '{}' active power is {} MW and out of target value {} MW (half deadband={} MW), a phase shift of {}° is required",
                            controlledBranch.getId(), pValue * PerUnit.SB, phaseControl.getTargetValue() * PerUnit.SB, halfTargetDeadband * PerUnit.SB, Math.toDegrees(da));
                    PiModel piModel = controllerBranch.getPiModel();

                    int oldTapPosition = piModel.getTapPosition();
                    Range<Integer> tapPositionRange = piModel.getTapPositionRange();
                    piModel.updateTapPositionToReachNewA1(da, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                        controllerContext.updateAllowedDirection(direction);
                        updated.setValue(true);
                    });

                    if (piModel.getTapPosition() != oldTapPosition) {
                        LOGGER.debug("Controller branch '{}' change tap from {} to {} to reach active power target (full range: {})", controllerBranch.getId(),
                                oldTapPosition, piModel.getTapPosition(), tapPositionRange);
                    }
                }
            }
        }

        return updated.booleanValue();
    }

    @Override
    public OuterLoopStatus check(DcOuterLoopContext context, Reporter reporter) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        var contextData = (IncrementalContextData) context.getData();

        LfNetwork network = context.getNetwork();

        List<LfBranch> controllerBranches = getControllerBranches(network);

        // find list of phase controls that are in current limiter and active power control
        List<TransformerPhaseControl> activePowerControlPhaseControls = new ArrayList<>();
        List<TransformerPhaseControl> currentLimiterPhaseControls = new ArrayList<>();
        for (LfBranch controllerBranch : controllerBranches) {
            controllerBranch.getPhaseControl().ifPresent(phaseControl -> {
                switch (phaseControl.getMode()) {
                    case CONTROLLER:
                        activePowerControlPhaseControls.add(phaseControl);
                        break;
                    case LIMITER:
                        currentLimiterPhaseControls.add(phaseControl);
                        break;
                    default:
                        break;
                }
            });
        }

        if (!currentLimiterPhaseControls.isEmpty() || !activePowerControlPhaseControls.isEmpty()) {
            var sensitivityContext = new DcSensitivityContext(network,
                    controllerBranches,
                    context.getLoadFlowContext().getEquationSystem(),
                    context.getLoadFlowContext().getJacobianMatrix());

            if (!activePowerControlPhaseControls.isEmpty()
                    && checkActivePowerControlPhaseControls(sensitivityContext,
                    contextData,
                    activePowerControlPhaseControls)) {
                status = OuterLoopStatus.UNSTABLE;
            }

            // Current limiter is currently not implemented
        }

        return status;
    }
}
