/**
 * Copyright (c) 2026, Elia Group (https://www.eliagroup.eu)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.se.equations;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide1ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2CurrentMagnitudeEquationTerm;
import com.powsybl.openloadflow.ac.equations.OpenBranchSide2ReactiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ShuntCompensatorReactiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.SingleEquationTerm;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfElement;
import com.powsybl.openloadflow.network.LfShunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the measurement function h(x) of a reading from the load flow's AC equation terms.
 *
 * @author Šime Pavlić {@literal <sime.pavlic at kickstage.com>}
 */
public final class MeasurementEquations {

    private MeasurementEquations() {
    }

    /**
     * The terms whose sum is the measurement function of a reading of this type on this element.
     *
     * @param type        what is measured
     * @param element     the bus or branch the reading is on
     * @param variableSet the variable set of the equation system that will own the terms
     * @return the terms of the measurement function
     * @throws IllegalArgumentException if the type is not a measurable quantity
     */
    public static List<SingleEquationTerm<AcVariableType, AcEquationType>> terms(
            AcEquationType type, LfElement element, VariableSet<AcVariableType> variableSet) {
        Objects.requireNonNull(element);
        return switch (type) {
            case BUS_TARGET_V -> List.of(variableSet.getVariable(element.getNum(), AcVariableType.BUS_V).createTerm());
            case BUS_TARGET_P -> busInjectionTerms((LfBus) element, true, variableSet);
            case BUS_TARGET_Q -> busInjectionTerms((LfBus) element, false, variableSet);
            case BRANCH_TARGET_P1, BRANCH_TARGET_P2, BRANCH_TARGET_Q1, BRANCH_TARGET_Q2,
                 BRANCH_TARGET_I1, BRANCH_TARGET_I2 -> List.of(branchTerm((LfBranch) element, type, variableSet));
            default -> throw new IllegalArgumentException(type + " is not a measurable quantity");
        };
    }

    /**
     * The flow or current term at one side of a branch. Tap position and phase shift are inputs
     * rather than unknowns, so the term has no derivative on either.
     *
     * @param branch      the branch, which must be connected at the side the type names
     * @param type        one of the {@code BRANCH_TARGET_P/Q/I} types, which carries the side
     * @param variableSet the variable set of the equation system that will own the term
     * @return the term
     * @throws IllegalArgumentException if the branch is open at the side the type names or the
     *                                  type is not a branch flow or current
     */
    public static SingleEquationTerm<AcVariableType, AcEquationType> branchTerm(
            LfBranch branch, AcEquationType type, VariableSet<AcVariableType> variableSet) {
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 != null && bus2 != null) {
            return switch (type) {
                case BRANCH_TARGET_P1 -> new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
                case BRANCH_TARGET_P2 -> new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
                case BRANCH_TARGET_Q1 -> new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
                case BRANCH_TARGET_Q2 -> new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
                case BRANCH_TARGET_I1 -> new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, false, false);
                case BRANCH_TARGET_I2 -> new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, false, false);
                default -> throw new IllegalArgumentException(type + " is not a branch flow or current");
            };
        }
        // open at the far end, the connected side still carries the branch's own charging and losses
        if (bus1 != null) {
            return switch (type) {
                case BRANCH_TARGET_P1 -> new OpenBranchSide2ActiveFlowEquationTerm(branch, bus1, variableSet);
                case BRANCH_TARGET_Q1 -> new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus1, variableSet);
                case BRANCH_TARGET_I1 -> new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, variableSet, false);
                default -> throw notConnected(branch, type);
            };
        }
        if (bus2 != null) {
            return switch (type) {
                case BRANCH_TARGET_P2 -> new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet);
                case BRANCH_TARGET_Q2 -> new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet);
                case BRANCH_TARGET_I2 -> new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, variableSet);
                default -> throw notConnected(branch, type);
            };
        }
        throw notConnected(branch, type);
    }

    private static IllegalArgumentException notConnected(LfBranch branch, AcEquationType type) {
        return new IllegalArgumentException(type + " cannot be read on branch " + branch.getId()
                + ", which is not connected at that side");
    }

    /**
     * Generation-positive: the flows leaving the bus into its branches plus its shunts. A static
     * var compensator is a device with a reading of its own, so the shunt {@code LfNetwork} splits
     * off one in standby is left out.
     */
    private static List<SingleEquationTerm<AcVariableType, AcEquationType>> busInjectionTerms(
            LfBus bus, boolean active, VariableSet<AcVariableType> variableSet) {
        List<SingleEquationTerm<AcVariableType, AcEquationType>> terms = new ArrayList<>();
        for (LfBranch branch : bus.getBranches()) {
            LfBus bus1 = branch.getBus1();
            TwoSides side = bus1 != null && bus1.getNum() == bus.getNum() ? TwoSides.ONE : TwoSides.TWO;
            terms.add(branchTerm(branch, active ? AcEquationType.branchTargetP(side) : AcEquationType.branchTargetQ(side), variableSet));
        }
        bus.getShunt().ifPresent(shunt -> terms.add(shuntTerm(shunt, bus, active, variableSet)));
        bus.getControllerShunt().ifPresent(shunt -> terms.add(shuntTerm(shunt, bus, active, variableSet)));
        return terms;
    }

    private static SingleEquationTerm<AcVariableType, AcEquationType> shuntTerm(LfShunt shunt, LfBus bus, boolean active,
                                                                               VariableSet<AcVariableType> variableSet) {
        return active
                ? new ShuntCompensatorActiveFlowEquationTerm(shunt, bus, variableSet)
                : new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, variableSet, false);
    }
}
