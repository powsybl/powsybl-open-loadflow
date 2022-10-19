/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.network.LfNetwork;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DefaultEquationEvaluator<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationEvaluator {

    private final LfNetwork network;

    private final EquationSystem<V, E> equationSystem;

    public DefaultEquationEvaluator(LfNetwork network, EquationSystem<V, E> equationSystem) {
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
    }

    @Override
    public double getP1(int branchNum) {
        return network.getBranch(branchNum).getP1().eval();
    }

    @Override
    public double getP2(int branchNum) {
        return network.getBranch(branchNum).getP2().eval();
    }

    @Override
    public double getQ1(int branchNum) {
        return network.getBranch(branchNum).getQ1().eval();
    }

    @Override
    public double getQ2(int branchNum) {
        return network.getBranch(branchNum).getQ2().eval();
    }

    @Override
    public double getV(int busNum) {
        return network.getBus(busNum).getV();
    }

    @Override
    public double getAngle(int busNum) {
        return network.getBus(busNum).getAngle();
    }

    @Override
    public double eval(int column) {
        return equationSystem.getIndex().getEquation(column).eval();
    }

    private static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> Map<Variable<V>, List<EquationTerm<V, E>>> indexTermsByVariable(Equation<V, E> eq) {
        Map<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = new TreeMap<>();
        for (EquationTerm<V, E> term : eq.getTerms()) {
            for (Variable<V> v : term.getVariables()) {
                termsByVariable.computeIfAbsent(v, k -> new ArrayList<>())
                        .add(term);
            }
        }
        return termsByVariable;
    }

    @Override
    public void der(int column, DerivativeHandler handler) {
        Equation<V, E> equation = equationSystem.getIndex().getEquation(column);
        Map<Variable<V>, List<EquationTerm<V, E>>> termsByVariable = indexTermsByVariable(equation);
        for (Map.Entry<Variable<V>, List<EquationTerm<V, E>>> e : termsByVariable.entrySet()) {
            Variable<V> v = e.getKey();
            int row = v.getRow();
            if (row != -1) {
                for (EquationTerm<V, E> term : e.getValue()) {
                    // create a derivative for all terms including de-activated ones because could be reactivated
                    // at jacobian update stage without any equation or variable index change
                    double value = term.isActive() ? term.der(v) : 0;
                    handler.onRow(row, value);
                }
            }
        }
    }
}
