/**
 * Copyright (c) 2026, Kickstage (https://kickstage.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.equations;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.Variable;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the measurement function h(x) of a meter out of the analytic AC equation terms
 * this package already provides for load flow. These are the terms the AC load flow
 * itself evaluates, so an estimator built on them interprets the network identically.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class MeasurementEquations {

    private MeasurementEquations() {
    }

    public static MeasurementEquation busVoltage(LfBus bus, VariableSet<AcVariableType> variableSet,
                                                 StateVector stateVector) {
        Objects.requireNonNull(bus);
        Variable<AcVariableType> v = variableSet.getVariable(bus.getNum(), AcVariableType.BUS_V);
        return new VoltageMagnitudeEquation(v, stateVector);
    }

    /**
     * Builds the equation for an active or reactive injection meter at a bus, in
     * generation-positive convention: power fed into the network is positive, which is
     * the sum of the flows leaving the bus into its incident branches plus the
     * contribution of the shunts sitting on it.
     *
     * <p>Shunt handling follows {@code AcEquationSystemCreator.createShuntEquations}: a
     * bus can carry a fixed shunt, a controller shunt and an SVC shunt at the same time,
     * and each has an active contribution as well as a reactive one.</p>
     */
    public static MeasurementEquation busInjection(LfBus bus, boolean active,
                                                   VariableSet<AcVariableType> variableSet,
                                                   StateVector stateVector) {
        Objects.requireNonNull(bus);
        List<SingleEquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : bus.getBranches()) {
            injectionTerm(branch, bus, active, variableSet).ifPresent(terms::add);
        }
        for (LfShunt shunt : shunts(bus)) {
            terms.add(shuntTerm(shunt, bus, active, variableSet));
        }
        return sum(terms, stateVector);
    }

    /**
     * @param side 1 or 2, resolved against the branch orientation
     */
    public static MeasurementEquation branchFlow(LfBranch branch, MeasuredQuantity quantity, int side,
                                                 VariableSet<AcVariableType> variableSet,
                                                 StateVector stateVector) {
        Objects.requireNonNull(branch);
        if (side != 1 && side != 2) {
            throw new IllegalArgumentException("Branch side must be 1 or 2, was " + side);
        }
        return sum(List.of(closedBranchTerm(branch, quantity, side, variableSet)), stateVector);
    }

    private static Optional<SingleEquationTerm<AcVariableType, AcEquationType>> injectionTerm(
            LfBranch branch, LfBus bus, boolean active, VariableSet<AcVariableType> variableSet) {
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 != null && bus2 != null) {
            int side = bus1.getNum() == bus.getNum() ? 1 : 2;
            MeasuredQuantity quantity = active ? MeasuredQuantity.BRANCH_P : MeasuredQuantity.BRANCH_Q;
            return Optional.of(closedBranchTerm(branch, quantity, side, variableSet));
        }
        if (bus1 == null && bus2 != null && bus2.getNum() == bus.getNum()) {
            // side 1 open: only the connected side 2 half carries flow
            return Optional.of(active
                    ? new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet)
                    : new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet));
        }
        if (bus2 == null && bus1 != null && bus1.getNum() == bus.getNum()) {
            return Optional.of(active
                    ? new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet)
                    : new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet));
        }
        return Optional.empty();
    }

    private static List<LfShunt> shunts(LfBus bus) {
        List<LfShunt> shunts = new ArrayList<>(3);
        bus.getShunt().ifPresent(shunts::add);
        bus.getControllerShunt().ifPresent(shunts::add);
        bus.getSvcShunt().ifPresent(shunts::add);
        return shunts;
    }

    private static SingleEquationTerm<AcVariableType, AcEquationType> shuntTerm(LfShunt shunt, LfBus bus, boolean active,
                                                                               VariableSet<AcVariableType> variableSet) {
        return active
                ? new ShuntCompensatorActiveFlowEquationTerm(shunt, bus, variableSet)
                : new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, variableSet, false);
    }

    private static SingleEquationTerm<AcVariableType, AcEquationType> closedBranchTerm(
            LfBranch branch, MeasuredQuantity quantity, int side, VariableSet<AcVariableType> variableSet) {
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 == null || bus2 == null) {
            throw new IllegalArgumentException("Branch " + branch.getId()
                    + " is open at one side and carries no measurable flow at side " + side);
        }
        return switch (quantity) {
            case BRANCH_P -> side == 1
                    ? new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false)
                    : new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
            case BRANCH_Q -> side == 1
                    ? new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false)
                    : new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
            case BRANCH_I -> side == 1
                    ? new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, false, false)
                    : new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, false, false);
            default -> throw new IllegalArgumentException(quantity + " is not a branch quantity");
        };
    }

    private static MeasurementEquation sum(List<SingleEquationTerm<AcVariableType, AcEquationType>> terms,
                                           StateVector stateVector) {
        terms.forEach(t -> t.setStateVector(stateVector));
        return new SumEquation(terms);
    }

    /** h = V_i : the measured quantity is a state variable, so the derivative is 1. */
    private record VoltageMagnitudeEquation(Variable<AcVariableType> variable, StateVector stateVector)
            implements MeasurementEquation {

        @Override
        public double eval() {
            return stateVector.get(variable.getRow());
        }

        @Override
        public Collection<Variable<AcVariableType>> variables() {
            return List.of(variable);
        }

        @Override
        public double der(Variable<AcVariableType> v) {
            return v.equals(variable) ? 1.0 : 0.0;
        }
    }

    /** h = sum of AC equation terms; a single flow term is the one-element case. */
    private static final class SumEquation implements MeasurementEquation {

        private final List<SingleEquationTerm<AcVariableType, AcEquationType>> terms;
        private final Set<Variable<AcVariableType>> variables = new LinkedHashSet<>();

        private SumEquation(List<SingleEquationTerm<AcVariableType, AcEquationType>> terms) {
            this.terms = terms;
            terms.forEach(t -> variables.addAll(t.getVariables()));
        }

        @Override
        public double eval() {
            double sum = 0;
            for (SingleEquationTerm<AcVariableType, AcEquationType> term : terms) {
                sum += term.eval();
            }
            return sum;
        }

        @Override
        public Collection<Variable<AcVariableType>> variables() {
            return variables;
        }

        @Override
        public double der(Variable<AcVariableType> variable) {
            double sum = 0;
            for (SingleEquationTerm<AcVariableType, AcEquationType> term : terms) {
                if (term.getVariables().contains(variable)) {
                    sum += term.der(variable);
                }
            }
            return sum;
        }
    }
}
