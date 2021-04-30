/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.Matrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.usefultoys.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
class DcLoadFlowMatrixTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowMatrixTest.class);

    private final MatrixFactory matrixFactory = new DenseMatrixFactory();

    private static void logNetwork(Network network) {
        network.getLoads().forEach(l ->  LOGGER.info("{} : p = {}.", l.getId(), l.getP0()));
        network.getGenerators().forEach(g ->  LOGGER.info("{} : p = {}.", g.getId(), g.getTargetP()));
        network.getBranchStream().forEach(b -> LOGGER.info("{} : p1 = {}, p2 = {}.",
                b.getId(), b.getTerminal1().getP(), b.getTerminal2().getP()));
    }

    @Test
    void buildDcMatrix() {
        Network network = EurostagTutorialExample1Factory.create();

        logNetwork(network);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new FirstSlackBusSelector());
        LfNetwork mainNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();

        VariableSet variableSet = new VariableSet();
        DcEquationSystemCreationParameters creationParameters = new DcEquationSystemCreationParameters(true, false, false, true);
        EquationSystem equationSystem = DcEquationSystem.create(mainNetwork, variableSet, creationParameters);

        for (LfBus b : mainNetwork.getBuses()) {
            equationSystem.createEquation(b.getNum(), EquationType.BUS_P);
            variableSet.getVariable(b.getNum(), VariableType.BUS_PHI);
        }

        double[] x = equationSystem.createStateVector(new UniformValueVoltageInitializer());
        try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
            ps.println("X=");
            Matrix.createFromColumn(x, new DenseMatrixFactory())
                    .print(ps, equationSystem.getColumnNames(), null);
        }

        equationSystem.updateEquations(x);

        Matrix j = new JacobianMatrix(equationSystem, matrixFactory).getMatrix();
        try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
            ps.println("J=");
            j.print(ps, equationSystem.getRowNames(), equationSystem.getColumnNames());
        }

        double[] targets = TargetVector.createArray(mainNetwork, equationSystem);
        try (PrintStream ps = LoggerFactory.getInfoPrintStream(LOGGER)) {
            ps.println("TGT=");
            Matrix.createFromColumn(targets, matrixFactory)
                    .print(ps, equationSystem.getRowNames(), null);
        }

        double[] dx = Arrays.copyOf(targets, targets.length);
        try (LUDecomposition lu = j.decomposeLU()) {
            lu.solveTransposed(dx);
        }

        assertEquals(0d, dx[0], 1E-14d);
        assertEquals(-0.04383352433493455d, dx[1], 1E-14d);
        assertEquals(-0.11239308112163815d, dx[2], 1E-14d);
        assertEquals(-0.2202418845341654d, dx[3], 1E-14d);

        Networks.resetState(network);
        equationSystem.updateNetwork(dx);

        logNetwork(network);

        network.getLine("NHV1_NHV2_1").getTerminal1().disconnect();
        network.getLine("NHV1_NHV2_1").getTerminal2().disconnect();

        lfNetworks = LfNetwork.load(network, new FirstSlackBusSelector());
        mainNetwork = lfNetworks.stream().filter(n -> n.getNumCC() == ComponentConstants.MAIN_NUM && n.getNumSC() == ComponentConstants.MAIN_NUM).findAny().orElseThrow();

        equationSystem = DcEquationSystem.create(mainNetwork, variableSet, creationParameters);

        j = new JacobianMatrix(equationSystem, matrixFactory).getMatrix();

        dx = Arrays.copyOf(targets, targets.length);
        try (LUDecomposition lu = j.decomposeLU()) {
            lu.solveTransposed(dx);
        }

        Networks.resetState(network);
        equationSystem.updateNetwork(dx);

        logNetwork(network);
    }

}
