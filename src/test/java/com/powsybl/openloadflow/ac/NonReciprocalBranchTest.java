/**
 * Copyright (c) 2026, Kick Stage d.o.o.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.SimplePiModel;
import com.powsybl.openloadflow.network.impl.LfNetworkLoaderImpl;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A branch whose pi model carries a side 2 series impedance: side 1 flows follow z12, side 2 flows z21, against the solved
 * bus voltages.
 *
 * @author Pascal Ehlert {@literal <pascal.ehlert at kickstage.com>}
 */
class NonReciprocalBranchTest {

    private static final double R = 0.0069;
    private static final double X = 0.0277;
    private static final double R21 = -0.0035;
    private static final double X21 = 0.0291;
    private static final double P_LOAD = 1;
    private static final double Q_LOAD = 0.2;

    private static Network create() {
        Network network = Network.create("non-reciprocal", "test");
        Substation s1 = network.newSubstation().setId("s1").add();
        VoltageLevel vl1 = s1.newVoltageLevel().setId("vl1").setNominalV(380).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl1.getBusBreakerView().newBus().setId("b1").add();
        vl1.newGenerator().setId("g1").setBus("b1").setConnectableBus("b1").setMinP(-500).setMaxP(500)
                .setTargetP(0).setTargetV(380 * 1.02).setVoltageRegulatorOn(true).add();
        Substation s2 = network.newSubstation().setId("s2").add();
        VoltageLevel vl2 = s2.newVoltageLevel().setId("vl2").setNominalV(380).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        vl2.getBusBreakerView().newBus().setId("b2").add();
        vl2.newLoad().setId("ld2").setBus("b2").setConnectableBus("b2").setP0(P_LOAD * 100).setQ0(Q_LOAD * 100).add();
        network.newLine().setId("l12").setBus1("b1").setConnectableBus1("b1").setBus2("b2").setConnectableBus2("b2")
                .setR(1).setX(1).setG1(0).setB1(0).setG2(0).setB2(0).add();
        return network;
    }

    private static Complex voltage(LfBus bus) {
        return Complex.valueOf(bus.getV(), 0).multiply(Complex.I.multiply(bus.getAngle()).exp());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void flowsFollowTheImpedanceOfTheirSide(boolean vectorized) {
        LoadFlowParameters parameters = new LoadFlowParameters().setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.create(parameters).setNewtonRaphsonConvEpsPerEq(1e-10);
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(parameters, parametersExt, new SparseMatrixFactory(),
                new EvenShiloachGraphDecrementalConnectivityFactory<>(), false, false).setVectorized(vectorized);
        LfNetwork lfNetwork = LfNetwork.load(create(), new LfNetworkLoaderImpl(), acParameters.getNetworkParameters()).get(0);
        LfBranch branch = lfNetwork.getBranchById("l12");
        ((SimplePiModel) branch.getPiModel()).setR(R).setX(X).setR21(R21).setX21(X21);

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {
            assertEquals(AcSolverStatus.CONVERGED, new AcloadFlowEngine(context).run().getSolverStatus());
        }

        Complex v1 = voltage(branch.getBus1());
        Complex v2 = voltage(branch.getBus2());
        Complex s1 = v1.multiply(v1.subtract(v2).divide(new Complex(R, X)).conjugate());
        Complex s2 = v2.multiply(v2.subtract(v1).divide(new Complex(R21, X21)).conjugate());
        assertEquals(s1.getReal(), branch.getP1().eval(), 1e-8);
        assertEquals(s1.getImaginary(), branch.getQ1().eval(), 1e-8);
        assertEquals(s2.getReal(), branch.getP2().eval(), 1e-8);
        assertEquals(s2.getImaginary(), branch.getQ2().eval(), 1e-8);
        assertEquals(-P_LOAD, branch.getP2().eval(), 1e-8);
        assertEquals(-Q_LOAD, branch.getQ2().eval(), 1e-8);
    }
}
