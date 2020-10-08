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
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static com.powsybl.openloadflow.util.ParameterConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
public class OpenLoadFlowParametersTest {

    private InMemoryPlatformConfig platformConfig;

    private FileSystem fileSystem;

    @Before
    public void setUp() {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        platformConfig = new InMemoryPlatformConfig(fileSystem);

        MapModuleConfig lfModuleConfig = platformConfig.createModuleConfig("load-flow-default-parameters");
        lfModuleConfig.setStringProperty("voltageInitMode", LoadFlowParameters.VoltageInitMode.DC_VALUES.toString());
        lfModuleConfig.setStringProperty("transformerVoltageControlOn", Boolean.toString(true));
    }

    @After
    public void tearDown() throws IOException {
        fileSystem.close();
    }

    @Test
    public void testConfig() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        olfModuleConfig.setStringProperty(BALANCE_TYPE_PARAM_NAME, OpenLoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD.toString());
        olfModuleConfig.setStringProperty(DC_PARAM_NAME, Boolean.toString(true));
        olfModuleConfig.setStringProperty("slackBusSelectorType", "First");

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);

        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES, parameters.getVoltageInitMode());
        assertTrue(parameters.isTransformerVoltageControlOn());

        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(OpenLoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD, olfParameters.getBalanceType());
        assertTrue(olfParameters.isDc());
        assertTrue(olfParameters.getSlackBusSelector() instanceof FirstSlackBusSelector);
        assertTrue(olfParameters.isDistributedSlack());
        assertTrue(olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
        assertFalse(olfParameters.hasVoltageRemoteControl());
        assertEquals(LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
    }

    @Test
    public void testDefaultOpenLoadflowConfig() {
        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);

        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES, parameters.getVoltageInitMode());
        assertTrue(parameters.isTransformerVoltageControlOn());

        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SLACK_BUS_SELECTOR_DEFAULT_VALUE, olfParameters.getSlackBusSelector());
        assertEquals(BALANCE_TYPE_DEFAULT_VALUE, olfParameters.getBalanceType());
        assertEquals(DC_DEFAULT_VALUE, olfParameters.isDc());
        assertEquals(DISTRIBUTED_SLACK_DEFAULT_VALUE, olfParameters.isDistributedSlack());
        assertEquals(VOLTAGE_REMOTE_CONTROLE_DEFAULT_VALUE, olfParameters.hasVoltageRemoteControl());
        assertEquals(LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
        assertEquals(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE, olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
    }

    @Test
    public void testInvalidOpenLoadflowConfig() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        // Invalid -> SlackBusSelectorParametersReader cannot be found
        olfModuleConfig.setStringProperty("slackBusSelectorType", "Invalid");

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);

        // Default value are selected and error log message is printed
        assertEquals(SLACK_BUS_SELECTOR_DEFAULT_VALUE, olfParameters.getSlackBusSelector());
    }

    @Test
    public void testFirstSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configFirstSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configFirstSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(FirstSlackBusSelector.class, olfParameters.getSlackBusSelector().getClass());
    }

    @Test
    public void testMostMeshedSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configMostMeshedSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configMostMeshedSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(MostMeshedSlackBusSelector.class, olfParameters.getSlackBusSelector().getClass());
    }

    @Test
    public void testNameSlackBusSelector() throws IOException {
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
}
