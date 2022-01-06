/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.equations.VariableSet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class PerUnitTest {

    public static class RuntimeExceptionAnswer implements Answer<Object> {

        public Object answer(InvocationOnMock invocation) {
            throw new RuntimeException(invocation.getMethod().getName() + " is not stubbed");
        }
    }

    @Test
    void test() {
        double vb = 380;
        double zb = 380 * 380 * PerUnit.SB;

        VariableSet<AcVariableType> variableSet = new VariableSet<>();
        variableSet.getVariable(0, AcVariableType.BUS_V).setRow(0);
        variableSet.getVariable(0, AcVariableType.BUS_PHI).setRow(1);
        variableSet.getVariable(1, AcVariableType.BUS_V).setRow(2);
        variableSet.getVariable(1, AcVariableType.BUS_PHI).setRow(3);

        LfBranch branch = Mockito.mock(LfBranch.class, new RuntimeExceptionAnswer());
        PiModel piModel = Mockito.mock(PiModel.class, new RuntimeExceptionAnswer());
        LfBus bus1 = Mockito.mock(LfBus.class, new RuntimeExceptionAnswer());
        LfBus bus2 = Mockito.mock(LfBus.class, new RuntimeExceptionAnswer());
        Mockito.doReturn(piModel).when(branch).getPiModel();
        Mockito.doReturn(0).when(bus1).getNum();
        Mockito.doReturn(1).when(bus2).getNum();
        Mockito.doReturn(1d).when(piModel).getR1();
        Mockito.doReturn(324 * Math.pow(10, -6) * zb).when(piModel).getB1();
        Mockito.doReturn(186 * Math.pow(10, -6) * zb).when(piModel).getB2();
        Mockito.doReturn(111 * Math.pow(10, -6) * zb).when(piModel).getG1();
        Mockito.doReturn(222 * Math.pow(10, -6) * zb).when(piModel).getG2();
        Mockito.doReturn(0.1 / zb).when(piModel).getR();
        Mockito.doReturn(3 / zb).when(piModel).getX();
        Mockito.doReturn(Math.hypot(0.1, 3) / zb).when(piModel).getZ();
        Mockito.doReturn(Math.atan2(0.1, 3)).when(piModel).getKsi();
        Mockito.doReturn(0d).when(piModel).getA1();

        ClosedBranchSide1ActiveFlowEquationTerm p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, false, false);
        StateVector stateVector = new StateVector();
        p1.setStateVector(stateVector);
        double[] x = new double[4];
        x[0] = 405 / vb;
        x[1] = 0.045;
        x[2] = 404 / vb;
        x[3] = 0.0297;
        stateVector.set(x);
        assertEquals(856.4176570806668, p1.eval() / PerUnit.SB, 0d);
    }

    @Test
    void testBaseCurrent() {
        // silly test to check the formulae:
        // when P = SB and v = vnom, i should be equal to base current
        double p = PerUnit.SB;
        double v = 400;
        // factor 1000 because p is in MW and V in kV, so :
        // p = 1000 * sqrt(3) * v * i
        double i = 1000 * p / (Math.sqrt(3) * v);
        double ib = PerUnit.ib(400);
        assertEquals(i, ib);
    }
}
