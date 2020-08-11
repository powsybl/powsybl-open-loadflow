/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.SubjectType;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfContingency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class ContingencyOuterLoop implements OuterLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContingencyOuterLoop.class);

    private final Map<Integer, List<LfContingency>> contingenciesByNetworkNum;

    private static class State {

        private double[] v;

        private double[] a;
    }

    private final Map<Integer, State> stateByNetworkNum = new HashMap<>();

    public ContingencyOuterLoop(Map<Integer, List<LfContingency>> contingenciesByNetworkNum) {
        this.contingenciesByNetworkNum = Objects.requireNonNull(contingenciesByNetworkNum);
    }

    @Override
    public String getType() {
        return "Contingency";
    }

    @Override
    public OuterLoopStatus check(OuterLoopContext context) {
        List<LfBus> buses = context.getNetwork().getBuses();
        if (context.getIteration() == 0) {
            // save base state
            State state = new State();
            state.v = new double[buses.size()];
            state.a = new double[buses.size()];
            for (LfBus bus : buses) {
                state.v[bus.getNum()] = bus.getV();
                state.a[bus.getNum()] = bus.getAngle();
            }
            stateByNetworkNum.put(context.getNetwork().getNum(), state);
        } else {
            // restore base state
            State state = stateByNetworkNum.get(context.getNetwork().getNum());
            for (LfBus bus : buses) {
                bus.setV(state.v[bus.getNum()]);
                bus.setAngle(state.a[bus.getNum()]);
            }
        }

        List<LfContingency> contingencies = contingenciesByNetworkNum.getOrDefault(context.getNetwork().getNum(), Collections.emptyList());
        if (contingencies.isEmpty() || context.getIteration() >= contingencies.size()) {
            return OuterLoopStatus.STABLE;
        }

        LfContingency contingency = contingencies.get(context.getIteration());

        LOGGER.info("Simulate contingency '{}'", contingency.getId());

        for (LfBranch branch : contingency.getBranches()) {
            // deactivate all equations related to a branch
            for (Equation equation : context.getEquationSystem().getEquations(SubjectType.BRANCH, branch.getNum())) {
                equation.setActive(false);
            }

            // deactivate all equation terms related to a branch
            for (EquationTerm equationTerm : context.getEquationSystem().getEquationTerms(SubjectType.BRANCH, branch.getNum())) {
                equationTerm.setActive(false);
            }
        }
        for (LfBus bus : contingency.getBuses()) {
            // deactivate all equations related to a bus
            for (Equation equation : context.getEquationSystem().getEquations(SubjectType.BUS, bus.getNum())) {
                equation.setActive(false);
            }

            // deactivate all equation terms related to a bus
            for (EquationTerm equationTerm : context.getEquationSystem().getEquationTerms(SubjectType.BUS, bus.getNum())) {
                equationTerm.setActive(false);
            }
        }

        return OuterLoopStatus.UNSTABLE;
    }
}
