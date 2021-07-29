/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.openloadflow.ac.equations.AbstractClosedBranchAcFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableType;
import com.powsybl.openloadflow.network.DiscretePhaseControl;
import com.powsybl.openloadflow.network.ElementType;
import com.powsybl.openloadflow.network.LfBranch;
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
        // TODO: only done for controller mode so far, phase shifter in limiter mode not yet implemented
        phaseControlsOn.stream().filter(dpc -> dpc.getMode() == DiscretePhaseControl.Mode.CONTROLLER).forEach(s -> switchOffPhaseControl(s, context));

        // if at least one phase shifter has been switched off we need to continue
        return phaseControlsOn.isEmpty() ? OuterLoopStatus.STABLE : OuterLoopStatus.UNSTABLE;
    }

    private OuterLoopStatus nextIteration(OuterLoopContext context) {
        // at second outer loop iteration we switch on phase control for branches that are in limiter mode
        // and a current greater than the limit
        for (LfBranch branch : context.getNetwork().getBranches()) {
            branch.getDiscretePhaseControl()
                .filter(dpc -> branch.isPhaseControlled() && dpc.getMode() == DiscretePhaseControl.Mode.LIMITER)
                .ifPresent(discretePhaseControl -> LOGGER.warn("Phase shifter in limiter mode not yet implemented")); // TODO
        }
        return OuterLoopStatus.STABLE;
    }

    private void switchOffPhaseControl(DiscretePhaseControl phaseControl, OuterLoopContext context) {
        // switch off phase control
        phaseControl.setMode(DiscretePhaseControl.Mode.OFF);

        // round the phase shift to the closest tap
        LfBranch controllerBranch = phaseControl.getController();
        PiModel piModel = controllerBranch.getPiModel();
        double a1Value = piModel.getA1();

        EquationSystem sys = context.getEquationSystem();
        LfBranch controlledBranch = phaseControl.getControlled();

        //When controller is not the controlled branch we cannot use partial derivative to assess the impact of a1 variation
        //To take into account the deadband a sensitivity analysis or a new load flow computation is needed
        //Therefore deadband check is not supported yet
        if (controllerBranch.getId().equals(controlledBranch.getId())) {
            AbstractClosedBranchAcFlowEquationTerm term = null;
            double p = 0.0;
            if (phaseControl.getControlledSide().equals(DiscretePhaseControl.ControlledSide.ONE)) {
                term = sys.getEquationTerm(ElementType.BRANCH, controlledBranch.getNum(), ClosedBranchSide1ActiveFlowEquationTerm.class);
                p = controlledBranch.getP1().eval();
            } else {
                term = sys.getEquationTerm(ElementType.BRANCH, controlledBranch.getNum(), ClosedBranchSide2ActiveFlowEquationTerm.class);
                p = controlledBranch.getP2().eval();
            }

            Variable v = context.getVariableSet().getVariable(controlledBranch.getNum(), VariableType.BRANCH_ALPHA1);
            double dpSideXa1 = term.der(v);
            double currentTapFlow = p + dpSideXa1 * (piModel.getCurrentTapA1() - a1Value);
            if (currentTapFlow < phaseControl.getTargetValue() + phaseControl.getTargetDeadband() && currentTapFlow > phaseControl.getTargetValue() - phaseControl.getTargetDeadband()) {
                piModel.setA1(Double.NaN);
                //Flow is within deadband
                LOGGER.info("Flow is within deadband '{}': {} -> {}", controllerBranch.getId(), a1Value, piModel.getA1());
                return;
            }
        }

        piModel.roundA1ToClosestTap();
        double roundedA1Value = piModel.getA1();
        LOGGER.info("Round phase shift of '{}': {} -> {}", controllerBranch.getId(), a1Value, roundedA1Value);
    }
}
