/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.equations.vector.AcVectorEngine;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide2DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcApproximationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfVscConverterStationImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class EquationsTest {

    public static class RuntimeExceptionAnswer implements Answer<Object> {

        public Object answer(InvocationOnMock invocation) {
            throw new PowsyblException(invocation.getMethod().getName() + " is not stubbed");
        }
    }

    private static final RuntimeExceptionAnswer ANSWER = new RuntimeExceptionAnswer();

    private static final double R = 5.872576933488291E-4;
    private static final double X = 0.007711911135433123;
    private static final double Y = 129.29521139058275;
    private static final double KSI = 0.07600275710144264;
    private static final double G_1 = 0.08448324029284184;
    private static final double G_2 = 0.06483244848429284;
    private static final double B_1 = 0.13324220618233085;
    private static final double B_2 = 0.18320177723653615;
    private static final double V_1 = 1.0714396912858781;
    private static final double PH_1 = 0.1613653508202422;
    private static final double R_1 = 0.95455;
    private static final double A_1 = 0.324294234;
    private static final double V_2 = 1.0718794209362505;
    private static final double PH_2 = 0.18609589391040748;
    private static final double B = 0.2748383993949494;
    private static final double DROOP = 103.13240312354819;
    private static final double P_0 = 1.9280906677246095;
    private static final double LOSS_FACTOR_1 = 0.01100000023841858;
    private static final double LOSS_FACTOR_2 = 0.02400453453002384;
    private static final double G_SHUNT = 0.0000372472384299244;

    private static <V extends Enum<V> & Quantity, E extends Enum<E> & Quantity> double[] eval(EquationTerm<V, E> term, List<Variable<V>> variables, StateVector sv) {
        term.setStateVector(sv);
        double[] values = new double[variables.size() + 2];

        // equation value
        values[0] = term.eval();

        // all derivative values
        for (int i = 0; i < variables.size(); i++) {
            var v = variables.get(i);
            try {
                values[i + 1] = term.der(v);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // not supported or no implemented
                values[i + 1] = Double.NaN;
            }
        }

        // sensitivity value
        double[] one = new double[values.length];
        Arrays.fill(one, 1);
        DenseMatrix dx = new DenseMatrix(values.length, 1, one);
        try {
            values[values.length - 1] = term.calculateSensi(dx, 0);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            // not supported
            values[values.length - 1] = Double.NaN;
        }

        return values;
    }

    private AcVectorEngine acVectorEnginee;

    private LfBranch branch;

    private LfBus bus1;

    private LfBus bus2;

    @BeforeEach
    void setUp() {
        acVectorEnginee = new AcVectorEngine(null, null);
        branch = Mockito.mock(LfBranch.class, ANSWER);
        Mockito.doReturn(0).when(branch).getNum();
        Mockito.doReturn(false).when(branch).isDisabled();
        PiModel piModel = Mockito.mock(PiModel.class, ANSWER);
        Mockito.doReturn(piModel).when(branch).getPiModel();
        Mockito.doReturn(R).when(piModel).getR();
        Mockito.doReturn(X).when(piModel).getX();
        Mockito.doReturn(Y).when(piModel).getY();
        Mockito.doReturn(G_1).when(piModel).getG1();
        Mockito.doReturn(G_2).when(piModel).getG2();
        Mockito.doReturn(B_1).when(piModel).getB1();
        Mockito.doReturn(B_2).when(piModel).getB2();
        Mockito.doReturn(KSI).when(piModel).getKsi();
        Mockito.doReturn(R_1).when(piModel).getR1();
        Mockito.doReturn(A_1).when(piModel).getA1();

        bus1 = Mockito.mock(LfBus.class, ANSWER);
        bus2 = Mockito.mock(LfBus.class, ANSWER);
        Mockito.doReturn(0).when(bus1).getNum();
        Mockito.doReturn(1).when(bus2).getNum();
    }

    @Test
    void branchTest() {
        VariableSet<AcVariableType> variableSet = new VariableSet<>();
        var v1Var = variableSet.getVariable(0, AcVariableType.BUS_V);
        var ph1Var = variableSet.getVariable(0, AcVariableType.BUS_PHI);
        var v2Var = variableSet.getVariable(1, AcVariableType.BUS_V);
        var ph2Var = variableSet.getVariable(1, AcVariableType.BUS_PHI);
        var r1Var = variableSet.getVariable(0, AcVariableType.BRANCH_RHO1);
        var a1Var = variableSet.getVariable(0, AcVariableType.BRANCH_ALPHA1);
        var unknownVar = variableSet.getVariable(999, AcVariableType.BUS_V);

        var variables = List.of(v1Var, ph1Var, v2Var, ph2Var, r1Var, a1Var, unknownVar);
        v1Var.setRow(0);
        ph1Var.setRow(1);
        v2Var.setRow(2);
        ph2Var.setRow(3);
        r1Var.setRow(4);
        a1Var.setRow(5);
        unknownVar.setRow(6);

        var sv = new StateVector(new double[] {V_1, PH_1, V_2, PH_2, R_1, A_1, 0});

        // closed branch equations
        assertArrayEquals(new double[] {41.78173051479356, 48.66261692116701, 138.21343172859858, 29.31710523088579, -138.21343172859858, 54.62161149356045, 138.21343172859858, Double.NaN, 270.81476537421185},
                eval(new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {-3.500079625302254, 122.46444997806617, 31.42440177840898, -128.9449438332101, -31.42440177840898, 137.46086897280827, 31.42440177840898, Double.NaN, 162.40477689607334},
                eval(new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {39.13246485286217, -0.8052805161189096, 126.09926753871545, 37.31322159867258, -126.09926753871542, Double.NaN, 126.09926753871542, Double.NaN, Double.NaN},
                eval(new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {-40.6365773800554, -48.52391742324069, -131.8614376204652, -27.319027760225953, 131.8614376204652, -54.4659275092331, -131.8614376204652, Double.NaN, -262.1703103131649},
                eval(new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {16.04980301110306, -123.06939783256767, 51.99045110393844, 152.96594042215764, -51.99045110393844, -138.1398958886022, 51.99045110393844, Double.NaN, -56.2529021950738},
                eval(new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {40.7613721648136, -0.07246503940372644, 132.23571821183896, 38.10038077658943, -132.23571821183896, Double.NaN, 132.23571821183896, Double.NaN, Double.NaN},
                eval(new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee), variables, sv));

        // open branch equations
        assertArrayEquals(new double[] {0.1717595025847833, Double.NaN, Double.NaN, 0.3204828812456483, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide1ActiveFlowEquationTerm(branch, bus2, variableSet, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {-0.36364935827807376, Double.NaN, Double.NaN, -0.6785266162875639, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide1ReactiveFlowEquationTerm(branch, bus2, variableSet, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {0.37520249405559764, Double.NaN, Double.NaN, 0.3500416993992393, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide1CurrentMagnitudeEquationTerm(branch, bus2, variableSet, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {0.15652310047954035, Double.NaN, Double.NaN, 0.2920535601715773, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide2ActiveFlowEquationTerm(branch, bus2, variableSet, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {-0.331495628053771, Double.NaN, Double.NaN, -0.6185315653587614, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide2ReactiveFlowEquationTerm(branch, bus2, variableSet, acVectorEnginee), variables, sv));
        assertArrayEquals(new double[] {0.3420075216110214, Double.NaN, Double.NaN, 0.31907275662806295, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN},
                eval(new OpenBranchSide2CurrentMagnitudeEquationTerm(branch, bus2, variableSet, false, acVectorEnginee), variables, sv));

        // assert current equation is consistent with active and reactive power ones
        var p1Eq = new ClosedBranchSide1ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        p1Eq.setStateVector(sv);
        double p1 = p1Eq.eval();
        var q1Eq = new ClosedBranchSide1ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        q1Eq.setStateVector(sv);
        double q1 = q1Eq.eval();
        var i1Eq = new ClosedBranchSide1CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        i1Eq.setStateVector(sv);
        double i1 = i1Eq.eval();
        assertEquals(i1, Math.hypot(p1, q1) / V_1, 10e-14);

        var p2Eq = new ClosedBranchSide2ActiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        p2Eq.setStateVector(sv);
        double p2 = p2Eq.eval();
        var q2Eq = new ClosedBranchSide2ReactiveFlowEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        q2Eq.setStateVector(sv);
        double q2 = q2Eq.eval();
        var i2Eq = new ClosedBranchSide2CurrentMagnitudeEquationTerm(branch, bus1, bus2, variableSet, true, true, acVectorEnginee);
        i2Eq.setStateVector(sv);
        double i2 = i2Eq.eval();
        assertEquals(i2, Math.hypot(p2, q2) / V_2, 10e-14);
    }

    @Test
    void dcBranchTest() {
        VariableSet<DcVariableType> variableSet = new VariableSet<>();
        var ph1Var = variableSet.getVariable(0, DcVariableType.BUS_PHI);
        var ph2Var = variableSet.getVariable(1, DcVariableType.BUS_PHI);
        var a1Var = variableSet.getVariable(0, DcVariableType.BRANCH_ALPHA1);
        var unknownVar = variableSet.getVariable(999, DcVariableType.DUMMY_P);

        var variables = List.of(ph1Var, ph2Var, a1Var, unknownVar);
        ph1Var.setRow(0);
        ph2Var.setRow(1);
        a1Var.setRow(2);
        unknownVar.setRow(3);

        var sv = new StateVector(new double[]{PH_1, PH_2, A_1, 0});

        // closed branch equations
        assertArrayEquals(new double[] {37.07881433490131, 123.77606318805043, -123.77606318805043, 123.77606318805043, Double.NaN, 123.77606318805043},
                eval(ClosedBranchSide1DcFlowEquationTerm.create(branch, bus1, bus2, variableSet, true, true, DcApproximationType.IGNORE_R), variables, sv));
        assertArrayEquals(new double[] {-37.07881433490131, -123.77606318805043, 123.77606318805043, -123.77606318805043, Double.NaN, -123.77606318805043},
                eval(ClosedBranchSide2DcFlowEquationTerm.create(branch, bus1, bus2, variableSet, true, true, DcApproximationType.IGNORE_R), variables, sv));
    }

    @Test
    void shuntTest() {
        var shunt = Mockito.mock(LfShunt.class, new RuntimeExceptionAnswer());
        Mockito.doReturn(0).when(shunt).getNum();
        Mockito.doReturn(G_SHUNT).when(shunt).getG();
        Mockito.doReturn(false).when(shunt).isDisabled();

        var bus = Mockito.mock(LfBus.class, ANSWER);
        Mockito.doReturn(0).when(bus).getNum();

        VariableSet<AcVariableType> variableSet = new VariableSet<>();
        var vVar = variableSet.getVariable(0, AcVariableType.BUS_V);
        var bVar = variableSet.getVariable(0, AcVariableType.SHUNT_B);
        var unknownVar = variableSet.getVariable(999, AcVariableType.BUS_V);

        var variables = List.of(vVar, bVar, unknownVar);
        vVar.setRow(0);
        bVar.setRow(1);
        unknownVar.setRow(2);

        var sv = new StateVector(new double[] {V_1, B, 0});

        assertArrayEquals(new double[] {4.275919696380507E-5, 7.98163392892194E-5, Double.NaN, Double.NaN, Double.NaN},
                eval(new ShuntCompensatorActiveFlowEquationTerm(shunt, bus, variableSet), variables, sv));
        assertArrayEquals(new double[] {-0.3155098135679268, -0.588945539602459, -1.1479830120627779, Double.NaN, -1.7369285516652369},
                eval(new ShuntCompensatorReactiveFlowEquationTerm(shunt, bus, variableSet, true), variables, sv));
    }

    @Test
    void hvdcTest() {
        var hvdc = Mockito.mock(LfHvdc.class, new RuntimeExceptionAnswer());
        Mockito.doReturn(0).when(hvdc).getNum();
        Mockito.doReturn(false).when(hvdc).isDisabled();
        Mockito.doReturn(DROOP).when(hvdc).getDroop();
        Mockito.doReturn(P_0).when(hvdc).getP0();
        Mockito.doReturn(0d).when(hvdc).getR();
        Mockito.doReturn(Double.MAX_VALUE).when(hvdc).getPMaxFromCS1toCS2();
        Mockito.doReturn(Double.MAX_VALUE).when(hvdc).getPMaxFromCS2toCS1();
        LfVscConverterStationImpl station1 = Mockito.mock(LfVscConverterStationImpl.class, new RuntimeExceptionAnswer());
        LfVscConverterStationImpl station2 = Mockito.mock(LfVscConverterStationImpl.class, new RuntimeExceptionAnswer());
        Mockito.doReturn(station1).when(hvdc).getConverterStation1();
        Mockito.doReturn(station2).when(hvdc).getConverterStation2();
        Mockito.doReturn(LOSS_FACTOR_1).when(station1).getLossFactor();
        Mockito.doReturn(LOSS_FACTOR_2).when(station2).getLossFactor();

        var bus1 = Mockito.mock(LfBus.class, ANSWER);
        var bus2 = Mockito.mock(LfBus.class, ANSWER);
        Mockito.doReturn(0).when(bus1).getNum();
        Mockito.doReturn(1).when(bus2).getNum();

        VariableSet<AcVariableType> variableSet = new VariableSet<>();
        var hvdcPh1Var = variableSet.getVariable(0, AcVariableType.BUS_PHI);
        var hvdcPh2Var = variableSet.getVariable(1, AcVariableType.BUS_PHI);
        var unknownVar = variableSet.getVariable(999, AcVariableType.BUS_V);

        var variables = List.of(hvdcPh1Var, hvdcPh2Var, unknownVar);
        hvdcPh1Var.setRow(0);
        hvdcPh2Var.setRow(1);
        unknownVar.setRow(2);

        var sv = new StateVector(new double[] {PH_1, PH_2, 0});

        assertArrayEquals(new double[] {-144.1554855266458, 5906.983150087268, -5906.983150087268, Double.NaN, Double.NaN},
                eval(new HvdcAcEmulationSide1ActiveFlowEquationTerm(hvdc, bus1, bus2, variableSet), variables, sv));
        assertArrayEquals(new double[] {144.20596034441598, -5909.051430021139, 5909.051430021139, Double.NaN, Double.NaN},
                eval(new HvdcAcEmulationSide2ActiveFlowEquationTerm(hvdc, bus1, bus2, variableSet), variables, sv));
    }
}
