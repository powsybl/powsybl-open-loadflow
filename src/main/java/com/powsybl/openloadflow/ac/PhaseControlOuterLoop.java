/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Transformers;

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

        if (context.getIteration() == 0) {

            status = OuterLoopStatus.UNSTABLE;

        } else {
            for (LfBranch branch : context.getNetwork().getBranches()) {
                PhaseTapChanger ptc = null;
                if (branch.getBranch() instanceof TwoWindingsTransformer) {
                    TwoWindingsTransformer twt = (TwoWindingsTransformer) branch.getBranch();
                    ptc = twt.getPhaseTapChanger();
                    if (ptc != null && ptc.isRegulating() && ptc.getRegulationMode() == PhaseTapChanger.RegulationMode.ACTIVE_POWER_CONTROL) {
                        int step = Transformers.findStep(ptc, branch.getA1());
                        ptc.setTapPosition(step);
                        branch.getPiModel().setA1(Transformers.getAngle(twt));
                        ptc.setRegulating(false); // Fix.

                        updateBranchEquations(context.getEquationSystem(), context.getVariableSet(), context.getNetwork(), branch);

                        status = OuterLoopStatus.UNSTABLE;

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
                }
            }
        }
        return status;
    }

    protected void updateBranchEquations(EquationSystem equationSystem, VariableSet variableSet, LfNetwork network, LfBranch branch) {

        // The phase tap changer is no more regulating active power.
        // Equations of branch buses have to be removed.
        equationSystem.removeEquation(branch.getNum(), EquationType.BRANCH_P);
        equationSystem.removeEquation(branch.getBus1().getNum(), EquationType.BUS_P);
        equationSystem.removeEquation(branch.getBus1().getNum(), EquationType.BUS_Q);
        equationSystem.removeEquation(branch.getBus2().getNum(), EquationType.BUS_P);
        equationSystem.removeEquation(branch.getBus2().getNum(), EquationType.BUS_Q);

        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();

        // If shunts are connected to bus 1.
        for (LfShunt shunt : bus1.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus1,
                    network, variableSet);
            equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q);
        }

        // If shunts are connected to bus 2.
        for (LfShunt shunt : bus2.getShunts()) {
            ShuntCompensatorReactiveFlowEquationTerm q = new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus2,
                    network, variableSet);
            equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q);
        }

        // TODO : if low impedance branches are connected to buses 1 or 2, or if the branch itself is low impedance branch.

        for (LfBranch b : network.getBranches()) {
            EquationTerm p1 = null;
            EquationTerm q1 = null;
            EquationTerm p2 = null;
            EquationTerm q2 = null;
            AcEquationTermDerivativeParameters derivativeParameters = new AcEquationTermDerivativeParameters(false, false);
            if (b.getBus1() == bus1 && b.getBus2() == bus2) {
                p1 = new ClosedBranchSide1ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                p2 = new ClosedBranchSide2ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                b.setP1(p1);
                b.setQ1(q1);
                b.setP2(p2);
                b.setQ2(q2);
            } else {
                if (b.getBus1() == bus1) {
                    if (b.getBus2() != null) {
                        p1 = new ClosedBranchSide1ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                        q1 = new ClosedBranchSide1ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);

                    } else if (b.getBus2() == null) {
                        p1 = new OpenBranchSide1ActiveFlowEquationTerm(b, b.getBus1(), variableSet);
                        q1 = new OpenBranchSide1ReactiveFlowEquationTerm(b, b.getBus1(), variableSet);
                    }
                    // Equation for bus1.
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                    b.setP1(p1);
                    b.setQ1(q1);
                } else if (b.getBus2() == bus1) {
                    if (b.getBus1() != null) {
                        p1 = new ClosedBranchSide2ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                        q1 = new ClosedBranchSide2ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);

                    } else if (b.getBus1() == null) {
                        p1 = new OpenBranchSide2ActiveFlowEquationTerm(b, b.getBus2(), variableSet);
                        q1 = new OpenBranchSide2ReactiveFlowEquationTerm(b, b.getBus2(), variableSet);
                    }
                    // Equation for bus1.
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_P).addTerm(p1);
                    equationSystem.createEquation(bus1.getNum(), EquationType.BUS_Q).addTerm(q1);
                    b.setP1(p1);
                    b.setQ1(q1);
                } else if (b.getBus1() == bus2) {
                    if (b.getBus2() != null) {
                        p2 = new ClosedBranchSide1ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                        q2 = new ClosedBranchSide1ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                    } else if (b.getBus2() == null) {
                        p2 = new OpenBranchSide1ActiveFlowEquationTerm(b, b.getBus1(), variableSet);
                        q2 = new OpenBranchSide1ReactiveFlowEquationTerm(b, b.getBus1(), variableSet);
                    }
                    // Equation for bus2.
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                    b.setP2(p2);
                    b.setQ2(q2);
                } else if (b.getBus2() == bus2) {
                    if (b.getBus1() != null) {
                        p2 = new ClosedBranchSide2ActiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);
                        q2 = new ClosedBranchSide2ReactiveFlowEquationTerm(b, b.getBus1(), b.getBus2(), variableSet, derivativeParameters);

                    } else if (b.getBus1() == null) {
                        p2 = new OpenBranchSide2ActiveFlowEquationTerm(b, bus1, variableSet);
                        q2 = new OpenBranchSide2ReactiveFlowEquationTerm(b, bus1, variableSet);
                    }
                    // Equation for bus2.
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_P).addTerm(p2);
                    equationSystem.createEquation(bus2.getNum(), EquationType.BUS_Q).addTerm(q2);
                    b.setP2(p2);
                    b.setQ2(q2);
                }
            }
        }
    }
}
