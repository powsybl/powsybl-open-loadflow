package com.powsybl.openloadflow.ac;

import com.powsybl.commons.datasource.FileDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.matpower.converter.MatpowerImporter;
import com.powsybl.openloadflow.CompareKnitroToNewtonRaphson;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.AcSolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

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
                    .setAcSolverType(AcSolverType.KNITRO);
        // Sparse matrix solver only
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        // No OLs
        parameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        parameters.setDistributedSlack(false)
                .setUseReactiveLimits(false);
        parameters.getExtension(OpenLoadFlowParameters.class)
                .setSvcVoltageMonitoring(false);
        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE);
        parametersExt.setGradientComputationModeKnitro(1) //user routine
                .setGradientUserRoutineKnitro(2); // jac 2
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

    @Test
    void caseImportedFromMatFile() {

        // Load network from .mat file
        Properties properties = new Properties();
        // We want base voltages to be taken into account
        properties.put("matpower.import.ignore-base-voltage", false);
        Network network = new MatpowerImporter().importData(
                new FileDataSource(Path.of("C:", "Users", "jarchambault", "Downloads"), "case57" +
                        ""),
                NetworkFactory.findDefault(), properties);
        network.write("XIIDM", new Properties(), Path.of("C:", "Users", "jarchambault", "Downloads", "case57" +
                ""));

        Instant start = Instant.now();
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);
        Instant end = Instant.now();

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());
        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.NONE);

        LoadFlowResult resultCompare = CompareKnitroToNewtonRaphson.runComparison(loadFlowRunner, parameters, parametersExt, network);

        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
//        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.NONE);
        parametersExt.setAcSolverType(AcSolverType.NEWTON_RAPHSON);
        LoadFlowResult nrResult = loadFlowRunner.run(network, parameters);

        Duration duration = Duration.between(start, end);
        double durationInSeconds = duration.toSeconds();
        System.out.println("Loadflow took " + durationInSeconds + " seconds");
    }

    @Test
    void caseImportedFromXiidmFile() {

        // Load network from .XIIDM file
        String situ = "C:\\Users\\jarchambault\\Desktop\\IGM\\20260613T1330Z_1D_FR_.xiidm";
        Network network = Network.read(situ);

        Instant start = Instant.now();
        LoadFlowResult knitroResult = loadFlowRunner.run(network, parameters);
        Instant end = Instant.now();

        assertEquals(LoadFlowResult.ComponentResult.Status.CONVERGED, knitroResult.getComponentResults().get(0).getStatus());

        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES);
//        parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES);
        parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.NONE);
        parametersExt.setAcSolverType(AcSolverType.NEWTON_RAPHSON);
        LoadFlowResult nrResult = loadFlowRunner.run(network, parameters);

        Duration duration = Duration.between(start, end);
        double durationInSeconds = duration.toSeconds();
        System.out.println("Loadflow took " + durationInSeconds + " seconds");
    }
}
