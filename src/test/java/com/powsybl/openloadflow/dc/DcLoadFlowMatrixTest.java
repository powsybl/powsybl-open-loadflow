/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.usefultoys.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
class DcLoadFlowMatrixTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowMatrixTest.class);

    private final MatrixFactory matrixFactory = new DenseMatrixFactory();

    private static void logNetwork(Network network) {
        network.getLoads().forEach(l -> LOGGER.info("{} : p = {}.", l.getId(), l.getP0()));
        network.getGenerators().forEach(g -> LOGGER.info("{} : p = {}.", g.getId(), g.getTargetP()));
        network.getBranchStream().forEach(b -> LOGGER.info("{} : p1 = {}, p2 = {}.",
                b.getId(), b.getTerminal1().getP(), b.getTerminal2().getP()));
    }

    @Test
    void buildDcMatrix() {
        Network network = EurostagTutorialExample1Factory.create();

        logNetwork(network);

        List<LfNetwork> lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.get(0);

        EquationSystem<DcVariableType, DcEquationType> equationSystem = new DcEquationSystemCreator(mainNetwork).create(false);

        for (LfBus b : mainNetwork.getBuses()) {
            equationSystem.createEquation(b.getNum(), DcEquationType.BUS_TARGET_P);
            equationSystem.getVariableSet().getVariable(b.getNum(), DcVariableType.BUS_PHI);
        }

        DcLoadFlowEngine.initStateVector(mainNetwork, equationSystem, new UniformValueVoltageInitializer());
        try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
            ps.println("X=");
            Matrix.createFromColumn(equationSystem.getStateVector().get(), new DenseMatrixFactory())
                    .print(ps, equationSystem.getColumnNames(mainNetwork), null);
        }

        try (var j = new JacobianMatrix<>(equationSystem, matrixFactory)) {
            try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
                ps.println("J=");
                j.getMatrix().print(ps, equationSystem.getRowNames(mainNetwork), equationSystem.getColumnNames(mainNetwork));
            }

            try (DcTargetVector targets = new DcTargetVector(mainNetwork, equationSystem)) {
                try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
                    ps.println("TGT=");
                    Matrix.createFromColumn(targets.getArray(), matrixFactory)
                            .print(ps, equationSystem.getRowNames(mainNetwork), null);
                }

                double[] dx = Arrays.copyOf(targets.getArray(), targets.getArray().length);
                try (LUDecomposition lu = j.getMatrix().decomposeLU()) {
                    lu.solveTransposed(dx);
                }

                assertEquals(0d, dx[0], 1E-14d);
                assertEquals(-0.04383352433493455d, dx[1], 1E-14d);
                assertEquals(-0.11239308112163815d, dx[2], 1E-14d);
                assertEquals(-0.2202418845341654d, dx[3], 1E-14d);

                Networks.resetState(network);
                DcLoadFlowEngine.updateNetwork(mainNetwork, equationSystem, dx);
            }
        }

        logNetwork(network);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();

        lfNetworks = Networks.load(network, new FirstSlackBusSelector());
        mainNetwork = lfNetworks.get(0);

        equationSystem = new DcEquationSystemCreator(mainNetwork).create(false);

        try (var j = new JacobianMatrix<>(equationSystem, matrixFactory)) {
            try (DcTargetVector targets = new DcTargetVector(mainNetwork, equationSystem)) {
                var dx = Arrays.copyOf(targets.getArray(), targets.getArray().length);
                try (LUDecomposition lu = j.getMatrix().decomposeLU()) {
                    lu.solveTransposed(dx);
                }

                Networks.resetState(network);
                DcLoadFlowEngine.updateNetwork(mainNetwork, equationSystem, dx);
            }
        }

        logNetwork(network);
    }

}
