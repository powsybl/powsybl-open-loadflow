/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.DiscretePhaseControl;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.PiModel;
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
            return  firstIteration(context);
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
        List<DiscretePhaseControl> phaseControlsUnstable = context.getNetwork().getBranches().stream()
                .map(branch -> branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseControlled()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(dpc -> dpc.getMode() == DiscretePhaseControl.Mode.LIMITER)
                .filter(dpc -> detectUnstability(context, dpc) == OuterLoopStatus.UNSTABLE)
                .collect(Collectors.toList());

        return phaseControlsUnstable.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
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

    private OuterLoopStatus detectUnstability(OuterLoopContext context, DiscretePhaseControl phaseControl) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;
        double currentLimit = phaseControl.getTargetValue();
        LfBranch controllerBranch = phaseControl.getController();
        LfBranch controlledBranch = phaseControl.getControlled();

        if (phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.ONE && currentLimit < controlledBranch.getI1().eval()) {
            PiModel piModel = controllerBranch.getPiModel();
            boolean isSensibilityPositive = isSensitivityCurrentPerA1Positive(context.getVariableSet(),
                    controlledBranch, controllerBranch, DiscretePhaseControl.ControlledSide.ONE);
            boolean success = isSensibilityPositive ? piModel.decreaseA1WithTapPositionIncrement() : piModel.increaseA1WithTapPositionIncrement();
            status = success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;

        } else if (phaseControl.getControlledSide() == DiscretePhaseControl.ControlledSide.TWO && currentLimit < controlledBranch.getI1().eval()) {
            PiModel piModel = controllerBranch.getPiModel();
            boolean isSensibilityPositive = isSensitivityCurrentPerA1Positive(context.getVariableSet(),
                    controlledBranch, controllerBranch, DiscretePhaseControl.ControlledSide.TWO);
            boolean success = isSensibilityPositive ? piModel.decreaseA1WithTapPositionIncrement() : piModel.increaseA1WithTapPositionIncrement();
            status = success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
        }
        return status;
    }

    boolean isSensitivityCurrentPerA1Positive(VariableSet variableSet, LfBranch controlledBranch,
                                              LfBranch controllerBranch,
                                              DiscretePhaseControl.ControlledSide controlledSide) {
        if (controlledBranch != controllerBranch) {
            // Log a warning because the impact of PST angle on a remote branch is not known without computing a true sensitivity analysis
            // For the moment, only equation system derivatives are used to assess the impact
            // Below local impact is measured and remote impact is supposed to be the same
            LOGGER.info("WARNING: remote current limiter phase control from branch {} on branch {} ", controllerBranch, controlledBranch);
        }

        Variable a1Var = variableSet.getVariable(controllerBranch.getNum(), VariableType.BRANCH_ALPHA1);
        LfBus b1 = controllerBranch.getBus1();
        LfBus b2 = controllerBranch.getBus2();
        if (controlledSide == DiscretePhaseControl.ControlledSide.ONE) {
            ClosedBranchSide1CurrentMagnitudeEquationTerm i1 = new ClosedBranchSide1CurrentMagnitudeEquationTerm(controllerBranch, controllerBranch.getBus1(), controllerBranch.getBus2(), variableSet, true, false);
            i1.updateFromState(b1.getV().eval(), b2.getV().eval(),
                    Math.toRadians(b1.getAngle()), Math.toRadians(b2.getAngle()));
            return i1.der(a1Var) > 0;
        } else {
            ClosedBranchSide2CurrentMagnitudeEquationTerm i2 = new ClosedBranchSide2CurrentMagnitudeEquationTerm(controllerBranch, controllerBranch.getBus1(), controllerBranch.getBus2(), variableSet, true, false);
            i2.updateFromState(b1.getV().eval(), b2.getV().eval(),
                    Math.toRadians(b1.getAngle()), Math.toRadians(b2.getAngle()));
            return i2.der(a1Var) > 0;
        }
    }
}
