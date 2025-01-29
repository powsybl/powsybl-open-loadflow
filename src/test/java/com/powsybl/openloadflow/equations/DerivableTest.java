/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.network.ElementType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.Writer;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Didier Vidal {@literal <didier.vidal_externe at rte-france.com>}
 */
class DerivableTest {

    static class ToBeFilteredEquationTerm extends AbstractEquationTerm<AcVariableType, AcVariableType> {

        ElementType type;

        ToBeFilteredEquationTerm(ElementType type) {
            this.type = type;
        }

        @Override
        public ElementType getElementType() {
            return type;
        }

        @Override
        public int getElementNum() {
            return 0;
        }

        @Override
        public List<Variable<AcVariableType>> getVariables() {
            return Collections.emptyList();
        }

        @Override
        public double eval() {
            throw new IllegalStateException("eval should not be called in this conyext");
        }

        @Override
        public double der(Variable<AcVariableType> variable) {
            throw new IllegalStateException("eval should not be called in this conyext");
        }

        @Override
        public void write(Writer writer) {
            // empty
        }
    }

    static class MyBranchEquationTerm extends AbstractEquationTerm<AcVariableType, AcVariableType> {

        static final double VALUE = 123456;
        static final double DER = 987654321;

        @Override
        public ElementType getElementType() {
            return ElementType.BRANCH;
        }

        @Override
        public int getElementNum() {
            return 0;
        }

        @Override
        public List<Variable<AcVariableType>> getVariables() {
            return Collections.emptyList();
        }

        @Override
        public double eval() {
            return VALUE;
        }

        @Override
        public double der(Variable<AcVariableType> variable) {
            return DER;
        }

        @Override
        public void write(Writer writer) {
            // empty
        }
    }

    @Test
    public void testDelegateFunction() {
        EquationSystem<AcVariableType, AcVariableType> equationSystem = Mockito.mock(EquationSystem.class);
        Equation<AcVariableType, AcVariableType> equation = new Equation<>(0, AcVariableType.BUS_V, equationSystem);
        equation.addTerm(new ToBeFilteredEquationTerm(ElementType.BUS));
        equation.addTerm(new ToBeFilteredEquationTerm(ElementType.SHUNT_COMPENSATOR));
        equation.addTerm(new ToBeFilteredEquationTerm(ElementType.HVDC));
        EquationTerm<AcVariableType, AcVariableType> inactive = new ToBeFilteredEquationTerm(ElementType.BRANCH);
        equation.addTerm(inactive);
        inactive.setActive(false);
        equation.addTerm(new MyBranchEquationTerm());
        InjectionDerivable<AcVariableType> derivable = new InjectionDerivable<>(equation);
        // Check that only my term is called and that result is delegated to the active branch term
        assertEquals(-MyBranchEquationTerm.VALUE, derivable.eval());
        assertEquals(-MyBranchEquationTerm.DER, derivable.der(new Variable<>(0, AcVariableType.BUS_V)));
    }
}
