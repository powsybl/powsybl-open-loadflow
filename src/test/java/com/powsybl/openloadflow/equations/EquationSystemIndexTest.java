/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.equations.EquationSystemIndexListener.ChangeType;
import com.powsybl.openloadflow.network.ElementType;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class EquationSystemIndexTest {

    public enum TestEquationType implements Quantity {
        X("x_", ElementType.BUS),
        Y("y_", ElementType.BUS);

        private final String symbol;

        private final ElementType elementType;

        TestEquationType(String symbol, ElementType elementType) {
            this.symbol = symbol;
            this.elementType = elementType;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }
    }

    public enum TestVariableType implements Quantity {
        A("a_", ElementType.BUS),
        B("b_", ElementType.BUS),
        C("c_", ElementType.BUS);

        private final String symbol;

        private final ElementType elementType;

        TestVariableType(String symbol, ElementType elementType) {
            this.symbol = symbol;
            this.elementType = elementType;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }
    }

    private final List<Pair<Quantity, ChangeType>> events = new ArrayList<>();

    @Test
    void test() {
        EquationSystem<TestVariableType, TestEquationType> equationSystem = new EquationSystem<>();
        equationSystem.getIndex().addListener(new EquationSystemIndexListener<>() {
            @Override
            public void onVariableChange(Variable<TestVariableType> variable, ChangeType changeType) {
                events.add(Pair.of(variable.getType(), changeType));
            }

            @Override
            public void onEquationChange(Equation<TestVariableType, TestEquationType> equation, ChangeType changeType) {
                events.add(Pair.of(equation.getType(), changeType));
            }
        });
        var a = equationSystem.getVariableSet().getVariable(0, TestVariableType.A);
        var b = equationSystem.getVariableSet().getVariable(0, TestVariableType.B);
        var c = equationSystem.getVariableSet().getVariable(0, TestVariableType.C);
        EquationTerm<TestVariableType, TestEquationType> aTerm = a.createTerm();
        EquationTerm<TestVariableType, TestEquationType>  bTerm = b.createTerm();
        var x = equationSystem.createEquation(0, TestEquationType.X)
                .addTerm(aTerm)
                .addTerm(bTerm);
        EquationTerm<TestVariableType, TestEquationType> aTerm2 = a.createTerm();
        EquationTerm<TestVariableType, TestEquationType>  cTerm = c.createTerm();
        var y = equationSystem.createEquation(0, TestEquationType.Y)
                .addTerm(aTerm2)
                .addTerm(cTerm);
        assertEquals(List.of(Pair.of(TestEquationType.X, ChangeType.ADDED),
                             Pair.of(TestVariableType.A, ChangeType.ADDED),
                             Pair.of(TestVariableType.B, ChangeType.ADDED),
                             Pair.of(TestEquationType.Y, ChangeType.ADDED),
                             Pair.of(TestVariableType.C, ChangeType.ADDED)),
                     events);
        events.clear();
        assertEquals(Set.of(x, y), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());

        // deactivate y
        y.setActive(false);
        assertEquals(List.of(Pair.of(TestVariableType.C, ChangeType.REMOVED),
                             Pair.of(TestEquationType.Y, ChangeType.REMOVED)),
                     events);
        events.clear();
        assertEquals(Set.of(x), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(a, b), equationSystem.getIndex().getSortedVariablesToFind());

        // reactivate y
        y.setActive(true);
        assertEquals(List.of(Pair.of(TestVariableType.C, ChangeType.ADDED),
                             Pair.of(TestEquationType.Y, ChangeType.ADDED)),
                     events);
        events.clear();
        assertEquals(Set.of(x, y), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());

        // deactivate c term
        cTerm.setActive(false);
        assertEquals(List.of(Pair.of(TestVariableType.C, ChangeType.REMOVED)),
                events);
        events.clear();
        assertEquals(Set.of(x, y), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(a, b), equationSystem.getIndex().getSortedVariablesToFind());

        // reactivate c term
        cTerm.setActive(true);
        assertEquals(List.of(Pair.of(TestVariableType.C, ChangeType.ADDED)),
                     events);
        events.clear();
        assertEquals(Set.of(x, y), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(a, b, c), equationSystem.getIndex().getSortedVariablesToFind());

        // deactivate a term
        aTerm.setActive(false);
        assertTrue(events.isEmpty());
        aTerm2.setActive(false);
        assertEquals(List.of(Pair.of(TestVariableType.A, ChangeType.REMOVED)),
                     events);
        events.clear();
        assertEquals(Set.of(x, y), equationSystem.getIndex().getSortedEquationsToSolve());
        assertEquals(Set.of(b, c), equationSystem.getIndex().getSortedVariablesToFind());
    }
}
