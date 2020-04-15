/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Evaluable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Equation implements Evaluable, Comparable<Equation> {

    /**
     * Bus or any other equipment id.
     */
    private final int num;

    private final EquationType type;

    private final EquationSystem equationSystem;

    private int row = -1;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private final List<EquationTerm> terms = new ArrayList<>();

    Equation(int num, EquationType type, EquationSystem equationSystem) {
        this.num = num;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    public int getNum() {
        return num;
    }

    public EquationType getType() {
        return type;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            equationSystem.notifyListeners(this, active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
    }

    public Equation addTerm(EquationTerm term) {
        Objects.requireNonNull(term);
        terms.add(term);
        return this;
    }

    public List<EquationTerm> getTerms() {
        return terms;
    }

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        if (bus.getControllerBuses().isEmpty()) {
            return bus.getTargetV();
        } else {
            return bus.getControllerBuses()
                    .stream()
                    .filter(LfBus::hasVoltageControl)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("None of the controller buses of bus '" + bus.getId()
                            + "'has voltage control on"))
                    .getTargetV();
        }
    }

    private static double getBranchA(LfBranch branch) {
        Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        return piModel.getA2() - piModel.getA1();
    }

    private static double getBranchTarget(LfBranch branch, PhaseControl.Unit unit) {
        Objects.requireNonNull(branch);
        PhaseControl phaseControl = branch.getPhaseControl()
                .orElseThrow(() -> new PowsyblException("Branch '" + branch.getId() + "' has no phase control"));
        if (phaseControl.getUnit() != unit) {
            throw new PowsyblException("Branch '" + branch.getId() + "' has not a target in " + unit);
        }
        return phaseControl.getTargetValue();
    }

    void initTarget(LfNetwork network, double[] targets) {
        switch (type) {
            case BUS_P:
                targets[row] = network.getBus(num).getTargetP();
                break;

            case BUS_Q:
                targets[row] = network.getBus(num).getTargetQ();
                break;

            case BUS_V:
                targets[row] = getBusTargetV(network.getBus(num));
                break;

            case BUS_PHI:
                targets[row] = 0;
                break;

            case BRANCH_P:
                targets[row] = getBranchTarget(network.getBranch(num), PhaseControl.Unit.MW);
                break;

            case BRANCH_I:
                targets[row] = getBranchTarget(network.getBranch(num), PhaseControl.Unit.A);
                break;

            case ZERO_Q:
            case ZERO_V:
                targets[row] = 0;
                break;

            case ZERO_PHI:
                targets[row] = getBranchA(network.getBranch(num));
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: "  + type);
        }

        for (EquationTerm term : terms) {
            if (term.hasRhs()) {
                for (Variable variable : term.getVariables()) {
                    targets[row] -= term.rhs(variable);
                }
            }
        }
    }

    public void update(double[] x) {
        for (EquationTerm term : terms) {
            term.update(x);
        }
    }

    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm term : terms) {
            value += term.eval();
            if (term.hasRhs()) {
                for (Variable variable : term.getVariables()) {
                    value -= term.rhs(variable);
                }
            }
        }
        return value;
    }

    @Override
    public int hashCode() {
        return num + type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Equation)) {
            return false;
        }
        Equation other = (Equation) obj;
        return num == other.num && type == other.type;
    }

    @Override
    public int compareTo(Equation o) {
        if (o == this) {
            return 0;
        }
        int c = num - o.num;
        if (c == 0) {
            c = type.ordinal() - o.type.ordinal();
        }
        return c;
    }

    public void write(Writer writer) throws IOException {
        writer.write(type.getSymbol());
        writer.append(Integer.toString(num));
        writer.append(" = ");
        for (Iterator<EquationTerm> it = terms.iterator(); it.hasNext();) {
            EquationTerm term = it.next();
            term.write(writer);
            if (it.hasNext()) {
                writer.write(" + ");
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Equation(num=")
                .append(num);
        switch (type) {
            case BUS_P:
            case BUS_Q:
            case BUS_V:
            case BUS_PHI:
                LfBus bus = equationSystem.getNetwork().getBus(num);
                builder.append(", busId=").append(bus.getId());
                break;
            case BRANCH_P:
            case BRANCH_I:
                LfBranch branch = equationSystem.getNetwork().getBranch(num);
                builder.append(", branchId=").append(branch.getId());
                break;
            case ZERO_Q:
            case ZERO_V:
            case ZERO_PHI:
                break;
        }
        builder.append(", type=").append(type)
                .append(", row=").append(row).append(")");
        return builder.toString();
    }
}
