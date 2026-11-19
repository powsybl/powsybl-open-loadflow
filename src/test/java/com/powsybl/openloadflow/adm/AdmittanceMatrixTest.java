/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.adm;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PiModel;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import com.powsybl.openloadflow.test.networks.ShuntNetworkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class AdmittanceMatrixTest {

    @Test
    void test() {
        Network network = EurostagTutorialExample1Factory.create();
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        var ySystem = AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        try (var y = AdmittanceMatrix.create(ySystem, new DenseMatrixFactory())) {
            var yRef = new DenseMatrix(8, 8, new double[] {
                    3.4570637119113563, -144.0028305895698, -3.2842105263157895, 136.80268906009132, 0.0, 0.0, 0.0, 0.0,
                    144.0028305895698, 3.4570637119113563, -136.80268906009132, -3.2842105263157895, 0.0, 0.0, 0.0, 0.0,
                    -3.2842105263157895, 136.80268906009132, 11.010710382513661, -216.20298481473702, -7.890710382513662, 86.79781420765028, 0.0, 0.0,
                    -136.80268906009132, -3.2842105263157895, 216.20298481473702, 11.010710382513661, -86.79781420765028, -7.890710382513662, 0.0, 0.0,
                    0.0, 0.0, -7.890710382513662, 86.79781420765028, 8.540588654886903, -141.94049103976127, -0.649012633744856, 55.6258682851227,
                    0.0, 0.0, -86.79781420765028, -7.890710382513662, 141.94049103976127, 8.540588654886903, -55.6258682851227, -0.649012633744856,
                    0.0, 0.0, 0.0, 0.0, -0.649012633744856, 55.6258682851227, 0.6481481481481483, -55.55177456269486,
                    0.0, 0.0, 0.0, 0.0, -55.6258682851227, -0.649012633744856, 55.55177456269486, 0.6481481481481483
            }).transpose();
            assertEquals(yRef, y.getMatrix());

            LfBus ngen = lfNetwork.getBusById("VLGEN_0");
            LfBus nhv1 = lfNetwork.getBusById("VLHV1_0");
            LfBus nhv2 = lfNetwork.getBusById("VLHV2_0");
            LfBus nload = lfNetwork.getBusById("VLLOAD_0");

            assertEquals(136.842, 1.0 / y.getZ(ngen, nhv1).abs(), 1e-3);
            assertEquals(87.155, 1.0 / y.getZ(nhv1, nhv2).abs(), 1e-3);
            assertEquals(55.629, 1.0 / y.getZ(nhv2, nload).abs(), 1e-3);
            assertEquals(28.582, 1.0 / y.getZ(ngen, nload).abs(), 1e-3);
        }
    }

    @Test
    void testWithShunt() {
        Network network = ShuntNetworkFactory.create();
        network.getShuntCompensator("SHUNT").setSectionCount(1);
        LfNetwork lfNetwork = LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).get(0);
        var ySystem = AdmittanceEquationSystem.create(lfNetwork, new VariableSet<>());
        try (var y = AdmittanceMatrix.create(ySystem, new DenseMatrixFactory())) {
            var yRef = new DenseMatrix(6, 6, new double[]{
                    160.00000000000003, 480.0000000000001, -160.00000000000003, -480.0000000000001, 0.0, 0.0,
                    -480.0000000000001, 160.00000000000003, 480.0000000000001, -160.00000000000003, 0.0, 0.0,
                    -160.00000000000003, -480.0000000000001, 320.00000000000006, 960.0000000000002, -160.00000000000003, -480.0000000000001,
                    480.0000000000001, -160.00000000000003, -960.0000000000002, 320.00000000000006, 480.0000000000001, -160.00000000000003,
                    0.0, 0.0, -160.00000000000003, -480.0000000000001, 160.00000000000003, 478.4000000000001,
                    0.0, 0.0, 480.0000000000001, -160.00000000000003, -478.4000000000001, 160.00000000000003,
            });
            assertEquals(yRef, y.getMatrix());
        }
    }

    private static Network createNetworkWithPhaseShifter() {
        Network network = PhaseShifterTestCaseFactory.create();
        PhaseTapChanger ptc = network.getTwoWindingsTransformer("PS1").getPhaseTapChanger();
        ptc.setTapPosition(ptc.getHighTapPosition()); // the phase shifter is not regulating
        return network;
    }

    private static LfNetwork load(Network network) {
        return LfNetwork.load(network, new LfNetworkLoaderImpl(), new FirstSlackBusSelector()).getFirst();
    }

    /**
     * Get the admittance Y(i, j) of the nodal equation I = Y * U from the matrix. The matrix is real: its rows are the
     * variables (vx, vy) and its columns are the equations (ix, iy), with ix = Re(Y) * vx - Im(Y) * vy
     * and iy = Im(Y) * vx + Re(Y) * vy.
     */
    private static Complex getY(AdmittanceEquationSystem ySystem, VariableSet<AdmittanceVariableType> variableSet, DenseMatrix matrix,
                                LfBus busI, LfBus busJ) {
        int ixColumn = ySystem.getEquationSystem().getEquation(busI.getNum(), AdmittanceEquationType.BUS_ADM_IX).orElseThrow().getColumn();
        int iyColumn = ySystem.getEquationSystem().getEquation(busI.getNum(), AdmittanceEquationType.BUS_ADM_IY).orElseThrow().getColumn();
        int vxRow = variableSet.getVariable(busJ.getNum(), AdmittanceVariableType.BUS_ADM_VX).getRow();
        return new Complex(matrix.get(vxRow, ixColumn), matrix.get(vxRow, iyColumn));
    }

    private static void assertComplexEquals(Complex expected, Complex actual, double delta) {
        assertEquals(expected.getReal(), actual.getReal(), delta);
        assertEquals(expected.getImaginary(), actual.getImaginary(), delta);
    }

    /**
     * A phase shifter makes the matrix asymmetric: Y12 = -rho * e^(-j * alpha) * y and Y21 = -rho * e^(j * alpha) * y,
     * where alpha is the phase shift of the Pi model, in radians, as in the AC equations of the branch.
     */
    @Test
    void testWithPhaseShifter() {
        Network network = createNetworkWithPhaseShifter();
        LfNetwork lfNetwork = load(network);
        LfBranch ps1 = lfNetwork.getBranchById("PS1");
        PiModel piModel = ps1.getPiModel();
        double alpha = piModel.getA1();
        // the phase shift of the Pi model is the one of the current tap, in radians
        assertEquals(Math.toRadians(network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().getCurrentStep().getAlpha()), alpha, 1e-12);
        var variableSet = new VariableSet<AdmittanceVariableType>();
        var ySystem = AdmittanceEquationSystem.create(lfNetwork, variableSet);
        try (var y = AdmittanceMatrix.create(ySystem, new DenseMatrixFactory())) {
            DenseMatrix matrix = y.getMatrix().toDense();
            Complex minusRhoY = new Complex(piModel.getR(), piModel.getX()).reciprocal().multiply(-piModel.getR1());
            assertComplexEquals(minusRhoY.multiply(ComplexUtils.polar2Complex(1, -alpha)),
                    getY(ySystem, variableSet, matrix, ps1.getBus1(), ps1.getBus2()), 1e-9);
            assertComplexEquals(minusRhoY.multiply(ComplexUtils.polar2Complex(1, alpha)),
                    getY(ySystem, variableSet, matrix, ps1.getBus2(), ps1.getBus1()), 1e-9);
        }
    }
}
