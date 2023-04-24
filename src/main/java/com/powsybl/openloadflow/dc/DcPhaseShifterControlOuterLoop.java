/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.AbstractPhaseControlOuterLoop;
import com.powsybl.openloadflow.OuterLoopContext;
import com.powsybl.openloadflow.IncrementalContextData;
import com.powsybl.openloadflow.OuterLoopStatus;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.JacobianMatrix;
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
public class DcPhaseShifterControlOuterLoop extends AbstractPhaseControlOuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcPhaseShifterControlOuterLoop.class);

    private static final int MAX_DIRECTION_CHANGE = 2;
    private static final int MAX_TAP_SHIFT = Integer.MAX_VALUE;
    private static final double MIN_TARGET_DEADBAND = 1 / PerUnit.SB; // 1 MW
    private static final double SENSI_EPS = 1e-6;
    private static final double PHASE_SHIFT_CROSS_IMPACT_MARGIN = 0.75;

    @Override
    public String getType() {
        return "DC phase shifter control";
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

    public static class SensitivityContext {

        private final List<LfBranch> controllerBranches;

        private final EquationSystem<DcVariableType, DcEquationType> equationSystem;

        private final JacobianMatrix<DcVariableType, DcEquationType> jacobianMatrix;

        private final int[] controllerBranchIndex;

        private DenseMatrix sensitivities;

        public SensitivityContext(LfNetwork network, List<LfBranch> controllerBranches,
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
                LfBus controlledSideBus = null;
                if (controllerBranch.getPhaseControl().isPresent()) {
                    if (controllerBranch.getPhaseControl().get().getControlledSide() == ControlledSide.ONE) {
                        controlledSideBus = controllerBranch.getBus1();
                    } else {
                        controlledSideBus = controllerBranch.getBus2();
                    }
                }
                assert controlledSideBus != null;
                equationSystem.getEquation(controlledSideBus.getNum(), DcEquationType.BUS_TARGET_P)
                        .ifPresent(equation -> rhs.set(equation.getColumn(), controllerBranchIndex[controllerBranch.getNum()], Math.toRadians(1d))); //TODO
            }
            jacobianMatrix.solveTransposed(rhs);
            return rhs;
        }

        private static EquationTerm<DcVariableType, DcEquationType> getI1(LfBranch controlledBranch) {
            return (EquationTerm<DcVariableType, DcEquationType>) controlledBranch.getI1();
        }

        @SuppressWarnings("unchecked")
        private static EquationTerm<DcVariableType, DcEquationType> getI2(LfBranch controlledBranch) {
            return (EquationTerm<DcVariableType, DcEquationType>) controlledBranch.getI2();
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

        public double calculateSensitivityFromA2I(LfBranch controllerBranch, LfBranch controlledBranch, ControlledSide controlledSide) {
            var i = controlledSide == ControlledSide.ONE ? getI1(controlledBranch) : getI2(controlledBranch);
            return calculateSensitivityFromA2S(controllerBranch, i);
        }

        double calculateSensitivityFromA2P(LfBranch controllerBranch, LfBranch controlledBranch, ControlledSide controlledSide) {
            var p = controlledSide == ControlledSide.ONE ? getP1(controlledBranch) : getP2(controlledBranch);
            return calculateSensitivityFromA2S(controllerBranch, p);
        }
    }

    private static double computeIb(TransformerPhaseControl phaseControl) {
        LfBus bus = phaseControl.getControlledSide() == ControlledSide.ONE
                ? phaseControl.getControlledBranch().getBus1() : phaseControl.getControlledBranch().getBus2();
        return PerUnit.ib(bus.getNominalV());
    }

    private static double computeI(TransformerPhaseControl phaseControl) {
        var i = phaseControl.getControlledSide() == ControlledSide.ONE
                ? phaseControl.getControlledBranch().getI1() : phaseControl.getControlledBranch().getI2();
        return i.eval();
    }

    private static boolean checkCurrentLimiterPhaseControls(SensitivityContext sensitivityContext, IncrementalContextData contextData,
                                                            List<TransformerPhaseControl> currentLimiterPhaseControls) {
        MutableBoolean updated = new MutableBoolean(false);

        for (TransformerPhaseControl phaseControl : currentLimiterPhaseControls) {
            LfBranch controllerBranch = phaseControl.getControllerBranch();
            LfBranch controlledBranch = phaseControl.getControlledBranch();
            double i = computeI(phaseControl);
            if (i > phaseControl.getTargetValue()) {
                var controllerContext = contextData.getControllersContexts().get(controllerBranch.getId());
                double di = phaseControl.getTargetValue() - i;
                double a2i = sensitivityContext.calculateSensitivityFromA2I(controllerBranch, controlledBranch, phaseControl.getControlledSide());
                if (Math.abs(a2i) > SENSI_EPS) {
                    double da = Math.toRadians(di / a2i);
                    double ib = computeIb(phaseControl);
                    LOGGER.trace("Controlled branch '{}' current is {} A and above target value {} A, a phase shift of {}° is required",
                            controlledBranch.getId(), i * ib, phaseControl.getTargetValue() * ib, Math.toDegrees(da));
                    PiModel piModel = controllerBranch.getPiModel();

                    int oldTapPosition = piModel.getTapPosition();
                    double oldA1 = piModel.getA1();
                    Range<Integer> tapPositionRange = piModel.getTapPositionRange();
                    piModel.updateTapPositionToExceedNewA1(da, MAX_TAP_SHIFT, controllerContext.getAllowedDirection()).ifPresent(direction -> {
                        controllerContext.updateAllowedDirection(direction);
                        updated.setValue(true);
                    });

                    if (piModel.getTapPosition() != oldTapPosition) {
                        LOGGER.debug("Controller branch '{}' change tap from {} to {} to limit current (full range: {})", controllerBranch.getId(),
                                oldTapPosition, piModel.getTapPosition(), tapPositionRange);

                        double discreteDa = piModel.getA1() - oldA1;
                        checkImpactOnOtherPhaseShifters(sensitivityContext, phaseControl, currentLimiterPhaseControls, discreteDa);
                    }
                }
            }
        }

        return updated.booleanValue();
    }

    private static void checkImpactOnOtherPhaseShifters(SensitivityContext sensitivityContext, TransformerPhaseControl phaseControl,
                                                        List<TransformerPhaseControl> currentLimiterPhaseControls, double da) {
        LfBranch controllerBranch = phaseControl.getControllerBranch();
        for (TransformerPhaseControl otherPhaseControl : currentLimiterPhaseControls) {
            if (otherPhaseControl != phaseControl) {
                LfBranch otherControlledBranch = otherPhaseControl.getControlledBranch();
                double i = computeI(otherPhaseControl);
                if (i > otherPhaseControl.getTargetValue()) {
                    // get cross sensitivity of the phase shifter controller branch on the other phase shifter controlled branch
                    double crossA2i = sensitivityContext.calculateSensitivityFromA2I(controllerBranch, otherControlledBranch,
                            otherPhaseControl.getControlledSide());
                    double ib = computeIb(otherPhaseControl);
                    double di = Math.toDegrees(da) * crossA2i;
                    if (di > PHASE_SHIFT_CROSS_IMPACT_MARGIN * (i - otherPhaseControl.getTargetValue())) {
                        LOGGER.warn("Controller branch '{}' tap change significantly impact (≈ {} A) another phase shifter current also above its limit '{}', simulation might not be reliable",
                                controllerBranch.getId(), di * ib, otherPhaseControl.getControlledBranch().getId());
                    }
                }
            }
        }
    }

    private static double getHalfTargetDeadband(TransformerPhaseControl phaseControl) {
        return Math.max(phaseControl.getTargetDeadband(), MIN_TARGET_DEADBAND) / 2;
    }

    private static boolean checkActivePowerControlPhaseControls(SensitivityContext sensitivityContext, IncrementalContextData contextData,
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
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
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
            var sensitivityContext = new SensitivityContext(network,
                    controllerBranches,
                    context.getLoadFlowContext().getEquationSystem(),
                    context.getLoadFlowContext().getJacobianMatrix());

            if (!currentLimiterPhaseControls.isEmpty()
                    && checkCurrentLimiterPhaseControls(sensitivityContext,
                    contextData,
                    currentLimiterPhaseControls)) {
                status = OuterLoopStatus.UNSTABLE;
            }

            if (!activePowerControlPhaseControls.isEmpty()
                    && checkActivePowerControlPhaseControls(sensitivityContext,
                    contextData,
                    activePowerControlPhaseControls)) {
                status = OuterLoopStatus.UNSTABLE;
            }
        }

        return status;
    }
}
