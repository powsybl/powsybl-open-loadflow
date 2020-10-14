/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.PhaseControl;
import com.powsybl.openloadflow.network.PiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
    public OuterLoopStatus check(OuterLoopContext context) {
        OuterLoopStatus status = OuterLoopStatus.STABLE;

        if (context.getIteration() == 0) {
            // at first iteration all branches controlling phase are switched off
            for (LfBranch branch : context.getNetwork().getBranches()) {
                if (branch.isPhaseControlled()) {
                    PhaseControl phaseControl = branch.getPhaseControl();
                    LfBranch controllerBranch = phaseControl.getController();
                    if (phaseControl.getMode() == PhaseControl.Mode.CONTROLLER) {
                        // switch off phase shifter
                        phaseControl.setMode(PhaseControl.Mode.OFF);

                        // de-activate a1 variable for next outer loop run
                        Variable a1 = context.getVariableSet().getVariable(controllerBranch.getNum(), VariableType.BRANCH_ALPHA1);
                        a1.setActive(false);

                        // de-activate phase control equation
                        Equation t = context.getEquationSystem().createEquation(branch.getNum(), EquationType.BRANCH_P);
                        t.setActive(false);

                        // round the phase shift to the closest tap
                        PiModel piModel = controllerBranch.getPiModel();
                        double a1Value = piModel.getA1();
                        piModel.roundA1ToClosestTap();
                        double roundedA1Value = piModel.getA1();
                        LOGGER.info("Round phase shift of '{}': {} -> {}", controllerBranch.getId(), a1Value, roundedA1Value);

                        // if at least one phase shifter has been switched off wee need to continue
                        status = OuterLoopStatus.UNSTABLE;
                    } else if (phaseControl.getMode() == PhaseControl.Mode.LIMITER) {
                        status = OuterLoopStatus.UNSTABLE;
                    }
                }
            }
        } else if (context.getIteration() > 0) {
            // at second iteration we switch on phase control for branches that are in limiter mode
            // and a current greater than the limit
            // phase control consists in increasing or decreasing tap position to limit the current
            for (LfBranch branch : context.getNetwork().getBranches()) {
                if (branch.isPhaseControlled()) {
                    PhaseControl phaseControl = branch.getPhaseControl();
                    if (phaseControl.getMode() == PhaseControl.Mode.LIMITER) {
                        double currentLimit  = branch.getPhaseControl().getTargetValue();
                        LfBranch controllerBranch = phaseControl.getController();

                        if (branch.getPhaseControl().getControlledSide() == PhaseControl.ControlledSide.ONE && currentLimit < branch.getI1()) {
                            PiModel piModel = controllerBranch.getPiModel();
                            boolean isSensibilityPositive = isSensibilityCurrentPerA1Positive(context.getEquationSystem().getEquationTerms(SubjectType.BRANCH, branch.getNum()),
                                    context.getVariableSet(), controllerBranch, PhaseControl.ControlledSide.ONE);
                            boolean success = isSensibilityPositive ? piModel.decreaseA1WithTapPositionIncrement() : piModel.increaseA1WithTapPositionIncrement();
                            status = success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;

                        } else if (branch.getPhaseControl().getControlledSide() == PhaseControl.ControlledSide.TWO && currentLimit < branch.getI2()) {
                            PiModel piModel = controllerBranch.getPiModel();
                            boolean isSensibilityPositive = isSensibilityCurrentPerA1Positive(context.getEquationSystem().getEquationTerms(SubjectType.BRANCH, branch.getNum()),
                                    context.getVariableSet(), controllerBranch, PhaseControl.ControlledSide.TWO);
                            boolean success = isSensibilityPositive ? piModel.decreaseA1WithTapPositionIncrement() : piModel.increaseA1WithTapPositionIncrement();
                            status = success ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE;
                        }
                    }
                }
            }
        }
        return status;
    }

    boolean isSensibilityCurrentPerA1Positive(List<EquationTerm> equationTerms, VariableSet variableSet,
                                                LfBranch controllerBranch, PhaseControl.ControlledSide controlledSide) {
        double derSide1 = 0;
        double derSide2 = 0;
        Variable ph1Var = variableSet.getVariable(controllerBranch.getBus1().getNum(), VariableType.BUS_PHI);
        Variable ph2Var = variableSet.getVariable(controllerBranch.getBus2().getNum(), VariableType.BUS_PHI);
        for (EquationTerm equationTerm : equationTerms) {
            if (equationTerm instanceof ClosedBranchSide1ActiveFlowEquationTerm) {
                derSide1 += equationTerm.eval() * equationTerm.der(ph1Var);
            } else if (equationTerm instanceof ClosedBranchSide1ReactiveFlowEquationTerm) {
                derSide1 += equationTerm.eval() * equationTerm.der(ph1Var);
            } else if (equationTerm instanceof ClosedBranchSide2ActiveFlowEquationTerm) {
                derSide2 += equationTerm.eval() * equationTerm.der(ph2Var);
            } else if (equationTerm instanceof ClosedBranchSide2ReactiveFlowEquationTerm) {
                derSide2 += equationTerm.eval() * equationTerm.der(ph2Var);
            }
        }
        if (controlledSide == PhaseControl.ControlledSide.ONE) {
            return derSide1 > 0 ? true : false;
        } else {
            return derSide2 > 0 ? true : false;
        }
    }
}
