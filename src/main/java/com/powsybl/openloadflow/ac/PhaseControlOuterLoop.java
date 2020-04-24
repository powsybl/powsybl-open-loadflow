/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Transformers;

import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PhaseControlOuterLoop implements OuterLoop {

    @Override
    public String getName() {
        return "Phase control";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {

        OuterLoopStatus status = OuterLoopStatus.STABLE;

        for (LfBranch branch : context.getNetwork().getBranches()) {
            PhaseTapChanger ptc = null;
            if (branch.getBranch() instanceof TwoWindingsTransformer) {
                TwoWindingsTransformer twt = (TwoWindingsTransformer) branch.getBranch();
                ptc = twt.getPhaseTapChanger();
                if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                    int step = Transformers.findStep(ptc, branch.getPiModel().getA1());
                    ptc.setTapPosition(step);
                    branch.getPiModel().setA1(Transformers.getAngle(twt));

                    status = OuterLoopStatus.UNSTABLE;

                    context.getEquationSystem().removeEquation(branch.getNum(), EquationType.BRANCH_P);
                    context.getEquationSystem().removeEquation(branch.getBus1().getNum(), EquationType.BUS_P);
                    context.getEquationSystem().removeEquation(branch.getBus1().getNum(), EquationType.BUS_Q);
                    context.getEquationSystem().removeEquation(branch.getBus2().getNum(), EquationType.BUS_P);
                    context.getEquationSystem().removeEquation(branch.getBus2().getNum(), EquationType.BUS_Q);

                    LfBus bus1 = branch.getBus1();
                    LfBus bus2 = branch.getBus2();

                    for (LfShunt shunt : bus1.getShunts()) {
                        ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus1,
                                context.getNetwork(), context.getVariableSet());
                        context.getEquationSystem().createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q);
                    }

                    for (LfShunt shunt : bus2.getShunts()) {
                        ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus2,
                                context.getNetwork(), context.getVariableSet());
                        context.getEquationSystem().createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q);
                    }

                    for (LfBranch b : context.getNetwork().getBranches()) {
                        EquationTerm p1 = null;
                        EquationTerm q1 = null;
                        EquationTerm p2 = null;
                        EquationTerm q2 = null;
                        if (b.getBus1() == bus1) {
                            if (b.getBus2() != null) {
                                AcEquationTermDerivativeParameters derivativeParameters = new AcEquationTermDerivativeParameters(false, false);
                                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);
                                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);

                            } else if (b.getBus2() == null) {
                                p1 = new OpenBranchSide1ActiveFlowEquationTerm(branch, b.getBus1(), context.getVariableSet());
                                q1 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, b.getBus1(), context.getVariableSet());
                            }
                            // Equation for bus1.
                            context.getEquationSystem().createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                            context.getEquationSystem().createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                        } else if (b.getBus1() == bus2) {
                            if (b.getBus2() != null) {
                                AcEquationTermDerivativeParameters derivativeParameters = new AcEquationTermDerivativeParameters(false, false);
                                p2 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);
                                q2 = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);
                            } else if (b.getBus2() == null) {
                                p2 = new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, context.getVariableSet());
                                q2 = new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, context.getVariableSet());
                            }
                            // Equation for bus2.
                            context.getEquationSystem().createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                            context.getEquationSystem().createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                        } else if (b.getBus2() == bus1) {
                            if (b.getBus1() != null) {
                                AcEquationTermDerivativeParameters derivativeParameters = new AcEquationTermDerivativeParameters(false, false);
                                p1 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);
                                q1 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);

                            } else if (b.getBus1() == null) {
                                p1 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, context.getVariableSet());
                                q1 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, context.getVariableSet());
                            }
                            // Equation for bus1.
                            context.getEquationSystem().createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                            context.getEquationSystem().createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                        } else if (b.getBus2() == bus2) {
                            if (b.getBus1() != null) {
                                AcEquationTermDerivativeParameters derivativeParameters = new AcEquationTermDerivativeParameters(false, false);
                                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);
                                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, b.getBus1(), b.getBus2(), context.getVariableSet(), derivativeParameters);

                            } else if (b.getBus1() == null) {
                                p2 = new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, context.getVariableSet());
                                q2 = new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, context.getVariableSet());
                            }
                            // Equation for bus2.
                            context.getEquationSystem().createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                            context.getEquationSystem().createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                        }
                    }
                }
            }


            }
            /*} else if (branch.getBranch() instanceof ThreeWindingsTransformer.Leg) {
                ThreeWindingsTransformer.Leg leg = (ThreeWindingsTransformer.Leg) branch.getBranch();
                ptc = leg.getPhaseTapChanger();
                if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                    int step = Transformers.findStep(ptc, branch.getPiModel().getA1());
                    ptc.setTapPosition(step);
                    branch.getPiModel().setA1(Transformers.getAngleLeg(leg));
                    status = OuterLoopStatus.UNSTABLE;
                }
            }*/

        return status;
    }
}
