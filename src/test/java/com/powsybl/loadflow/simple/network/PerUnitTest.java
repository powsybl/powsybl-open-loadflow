/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.network;

import com.powsybl.loadflow.simple.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.loadflow.simple.equations.VariableSet;
import com.powsybl.loadflow.simple.equations.VariableType;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PerUnitTest {

    public static class RuntimeExceptionAnswer implements Answer<Object> {

        public Object answer(InvocationOnMock invocation) {
            throw new RuntimeException(invocation.getMethod().getName() + " is not stubbed");
        }
    }

    @Test
    public void test() {
        double vb = 380;
        double zb = 380 * 380 * PerUnit.SB;

        VariableSet variableSet = new VariableSet();
        variableSet.getVariable(0, VariableType.BUS_V).setColumn(0);
        variableSet.getVariable(0, VariableType.BUS_PHI).setColumn(1);
        variableSet.getVariable(1, VariableType.BUS_V).setColumn(2);
        variableSet.getVariable(1, VariableType.BUS_PHI).setColumn(3);

        LfBranch branch = Mockito.mock(LfBranch.class, new RuntimeExceptionAnswer());
        LfBus bus1 = Mockito.mock(LfBus.class, new RuntimeExceptionAnswer());
        LfBus bus2 = Mockito.mock(LfBus.class, new RuntimeExceptionAnswer());
        Mockito.doReturn(0).when(bus1).getNum();
        Mockito.doReturn(1).when(bus2).getNum();
        Mockito.doReturn(1d).when(branch).r1();
        Mockito.doReturn(1d).when(branch).r2();
        Mockito.doReturn(324 * Math.pow(10, -6) * zb).when(branch).b1();
        Mockito.doReturn(186 * Math.pow(10, -6) * zb).when(branch).b2();
        Mockito.doReturn(111 * Math.pow(10, -6) * zb).when(branch).g1();
        Mockito.doReturn(222 * Math.pow(10, -6) * zb).when(branch).g2();
        Mockito.doReturn(zb / Math.hypot(0.1, 3)).when(branch).y();
        Mockito.doReturn(Math.atan2(0.1, 3)).when(branch).ksi();
        Mockito.doReturn(0d).when(branch).a1();
        Mockito.doReturn(0d).when(branch).a2();

        ClosedBranchSide1ActiveFlowEquationTerm p1 = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet);
        double[] x = new double[4];
        x[0] = 405 / vb;
        x[1] = 0.045;
        x[2] = 404 / vb;
        x[3] = 0.0297;
        p1.update(x);
        assertEquals(856.4176570806668, p1.eval() / PerUnit.SB, 0d);
    }
}
