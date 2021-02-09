/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.InMemoryPlatformConfig;
import com.powsybl.commons.config.MapModuleConfig;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.config.YamlModuleConfigRepository;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.extensions.SlackTerminal;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.powsybl.openloadflow.util.ParameterConstants.*;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
class OpenLoadFlowParametersTest {

    private InMemoryPlatformConfig platformConfig;

    private FileSystem fileSystem;

    @BeforeEach
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        platformConfig = new InMemoryPlatformConfig(fileSystem);

        MapModuleConfig lfModuleConfig = platformConfig.createModuleConfig("load-flow-default-parameters");
        lfModuleConfig.setStringProperty("voltageInitMode", LoadFlowParameters.VoltageInitMode.DC_VALUES.toString());
        lfModuleConfig.setStringProperty("transformerVoltageControlOn", Boolean.toString(true));
        lfModuleConfig.setStringProperty("balanceType", LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD.toString());
        lfModuleConfig.setStringProperty("dc", Boolean.toString(true));
    }

    @AfterEach
    public void tearDown() throws IOException {
        fileSystem.close();
    }

    @Test
    void testConfig() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        olfModuleConfig.setStringProperty("slackBusSelectorType", "First");

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);

        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES, parameters.getVoltageInitMode());
        assertTrue(parameters.isTransformerVoltageControlOn());
        assertEquals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD, parameters.getBalanceType());
        assertTrue(parameters.isDc());
        assertTrue(parameters.isDistributedSlack());

        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertTrue(olfParameters.getSlackBusSelector() instanceof FirstSlackBusSelector);

        assertTrue(olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
        assertTrue(olfParameters.hasVoltageRemoteControl());
        assertEquals(LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
    }

    @Test
    void testDefaultOpenLoadflowConfig() {
        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);

        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES, parameters.getVoltageInitMode());
        assertTrue(parameters.isTransformerVoltageControlOn());
        assertEquals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD, parameters.getBalanceType());
        assertTrue(parameters.isDc());
        assertTrue(parameters.isDistributedSlack());

        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SLACK_BUS_SELECTOR_DEFAULT_VALUE, olfParameters.getSlackBusSelector());
        assertEquals(VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE, olfParameters.hasVoltageRemoteControl());
        assertEquals(LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
        assertEquals(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE, olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());

        assertEquals(DC_USE_TRANSFORMER_RATIO_DEFAULT_VALUE, olfParameters.isDcUseTransformerRatio());
    }

    @Test
    void testInvalidOpenLoadflowConfig() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        // Invalid -> SlackBusSelectorParametersReader cannot be found
        olfModuleConfig.setStringProperty("slackBusSelectorType", "Invalid");

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);

        // Default value are selected and error log message is printed
        assertEquals(SLACK_BUS_SELECTOR_DEFAULT_VALUE, olfParameters.getSlackBusSelector());
    }

    @Test
    void testFirstSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configFirstSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configFirstSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(FirstSlackBusSelector.class, olfParameters.getSlackBusSelector().getClass());
    }

    @Test
    void testMostMeshedSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configMostMeshedSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configMostMeshedSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(MostMeshedSlackBusSelector.class, olfParameters.getSlackBusSelector().getClass());
    }

    @Test
    void testNameSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configNameSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configNameSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(NameSlackBusSelector.class, olfParameters.getSlackBusSelector().getClass());
        LfNetwork lfNetwork = LfNetwork.load(EurostagTutorialExample1Factory.create(), olfParameters.getSlackBusSelector()).get(0);
        PowsyblException thrown = assertThrows(PowsyblException.class, lfNetwork::getSlackBus);
        assertEquals("Slack bus '???' not found", thrown.getMessage());
    }

    @Test
    void testMaxIterationReached() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setWriteSlackBus(true);
        Network network = EurostagTutorialExample1Factory.create();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        network.getVoltageLevelStream().forEach(vl -> vl.setNominalV(21));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testIsWriteSlackBus() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setWriteSlackBus(true);
        Network network = EurostagTutorialExample1Factory.create();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new SparseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(network.getVoltageLevel("VLHV1").getExtension(SlackTerminal.class).getTerminal().getBusView().getBus().getId(),
                result.getComponentResults().get(0).getSlackBusId());
    }
}
