/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.matpower.converter.MatpowerImporter;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Properties;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertAngleEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jeanne Archambault {@literal <jeanne.archambault at artelys.com>}
 */

public class MatPowerTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    private OpenLoadFlowParameters parametersExt;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters();
        parametersExt = OpenLoadFlowParameters.create(parameters)
                .setAcSolverType(AcSolverType.KNITRO)
                ;
        // Sparse matrix solver only
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        // No OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
        parameters.getExtension(OpenLoadFlowParameters.class)
                .setSvcVoltageMonitoring(false);
    }
    @Test
    void IEEE14() {
        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
//        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case14"),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case14"));
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
        assertVoltageEquals(1.06, network.getBusView().getBus("VL-1_0"));
        assertVoltageEquals(1.045, network.getBusView().getBus("VL-2_0"));
        assertVoltageEquals(1.01, network.getBusView().getBus("VL-3_0"));
        assertVoltageEquals(1.0176, network.getBusView().getBus("VL-4_0"));
        assertVoltageEquals(1.0195, network.getBusView().getBus("VL-5_0"));
        assertVoltageEquals(1.07, network.getBusView().getBus("VL-5_1"));
        assertVoltageEquals(1.0615, network.getBusView().getBus("VL-4_1"));
        assertVoltageEquals(1.09, network.getBusView().getBus("VL-8_0"));
        assertVoltageEquals(1.0559, network.getBusView().getBus("VL-4_2"));
        assertVoltageEquals(1.0509, network.getBusView().getBus("VL-10_0"));
        assertVoltageEquals(1.0569, network.getBusView().getBus("VL-11_0"));
        assertVoltageEquals(1.0551, network.getBusView().getBus("VL-12_0"));
        assertVoltageEquals(1.0503, network.getBusView().getBus("VL-13_0"));
        assertVoltageEquals(1.0355, network.getBusView().getBus("VL-14_0"));
        assertAngleEquals(0, network.getBusView().getBus("VL-1_0"));
        assertAngleEquals(-4.982589, network.getBusView().getBus("VL-2_0"));
        assertAngleEquals(-12.725099, network.getBusView().getBus("VL-3_0"));
        assertAngleEquals(-10.312901, network.getBusView().getBus("VL-4_0"));
        assertAngleEquals(-8.773853, network.getBusView().getBus("VL-5_0"));
        assertAngleEquals(-14.220946, network.getBusView().getBus("VL-5_1"));
        assertAngleEquals(-13.359627, network.getBusView().getBus("VL-4_1"));
        assertAngleEquals(-13.359627, network.getBusView().getBus("VL-8_0"));
        assertAngleEquals(-14.938521, network.getBusView().getBus("VL-4_2"));
        assertAngleEquals(-15.097288, network.getBusView().getBus("VL-10_0"));
        assertAngleEquals(-14.790622, network.getBusView().getBus("VL-11_0"));
        assertAngleEquals(-15.075584, network.getBusView().getBus("VL-12_0"));
        assertAngleEquals(-15.156276, network.getBusView().getBus("VL-13_0"));
        assertAngleEquals(-16.033644, network.getBusView().getBus("VL-14_0"));
    }

    @Test
    void case1951Rte() {
        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case1951rte"),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case1951rte"));
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
    }

    @Test
    void case57() {
        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case57"),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case57"));
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
    }

    @Test
    void case1888Rte() {
        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case1888rte"),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case1888rte"));
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
    }
}
