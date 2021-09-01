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
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class Equation<V extends Enum<V> & VariableType, E extends Enum<E> & VariableType> implements Evaluable, Comparable<Equation<V, E>> {

    /**
     * Bus or any other equipment id.
     */
    private final int num;

    private final E type;

    private final EquationSystem<V, E> equationSystem;

    private int column = -1;

    private Object data;

    /**
     * true if this equation term active, false otherwise
     */
    private boolean active = true;

    private EquationSystem.EquationUpdateType updateType;

    private final List<EquationTerm<V, E>> terms = new ArrayList<>();

    Equation(int num, E type, EquationSystem<V, E> equationSystem) {
        this(num, type, equationSystem, EquationSystem.EquationUpdateType.DEFAULT);
    }

    Equation(int num, E type, EquationSystem<V, E> equationSystem, EquationSystem.EquationUpdateType updateType) {
        this.num = num;
        this.type = Objects.requireNonNull(type);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.updateType = updateType;
    }

    public int getNum() {
        return num;
    }

    public E getType() {
        return type;
    }

    public EquationSystem<V, E> getEquationSystem() {
        return equationSystem;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (active != this.active) {
            this.active = active;
            equationSystem.notifyEquationChange(this, active ? EquationEventType.EQUATION_ACTIVATED : EquationEventType.EQUATION_DEACTIVATED);
        }
    }

    public EquationSystem.EquationUpdateType getUpdateType() {
        return updateType;
    }

    public void setUpdateType(EquationSystem.EquationUpdateType updateType) {
        this.updateType = updateType;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public <T> T getData() {
        return (T) data;
    }

    public Equation<V, E> addTerm(EquationTerm<V, E> term) {
        Objects.requireNonNull(term);
        terms.add(term);
        term.setEquation(this);
        equationSystem.addEquationTerm(term);
        equationSystem.notifyEquationTermChange(term, EquationTermEventType.EQUATION_TERM_ADDED);
        return this;
    }

    public Equation<V, E> addTerms(List<EquationTerm<V, E>> terms) {
        Objects.requireNonNull(terms);
        for (EquationTerm<V, E> term : terms) {
            addTerm(term);
        }
        return this;
    }

    public List<EquationTerm<V, E>> getTerms() {
        return terms;
    }

    private static double getBusTargetV(LfBus bus) {
        Objects.requireNonNull(bus);
        return bus.getDiscreteVoltageControl().filter(dvc -> bus.isDiscreteVoltageControlled())
            .map(DiscreteVoltageControl::getTargetValue)
            .orElse(getVoltageControlledTargetValue(bus).orElse(Double.NaN));
    }

    private static Optional<Double> getVoltageControlledTargetValue(LfBus bus) {
        return bus.getVoltageControl().filter(vc -> bus.isVoltageControlled()).map(vc -> {
            if (vc.getControllerBuses().stream().noneMatch(LfBus::isVoltageControllerEnabled)) {
                throw new IllegalStateException("None of the controller buses of bus '" + bus.getId() + "'has voltage control on");
            }
            return vc.getTargetValue();
        });
    }

    private static double getBranchA(LfBranch branch) {
        Objects.requireNonNull(branch);
        PiModel piModel = branch.getPiModel();
        return PiModel.A2 - piModel.getA1();
    }

    private static double getBranchTarget(LfBranch branch, DiscretePhaseControl.Unit unit) {
        Objects.requireNonNull(branch);
        Optional<DiscretePhaseControl> phaseControl = branch.getDiscretePhaseControl().filter(dpc -> branch.isPhaseControlled());
        if (phaseControl.isEmpty()) {
            throw new PowsyblException("Branch '" + branch.getId() + "' is not phase-controlled");
        }
        if (phaseControl.get().getUnit() != unit) {
            throw new PowsyblException("Branch '" + branch.getId() + "' has not a target in " + unit);
        }
        return phaseControl.get().getTargetValue();
    }

    private static double getReactivePowerDistributionTarget(LfNetwork network, int num, DistributionData data) {
        LfBus controllerBus = network.getBus(num);
        LfBus firstControllerBus = network.getBus(data.getFirstControllerElementNum());
        double c = data.getC();
        return c * (controllerBus.getLoadTargetQ() - controllerBus.getGenerationTargetQ())
                - firstControllerBus.getLoadTargetQ() - firstControllerBus.getGenerationTargetQ();
    }

    private static double getRho1DistributionTarget(LfNetwork network, int num, DistributionData data) {
        LfBranch controllerBranch = network.getBranch(num);
        LfBranch firstControllerBranch = network.getBranch(data.getFirstControllerElementNum());
        // as a first and very simple ratio distribution strategy, we keep the gap between the 2 ratios constant
        return controllerBranch.getPiModel().getR1() - firstControllerBranch.getPiModel().getR1();
    }

    private static double createBusWithSlopeTarget(LfBus bus, DistributionData data) {
        double slope = data.getC();
        return getBusTargetV(bus) - slope * (bus.getLoadTargetQ() - bus.getGenerationTargetQ());
    }

    void initTarget(LfNetwork network, double[] targets) {
        switch (type) {
            case BUS_P:
                targets[column] = network.getBus(num).getTargetP();
                break;

            case BUS_Q:
                targets[column] = network.getBus(num).getTargetQ();
                break;

            case BUS_V:
                targets[column] = getBusTargetV(network.getBus(num));
                break;

            case BUS_V_SLOPE:
                targets[column] = createBusWithSlopeTarget(network.getBus(num), getData());
                break;

            case BUS_PHI:
                targets[column] = 0;
                break;

            case BRANCH_P:
                targets[column] = getBranchTarget(network.getBranch(num), DiscretePhaseControl.Unit.MW);
                break;

            case BRANCH_I:
                targets[column] = getBranchTarget(network.getBranch(num), DiscretePhaseControl.Unit.A);
                break;

            case BRANCH_ALPHA1:
                targets[column] = network.getBranch(num).getPiModel().getA1();
                break;

            case BRANCH_RHO1:
                targets[column] = network.getBranch(num).getPiModel().getR1();
                break;

            case ZERO_Q:
                targets[column] = getReactivePowerDistributionTarget(network, num, getData());
                break;

            case ZERO_V:
                targets[column] = 0;
                break;

            case ZERO_PHI:
                targets[column] = getBranchA(network.getBranch(num));
                break;

            case ZERO_RHO1:
                targets[column] = getRho1DistributionTarget(network, num, getData());
                break;

            default:
                throw new IllegalStateException("Unknown state variable type: "  + type);
        }

        for (EquationTerm<V, E> term : terms) {
            if (term.isActive() && term.hasRhs()) {
                targets[column] -= term.rhs();
            }
        }
    }

    public void update(double[] x) {
        for (EquationTerm<V, E> term : terms) {
            if (term.isActive()) {
                term.update(x);
            }
        }
    }

    @Override
    public double eval() {
        double value = 0;
        for (EquationTerm<V, E> term : terms) {
            if (term.isActive()) {
                value += term.eval();
                if (term.hasRhs()) {
                    value -= term.rhs();
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
        if (obj instanceof Equation) {
            return compareTo((Equation) obj) == 0;
        }
        return false;
    }

    @Override
    public int compareTo(Equation<V, E> o) {
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
        List<EquationTerm<V, E>> activeTerms = terms.stream().filter(EquationTerm::isActive).collect(Collectors.toList());
        for (Iterator<EquationTerm<V, E>> it = activeTerms.iterator(); it.hasNext();) {
            EquationTerm<V, E> term = it.next();
            term.write(writer);
            if (it.hasNext()) {
                writer.write(" + ");
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Equation(num=").append(num)
                .append(", type=").append(type)
                .append(", column=").append(column).append(")");
        return builder.toString();
    }
}
