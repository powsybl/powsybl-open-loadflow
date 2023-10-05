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
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoop;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertVoltageEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
class OpenLoadFlowParametersTest {

    private InMemoryPlatformConfig platformConfig;

    private FileSystem fileSystem;

    public static final double DELTA_MISMATCH = 1E-4d;

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
        olfModuleConfig.setStringProperty("slackBusSelectionMode", "FIRST");

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);

        assertEquals(LoadFlowParameters.VoltageInitMode.DC_VALUES, parameters.getVoltageInitMode());
        assertTrue(parameters.isTransformerVoltageControlOn());
        assertEquals(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD, parameters.getBalanceType());
        assertTrue(parameters.isDc());
        assertTrue(parameters.isDistributedSlack());

        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SlackBusSelectionMode.FIRST, olfParameters.getSlackBusSelectionMode());
        assertFalse(olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
        assertTrue(olfParameters.hasVoltageRemoteControl());
        assertEquals(OpenLoadFlowParameters.LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
        assertEquals(LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE, olfParameters.getMinPlausibleTargetVoltage());
        assertEquals(LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE, olfParameters.getMaxPlausibleTargetVoltage());
        assertEquals(LfNetworkParameters.SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE, olfParameters.getSlackBusCountryFilter());
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
        assertEquals(OpenLoadFlowParameters.SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE, olfParameters.getSlackBusSelectionMode());
        assertEquals(OpenLoadFlowParameters.VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE, olfParameters.hasVoltageRemoteControl());
        assertEquals(OpenLoadFlowParameters.LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE, olfParameters.getLowImpedanceBranchMode());
        assertEquals(OpenLoadFlowParameters.THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE, olfParameters.isThrowsExceptionInCaseOfSlackDistributionFailure());
        assertEquals(OpenLoadFlowParameters.SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE, olfParameters.getSlackBusPMaxMismatch(), 0.0);
        assertEquals(OpenLoadFlowParameters.REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE, olfParameters.hasReactivePowerRemoteControl());
    }

    @Test
    void testInvalidOpenLoadflowConfig() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        olfModuleConfig.setStringProperty("slackBusSelectionMode", "Invalid");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> LoadFlowParameters.load(platformConfig));
        assertEquals("No enum constant com.powsybl.openloadflow.network.SlackBusSelectionMode.Invalid", exception.getMessage());
    }

    @Test
    void testInvalidOpenLoadflowConfigNewtonRaphson() {
        MapModuleConfig olfModuleConfig = platformConfig.createModuleConfig("open-loadflow-default-parameters");
        olfModuleConfig.setStringProperty("newtonRaphsonStoppingCriteriaType", "Invalid");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> LoadFlowParameters.load(platformConfig));
        assertEquals("No enum constant com.powsybl.openloadflow.ac.nr.NewtonRaphsonStoppingCriteriaType.Invalid", exception.getMessage());

        OpenLoadFlowParameters openLoadFlowParameters = OpenLoadFlowParameters.create(new LoadFlowParameters());
        assertThrows(PowsyblException.class, () -> openLoadFlowParameters.setMaxAngleMismatch(-1));
        assertThrows(PowsyblException.class, () -> openLoadFlowParameters.setMaxVoltageMismatch(-1));
        assertThrows(PowsyblException.class, () -> openLoadFlowParameters.setMaxRatioMismatch(-1));
        assertThrows(PowsyblException.class, () -> openLoadFlowParameters.setMaxActivePowerMismatch(-1));
        assertThrows(PowsyblException.class, () -> openLoadFlowParameters.setMaxReactivePowerMismatch(-1));
    }

    @Test
    void testFirstSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configFirstSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configFirstSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SlackBusSelectionMode.FIRST, olfParameters.getSlackBusSelectionMode());
    }

    @Test
    void testMostMeshedSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configMostMeshedSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configMostMeshedSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SlackBusSelectionMode.MOST_MESHED, olfParameters.getSlackBusSelectionMode());
    }

    @Test
    void testNameSlackBusSelector() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configNameSlackBusSelector.yml");

        Files.copy(getClass().getResourceAsStream("/configNameSlackBusSelector.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(SlackBusSelectionMode.NAME, olfParameters.getSlackBusSelectionMode());
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(olfParameters.getSlackBusSelectionMode(), olfParameters.getSlackBusesIds(), olfParameters.getPlausibleActivePowerLimit(),
                olfParameters.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(), Collections.emptySet());
        List<LfNetwork> lfNetworks = Networks.load(EurostagTutorialExample1Factory.create(), slackBusSelector);
        LfNetwork lfNetwork = lfNetworks.get(0);
        assertEquals("VLHV1_0", lfNetwork.getSlackBus().getId()); // fallback to automatic method.
    }

    @Test
    void testMaxIterationReached() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setWriteSlackBus(true);
        Network network = EurostagTutorialExample1Factory.create();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));

        // Change the nominal voltage to have a target V distant enough but still plausible (in [0.8 1.2] in Pu), so that the NR diverges
        network.getVoltageLevel("VLGEN").setNominalV(100);
        network.getGenerator("GEN").setTargetV(120);

        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, result.getComponentResults().get(0).getStatus());
    }

    @Test
    void testIsWriteSlackBus() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        parameters.setWriteSlackBus(true);
        Network network = EurostagTutorialExample1Factory.create();
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(network.getVoltageLevel("VLHV1").getExtension(SlackTerminal.class).getTerminal().getBusView().getBus().getId(),
                result.getComponentResults().get(0).getSlackBusId());
    }

    @Test
    void testSetSlackBusPMaxMismatch() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertEquals(-0.004, result.getComponentResults().get(0).getSlackBusActivePowerMismatch(), DELTA_MISMATCH);

        parameters.getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.0001);
        LoadFlow.Runner loadFlowRunner2 = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        LoadFlowResult result2 = loadFlowRunner2.run(network, parameters);
        assertEquals(-1.8703e-5, result2.getComponentResults().get(0).getSlackBusActivePowerMismatch(), DELTA_MISMATCH);
    }

    @Test
    void testPlausibleTargetVoltage() {
        LoadFlowParameters parameters = LoadFlowParameters.load();
        Network network = EurostagTutorialExample1Factory.create();
        network.getGenerator("GEN").setTargetV(30.0);
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        loadFlowRunner.run(network, parameters);
        assertTrue(Double.isNaN(network.getGenerator("GEN").getRegulatingTerminal().getBusView().getBus().getV())); // no calculation
        parameters.getExtension(OpenLoadFlowParameters.class).setMaxPlausibleTargetVoltage(1.3);
        loadFlowRunner.run(network, parameters);
        assertEquals(30.0, network.getGenerator("GEN").getRegulatingTerminal().getBusView().getBus().getV(), DELTA_MISMATCH);
    }

    @Test
    void testLowImpedanceThreshold() throws IOException {
        Path cfgDir = Files.createDirectory(fileSystem.getPath("config"));
        Path cfgFile = cfgDir.resolve("configLowImpedanceThreshold.yml");

        Files.copy(getClass().getResourceAsStream("/configLowImpedanceThreshold.yml"), cfgFile);
        PlatformConfig platformConfig = new PlatformConfig(new YamlModuleConfigRepository(cfgFile), cfgDir);

        LoadFlowParameters parameters = LoadFlowParameters.load(platformConfig);
        OpenLoadFlowParameters olfParameters = parameters.getExtension(OpenLoadFlowParameters.class);
        assertEquals(1.0E-2, olfParameters.getLowImpedanceThreshold());
    }

    @Test
    void testAlwaysUpdateNetwork() {
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setTransformerVoltageControlOn(true)
                .setDistributedSlack(false);

        OpenLoadFlowParameters olfParameters = OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setMaxNewtonRaphsonIterations(2); // Force final status of following run to be MAX_ITERATION_REACHED
        assertFalse(olfParameters.isAlwaysUpdateNetwork()); // Default value of alwaysUpdateNetwork

        // Check the network is not updated if alwaysUpdateNetwork = false and final status = MAX_ITERATION_REACHED
        Network network = EurostagTutorialExample1Factory.create();
        var nload = network.getBusBreakerView().getBus("NLOAD");
        LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));
        loadFlowRunner.run(network, parameters);
        assertTrue(Double.isNaN(nload.getV()));

        // Check the network is updated if alwaysUpdateNetwork = true and final status = MAX_ITERATION_REACHED
        olfParameters.setAlwaysUpdateNetwork(true);
        assertTrue(olfParameters.isAlwaysUpdateNetwork());

        loadFlowRunner.run(network, parameters);
        assertVoltageEquals(158, nload);
    }

    @Test
    void testUpdateParameters() {
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put("slackBusSelectionMode", "FIRST");
        parametersMap.put("voltageRemoteControl", "true");
        parametersMap.put("reactivePowerRemoteControl", "false");
        OpenLoadFlowParameters parameters = OpenLoadFlowParameters.load(parametersMap);
        assertEquals(SlackBusSelectionMode.FIRST, parameters.getSlackBusSelectionMode());
        assertTrue(parameters.hasVoltageRemoteControl());
        assertFalse(parameters.hasReactivePowerRemoteControl());
        Map<String, String> updateParametersMap = new HashMap<>();
        updateParametersMap.put("slackBusSelectionMode", "MOST_MESHED");
        updateParametersMap.put("voltageRemoteControl", "false");
        updateParametersMap.put("maxNewtonRaphsonIterations", "10");
        parameters.update(updateParametersMap);
        assertEquals(SlackBusSelectionMode.MOST_MESHED, parameters.getSlackBusSelectionMode());
        assertFalse(parameters.hasVoltageRemoteControl());
        assertEquals(10, parameters.getMaxNewtonRaphsonIterations());
        assertFalse(parameters.hasReactivePowerRemoteControl());
    }

    @Test
    void testCompareParameters() {
        assertTrue(OpenLoadFlowParameters.equals(new LoadFlowParameters(), new LoadFlowParameters()));
        assertFalse(OpenLoadFlowParameters.equals(new LoadFlowParameters(), new LoadFlowParameters().setDc(true)));
        var p1 = new LoadFlowParameters();
        var p2 = new LoadFlowParameters();
        var pe1 = OpenLoadFlowParameters.create(p1);
        OpenLoadFlowParameters.create(p2);
        assertTrue(OpenLoadFlowParameters.equals(p1, p2));
        assertFalse(OpenLoadFlowParameters.equals(p1, new LoadFlowParameters()));
        assertFalse(OpenLoadFlowParameters.equals(new LoadFlowParameters(), p2));
        pe1.setMinRealisticVoltage(0.3);
        assertFalse(OpenLoadFlowParameters.equals(p1, p2));
    }

    @Test
    void testCloneParameters() {
        var p = new LoadFlowParameters();
        assertTrue(OpenLoadFlowParameters.equals(p, OpenLoadFlowParameters.clone(p)));
        var pe = OpenLoadFlowParameters.create(p);
        assertTrue(OpenLoadFlowParameters.equals(p, OpenLoadFlowParameters.clone(p)));
        pe.setMaxNewtonRaphsonIterations(20);
        assertTrue(OpenLoadFlowParameters.equals(p, OpenLoadFlowParameters.clone(p)));
        assertFalse(OpenLoadFlowParameters.equals(new LoadFlowParameters(), OpenLoadFlowParameters.clone(p)));
    }

    @Test
    void testToString() {
        OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();
        assertEquals("OpenLoadFlowParameters(slackBusSelectionMode=MOST_MESHED, slackBusesIds=[], throwsExceptionInCaseOfSlackDistributionFailure=false, voltageRemoteControl=true, lowImpedanceBranchMode=REPLACE_BY_ZERO_IMPEDANCE_LINE, loadPowerFactorConstant=false, plausibleActivePowerLimit=5000.0, newtonRaphsonStoppingCriteriaType=UNIFORM_CRITERIA, slackBusPMaxMismatch=1.0, maxActivePowerMismatch=0.01, maxReactivePowerMismatch=0.01, maxVoltageMismatch=1.0E-4, maxAngleMismatch=1.0E-5, maxRatioMismatch=1.0E-5, maxSusceptanceMismatch=1.0E-4, voltagePerReactivePowerControl=false, reactivePowerRemoteControl=false, maxNewtonRaphsonIterations=15, maxOuterLoopIterations=20, newtonRaphsonConvEpsPerEq=1.0E-4, voltageInitModeOverride=NONE, transformerVoltageControlMode=WITH_GENERATOR_VOLTAGE_CONTROL, shuntVoltageControlMode=WITH_GENERATOR_VOLTAGE_CONTROL, minPlausibleTargetVoltage=0.8, maxPlausibleTargetVoltage=1.2, minRealisticVoltage=0.5, maxRealisticVoltage=2.0, reactiveRangeCheckMode=MAX, lowImpedanceThreshold=1.0E-8, networkCacheEnabled=false, svcVoltageMonitoring=true, stateVectorScalingMode=NONE, maxSlackBusCount=1, debugDir=null, incrementalTransformerVoltageControlOuterLoopMaxTapShift=3, secondaryVoltageControl=false, reactiveLimitsMaxPqPvSwitch=3, phaseShifterControlMode=CONTINUOUS_WITH_DISCRETISATION, alwaysUpdateNetwork=false, mostMeshedSlackBusSelectorMaxNominalVoltagePercentile=95.0, reportedFeatures=[], slackBusCountryFilter=[], actionableSwitchesIds=[], asymmetrical=false, minNominalVoltageTargetVoltageCheck=20.0, reactivePowerDispatchMode=Q_EQUAL_PROPORTION, outerLoopNames=null, useActiveLimits=true, loadModel=false)",
                     parameters.toString());
    }

    @Test
    void testExplicitOuterLoopsParameter() {
        LoadFlowParameters parameters = new LoadFlowParameters()
                .setShuntCompensatorVoltageControlOn(true);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSecondaryVoltageControl(true);

        assertEquals(List.of("DistributedSlack", "SecondaryVoltageControl", "VoltageMonitoring", "ReactiveLimits", "ShuntVoltageControl"), OpenLoadFlowParameters.createOuterLoops(parameters, parametersExt).stream().map(OuterLoop::getType).toList());

        parametersExt.setOuterLoopNames(List.of("ReactiveLimits", "SecondaryVoltageControl"));
        assertEquals(List.of("ReactiveLimits", "SecondaryVoltageControl"), OpenLoadFlowParameters.createOuterLoops(parameters, parametersExt).stream().map(OuterLoop::getType).toList());

        parametersExt.setOuterLoopNames(ExplicitAcOuterLoopConfig.NAMES);
        PowsyblException e = assertThrows(PowsyblException.class, () -> OpenLoadFlowParameters.createOuterLoops(parameters, parametersExt));
        assertEquals("Multiple (2) outer loops with same type: ShuntVoltageControl", e.getMessage());

        parametersExt.setOuterLoopNames(List.of("ReactiveLimits", "Foo"));
        e = assertThrows(PowsyblException.class, () -> OpenLoadFlowParameters.createOuterLoops(parameters, parametersExt));
        assertEquals("Unknown outer loop 'Foo'", e.getMessage());

        assertEquals("Ordered explicit list of outer loop names, supported outer loops are IncrementalPhaseControl, DistributedSlack, IncrementalShuntVoltageControl, IncrementalTransformerVoltageControl, VoltageMonitoring, PhaseControl, ReactiveLimits, SecondaryVoltageControl, ShuntVoltageControl, SimpleTransformerVoltageControl, TransformerVoltageControl",
                OpenLoadFlowParameters.SPECIFIC_PARAMETERS.stream().filter(p -> p.getName().equals(OpenLoadFlowParameters.OUTER_LOOP_NAMES_PARAM_NAME)).findFirst().orElseThrow().getDescription());
    }
}
