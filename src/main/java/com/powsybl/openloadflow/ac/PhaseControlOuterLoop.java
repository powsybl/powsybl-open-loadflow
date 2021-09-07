/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControlOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseControlOuterLoop.class);

    @Override
    public String getType() {
        return "Phase control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context, Reporter reporter) {
        if (context.getIteration() == 0) {
            // at first outer loop iteration:
            // branches with active power control are switched off and taps are rounded
            // branches with current limiter control will wait for second iteration
            return firstIteration(context);
        } else if (context.getIteration() > 0) {
            // at second outer loop iteration:
            // flow of branches with fixed tap are recomputed
            return nextIteration(context);
        }
        return OuterLoopStatus.STABLE;
    }

    private OuterLoopStatus firstIteration(OuterLoopContext context) {

        List<DiscretePhaseControl> phaseControlsOn = context.getNetwork().getBranches().stream()
            .map(branch -> branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseControlled()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(dpc -> dpc.getMode() == DiscretePhaseControl.Mode.CONTROLLER || dpc.getMode() == DiscretePhaseControl.Mode.LIMITER)
            .collect(Collectors.toList());

        // all branches with active power control are switched off
        phaseControlsOn.stream().filter(dpc -> dpc.getMode() == DiscretePhaseControl.Mode.CONTROLLER).forEach(this::switchOffPhaseControl);

        // if at least one phase shifter has been switched off we need to continue
        return phaseControlsOn.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
    }

    private OuterLoopStatus nextIteration(OuterLoopContext context) {
        // at second outer loop iteration we switch on phase control for branches that are in limiter mode
        // and a current greater than the limit
        // phase control consists in increasing or decreasing tap position to limit the current
        List<DiscretePhaseControl> unstablePhaseControls = context.getNetwork().getBranches().stream()
                .map(branch -> branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseControlled()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(dpc -> dpc.getMode() == DiscretePhaseControl.Mode.LIMITER)
                .filter(dpc -> changeTapPositions(dpc) == OuterLoopStatus.UNSTABLE)
                .collect(Collectors.toList());

        return unstablePhaseControls.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
    }

    private void switchOffPhaseControl(DiscretePhaseControl phaseControl) {
        // switch off phase control
        phaseControl.setMode(DiscretePhaseControl.Mode.OFF);

        // round the phase shift to the closest tap
        LfBranch controllerBranch = phaseControl.getController();
        PiModel piModel = controllerBranch.getPiModel();
        double a1Value = piModel.getA1();
        piModel.roundA1ToClosestTap();
        double roundedA1Value = piModel.getA1();
        LOGGER.info("Round phase shift of '{}': {} -> {}", controllerBranch.getId(), a1Value, roundedA1Value);
    }

    private OuterLoopStatus changeTapPositions(DiscretePhaseControl phaseControl) {
        // only local control supported: controlled branch is controller branch.
        double currentLimit = phaseControl.getTargetValue();
        LfBranch controllerBranch = phaseControl.getController();
        PiModel piModel = controllerBranch.getPiModel();
        boolean isSensibilityPositive;
        boolean success = false;
        if (phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.ONE && currentLimit < controllerBranch.getI1().eval()) {
            isSensibilityPositive = isSensitivityCurrentPerA1Positive(controllerBranch, DiscretePhaseControl.ControlledSide.ONE);
            success = isSensibilityPositive ? piModel.updateTapPosition(PiModel.Direction.DECREASE) : piModel.updateTapPosition(PiModel.Direction.INCREASE);
        } else if (phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.TWO && currentLimit < controllerBranch.getI2().eval()) {
            isSensibilityPositive = isSensitivityCurrentPerA1Positive(controllerBranch, DiscretePhaseControl.ControlledSide.TWO);
            success = isSensibilityPositive ? piModel.updateTapPosition(PiModel.Direction.DECREASE) : piModel.updateTapPosition(PiModel.Direction.INCREASE);
        }
        return success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
    }

    boolean isSensitivityCurrentPerA1Positive(LfBranch controllerBranch, DiscretePhaseControl.ControlledSide controlledSide) {
        if (controlledSide == DiscretePhaseControl.ControlledSide.ONE) {
            ClosedBranchSide1CurrentMagnitudeEquationTerm i1 = (ClosedBranchSide1CurrentMagnitudeEquationTerm) controllerBranch.getI1();
            Variable<AcVariableType> a1Var = i1.getVariables().stream().filter(v -> v.getType() == AcVariableType.BRANCH_ALPHA1).findFirst().orElseThrow();
            return i1.der(a1Var) > 0;
        } else {
            ClosedBranchSide2CurrentMagnitudeEquationTerm i2 = (ClosedBranchSide2CurrentMagnitudeEquationTerm) controllerBranch.getI2();
            Variable<AcVariableType> a1Var = i2.getVariables().stream().filter(v -> v.getType() == AcVariableType.BRANCH_ALPHA1).findFirst().orElseThrow();
            return i2.der(a1Var) > 0;
        }
    }
}
