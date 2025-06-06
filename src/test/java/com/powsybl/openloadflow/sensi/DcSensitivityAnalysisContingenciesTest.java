/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.ComparisonUtils;
import com.powsybl.contingency.*;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControlAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.DebugUtil;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 */
class DcSensitivityAnalysisContingenciesTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testContingencyWithOneElementAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));
        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, result.getBranchFlow1SensitivityValue("g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getBranchFlow1SensitivityValue("g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, result.getBranchFlow1SensitivityValue("g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(5, result.getValues("l23").size());
        assertEquals(2d / 15d, result.getBranchFlow1SensitivityValue("l23", "g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6d, result.getBranchFlow1SensitivityValue("l23", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 15d, result.getBranchFlow1SensitivityValue("l23", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 15d, result.getBranchFlow1SensitivityValue("l23", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithOneElementAwayOnSlack() {
        //remove a branch connected to slack
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l12", new BranchContingency("l12")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, result.getBranchFlow1SensitivityValue("g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getBranchFlow1SensitivityValue("g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, result.getBranchFlow1SensitivityValue("g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(5, result.getValues("l12").size());
        assertEquals(-1d / 15d, result.getBranchFlow1SensitivityValue("l12", "g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l12", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.6d, result.getBranchFlow1SensitivityValue("l12", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 15d, result.getBranchFlow1SensitivityValue("l12", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l12", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithOneElementAwayOnSlackWithAdditionalFactors() {
        //remove a branch connected to slack
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l12", new BranchContingency("l12")));

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l13", "g2"),
                                                  createBranchFlowPerInjectionIncrease("l14", "g2"),
                                                  createBranchFlowPerInjectionIncrease("l34", "g2"),
                                                  createBranchFlowPerInjectionIncrease("l12", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(4, result.getPreContingencyValues().size());
        assertEquals(0.05d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(4, result.getValues("l12").size());
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l12", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 15d, result.getBranchFlow1SensitivityValue("l12", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l12", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0666, result.getBranchFlow1SensitivityValue("l12", "g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFlowFlowSensitivityValueFiltering() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.setFlowFlowSensitivityValueThreshold(0.1);

        List<Contingency> contingencies = List.of(new Contingency("l12", new BranchContingency("l12")));

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l13", "g2"),
                createBranchFlowPerInjectionIncrease("l14", "g2"),
                createBranchFlowPerInjectionIncrease("l34", "g2"),
                createBranchFlowPerInjectionIncrease("l12", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(3, result.getPreContingencyValues().size());
        assertEquals(2, result.getValues("l12").size());
    }

    @Test
    void testFunctionRefOnOneElement() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23", new BranchContingency("l23")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        Network networkDisconnected = FourBusNetworkFactory.create();
        networkDisconnected.getLine("l23").getTerminal1().disconnect();
        networkDisconnected.getLine("l23").getTerminal2().disconnect();
        runDcLf(networkDisconnected);

        for (Line line : networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList())) {
            assertEquals(line.getTerminal1().getP(), result.getBranchFlow1FunctionReferenceValue("l23", line.getId()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefOnTwoElement() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23+l34", new BranchContingency("l23"), new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        Network networkDisconnected = FourBusNetworkFactory.create();
        networkDisconnected.getLine("l23").getTerminal1().disconnect();
        networkDisconnected.getLine("l23").getTerminal2().disconnect();
        networkDisconnected.getLine("l34").getTerminal1().disconnect();
        networkDisconnected.getLine("l34").getTerminal2().disconnect();

        runDcLf(networkDisconnected);

        for (Line line : networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList())) {
            assertEquals(line.getTerminal1().getP(), result.getBranchFlow1FunctionReferenceValue("l23+l34", line.getId()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testContingencyWithTwoElementsAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l23+l34", new BranchContingency("l23"), new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g2")),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, result.getBranchFlow1SensitivityValue("g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, result.getBranchFlow1SensitivityValue("g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getBranchFlow1SensitivityValue("g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, result.getBranchFlow1SensitivityValue("g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, result.getBranchFlow1SensitivityValue("g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(5, result.getValues("l23+l34").size());
        assertEquals(0.2, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLine() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(14, result.getValues("l34").size());
        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(-4d / 3d, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 3d, result.getBranchFlow1FunctionReferenceValue("l34", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLineWithDistributedSlack() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(14, result.getValues("l34").size());
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void slackRedistributionInAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        Branch<?> l12 = network.getBranch("l12");

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()), Collections.singletonList(l12));

        factors.addAll(createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                          network.getBranchStream().filter(b -> !b.equals(l12)).collect(Collectors.toList())));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(14, result.getValues("l34").size());
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("l34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("l34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnTwoComponentAtATime() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34+l48", new BranchContingency("l34"), new BranchContingency("l48")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l34+l48");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(36, result.getValues("l34+l48").size());

        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l48", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l810", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g2", "l910", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l48", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l810", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g6", "l910", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        // FIXME: Next line is not working with EvenShiloach, it feels like the connectivity check is wrong (in the predefinedResults definition)
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l48", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l810", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l48", "g10", "l910", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingTheSameConnectivityTwice() {
        Network network = ConnectedComponentNetworkFactory.createTwoConnectedComponentsLinkedByASerieOfTwoBranches();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34+l45", new BranchContingency("l34"), new BranchContingency("l45")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l34+l45");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(16, result.getValues("l34+l45").size());

        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g2", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l45", "g6", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingConnectivityOnTwoBranches() {
        Network network = ConnectedComponentNetworkFactory.createThreeCc();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34+l47", new BranchContingency("l34"), new BranchContingency("l47")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l34+l47");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(33, result.getValues("l34+l47").size());

        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g2", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g6", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l47", "g9", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingAllPossibleCompensations() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorOnOneSide();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "d1", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "d1", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "d1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d5", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER)));
        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d5", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER)));
        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d5", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER)));

        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER)));
        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER)));
        assertTrue(Double.isNaN(result.getBranchFlow1SensitivityValue("l34", "d6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER)));
    }

    @Test
    void testPhaseShifterUnrelatedContingency() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChanger();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l23")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l14", new BranchContingency("l14")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(15d / 4d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(-10d / 4d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 4d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(15d / 4d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l23", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 4d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l23", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);

        assertEquals(5, result.getValues("l14").size());

        assertEquals(10d / 3d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l14", "l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(-10d / 3d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l14", "l23", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l14", "l23", "l14", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(10d / 3d * Math.PI / 180d, result.getBranchFlow1SensitivityValue("l14", "l23", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l14", "l23", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testPhaseShifterConnectivityLoss() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l56", "l34")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("l34").size());

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "l56", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "l56", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "l56", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "l56", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "l56", "l45", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "l56", "l46", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34", "l56", "l56", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnTransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerPSTAngle(branch.getId(), "l56", "l56")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("l56").size());

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l13", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l34", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l45", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l56", "l56", "l46", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l56", "l56", "l56", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLcc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(1.333, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcVsc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(6, result.getValues("hvdc34").size());
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(1.333, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcVscDistributedOnLoad() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(6, result.getValues("hvdc34").size());
        assertEquals(2d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccWithoutLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("hvdc34").size());
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 12d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 12d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d / 3d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l25")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("hvdc34").size());
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(1.333, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.666, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndTransformerWithoutLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l23")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(6, result.getValues("hvdc34").size());
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndTransformerAndLosingConnectivity() {
        Network network = HvdcNetworkFactory.createLinkedNetworkWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").map(network::getBranch).collect(Collectors.toList()),
                                                             "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l23"), new BranchContingency("l25")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(7, contingencyResult.size());
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("hvdc34", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testTrivialContingencyOnGenerator() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1", true);
        sensiParameters.setLoadFlowParameters(new LoadFlowParameters()
                .setDc(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX));

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g2").map(network::getGenerator).collect(Collectors.toList()),
                    Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("g6", new GeneratorContingency("g6")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(3, result.getValues("g6").size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("g6", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue("g6", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue("g6", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(-1.3333d, result.getBranchFlow1FunctionReferenceValue("g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.3333d, result.getBranchFlow1FunctionReferenceValue("g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.6666d, result.getBranchFlow1FunctionReferenceValue("g6", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnLoad() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1", true);
        sensiParameters.setLoadFlowParameters(new LoadFlowParameters()
                .setDc(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD));

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g2").map(network::getGenerator).collect(Collectors.toList()),
                Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("d5", new LoadContingency("d5")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(3, result.getValues("d5").size());
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("d5", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("d5", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("d5", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(-14d / 10d, result.getBranchFlow1FunctionReferenceValue("d5", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-8d / 30d, result.getBranchFlow1FunctionReferenceValue("d5", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(34d / 30d, result.getBranchFlow1FunctionReferenceValue("d5", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyMultipleLinesBreaksOneContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l24+l35", new BranchContingency("l24"), new BranchContingency("l35")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l24+l35");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(16, result.getValues("l24+l35").size());
        assertEquals(-0.5d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l24", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l35", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l24", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l35", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l24+l35", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testCircularLossOfConnectivity() {
        Network network = ConnectedComponentNetworkFactory.createThreeCircularCc();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l34+l27+l58", new BranchContingency("l34"), new BranchContingency("l27"), new BranchContingency("l58")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()),
                                                             "l34+l27+l58");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(36, result.getValues("l34+l27+l58").size());
        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER));
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g2", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER));

        // Components that are not linked to slack should be NaN
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER));
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l34+l27+l58", "g9", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER));
    }

    @Test
    void testAsymetricLossOnMultipleComponents() {
        Network network = ConnectedComponentNetworkFactory.createAsymetricNetwork();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l27+l18+l39+l14", new BranchContingency("l27"),
                                                                                     new BranchContingency("l18"),
                                                                                     new BranchContingency("l39"),
                                                                                     new BranchContingency("l14")));

        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                                                             network.getBranchStream().collect(Collectors.toList()), "l27+l18+l39+l14");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(39, result.getValues("l27+l18+l39+l14").size());
        List<SensitivityValue> contingencyValues = result.getValues("l27+l18+l39+l14");
        assertEquals(-2d / 3d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l18", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l27", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l39", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g2", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l18", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l27", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l39", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g6", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l18", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l27", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l39", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l79", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l27+l18+l39+l14", "g9", "l89", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testRemainingGlskFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g2", 25f),
                                                              new WeightedSensitivityVariable("g6", 40f),
                                                              new WeightedSensitivityVariable("d3", 35f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", "l34")).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(7, result.getValues("l34").size());
        assertEquals(-17d / 36d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 18d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-19d / 36d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskFactorBug() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g2", 25f),
                                                              new WeightedSensitivityVariable("g6", 40f),
                                                              new WeightedSensitivityVariable("g10", 35f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l45", new BranchContingency("l45")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        List<Contingency> contingencies2 = List.of(new Contingency("l34", new BranchContingency("l34")),
                                                   new Contingency("l45", new BranchContingency("l45")));

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, contingencies2, variableSets, sensiParameters);

        // result for l45 contingency should exactly be the same as l34 contingency for simulation 2 should not impact
        // l45 contingency.
        // an issue has been identified that is responsible in case of 2 consecutive GLSK sensitivity loosing connectivity
        // of bad reset of state
        for (Branch<?> branch : network.getBranches()) {
            assertEquals(result.getBranchFlow1SensitivityValue("l45", "glsk", branch.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                         result2.getBranchFlow1SensitivityValue("l45", "glsk", branch.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                         0d);
        }
    }

    @Test
    void testRemainingGlskFactorsAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("g2", 25f),
                                                              new WeightedSensitivityVariable("g6", 40f),
                                                              new WeightedSensitivityVariable("d3", 35f));
        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = contingencies.stream()
                .flatMap(c -> network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", c.getId())))
                .collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies,
            variableSets, sensiParameters);

        assertEquals(7, result.getValues("l34").size());
        assertEquals(-17d / 36d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 18d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-19d / 36d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l34", "glsk", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiRescale() {
        SensitivityAnalysisParameters sensiParameters = createParameters(true, List.of("b1_vl_0", "b4_vl_0"), true);

        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getLine("l25").getTerminal1().disconnect();
        network1.getLine("l25").getTerminal2().disconnect();
        runLf(network1, sensiParameters.getLoadFlowParameters());

        Network network2 = HvdcNetworkFactory.createNetworkWithGenerators();
        network2.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + SENSI_CHANGE);
        network2.getLine("l25").getTerminal1().disconnect();
        network2.getLine("l25").getTerminal2().disconnect();
        runLf(network2, sensiParameters.getLoadFlowParameters());

        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        Map<String, Double> loadFlowDiff = network.getLineStream()
                .map(Identifiable::getId)
                .collect(Collectors.toMap(Function.identity(), line -> (network2.getLine(line).getTerminal1().getP() - network1.getLine(line).getTerminal1().getP()) / SENSI_CHANGE));

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        List<Contingency> contingencies = Collections.singletonList(new Contingency("l25", new BranchContingency("l25")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(loadFlowDiff.get("l12"), result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l45", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l46", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l25", "hvdc34", "l56", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testNullValue() {
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                         SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                                                                         false, ContingencyContext.all());

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l12", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l13", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l23", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l45", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l46", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l56", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReconnectingMultipleLinesToRestoreConnectivity() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "d5", "l23+l24+l36+l35+l46")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l23+l24+l36+l35+l46",
                                                                  new BranchContingency("l23"),
                                                                  new BranchContingency("l24"),
                                                                  new BranchContingency("l36"),
                                                                  new BranchContingency("l35"),
                                                                  new BranchContingency("l46")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(11, result.getValues("l23+l24+l36+l35+l46").size());
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l24", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l35", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l36", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, result.getBranchFlow1SensitivityValue("l23+l24+l36+l35+l46", "d5", "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testBreakingConnectivityOutsideMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        // Create contingencies containing a branch contingency on l12 which breaks the connectivity outside the main
        // component and which leaves a connected component with a single bus
        // Connected components after these contingencies are: {b1}, {b2, b3, b4}, {b5, b6, b7, b8}
        // The two contingencies should be grouped together for computing the sensitivities (shared ConnectivityAnalysisResult)
        List<List<String>> contingencyBranchesIds = List.of(
                List.of("l12", "l36", "l35", "l46"),
                List.of("l12", "l36", "l35", "l34", "l46"));
        List<Contingency> contingencies = contingencyBranchesIds.stream()
                .map(branchesIds -> new Contingency(String.join("+", branchesIds),
                        branchesIds.stream().map(BranchContingency::new).collect(Collectors.toList())))
                .collect(Collectors.toList());
        String contingency1Id = contingencies.get(0).getId();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);

        String variableId = "d6";
        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), variableId)).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(11, result.getValues(contingency1Id).size());
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l24", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l35", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l36", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1 / 3., result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1 / 3., result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l57", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(2 / 3., result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l67", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, "l78", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyAllLines() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        List<String> branchesId = network.getLineStream().map(Line::getId).collect(Collectors.toList());
        List<Contingency> contingencies = List.of(new Contingency(
                String.join("+", branchesId),
                branchesId.stream().map(BranchContingency::new).collect(Collectors.toList())));
        String contingency1Id = contingencies.get(0).getId();

        SensitivityAnalysisParameters sensiParameters = createParameters(true);

        String variableId = "d4";
        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), variableId)).collect(Collectors.toList());

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(11, result.getValues(contingency1Id).size());
        for (Line line : network.getLines()) {
            assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue(contingency1Id, variableId, line.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefWithMultipleReconnections() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "d5")).collect(Collectors.toList());

        List<String> contingencyBranchesId = List.of("l23", "l24", "l36", "l35", "l46");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        Network networkDisconnected = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        contingencyBranchesId.forEach(id -> {
            networkDisconnected.getLine(id).getTerminal1().disconnect();
            networkDisconnected.getLine(id).getTerminal2().disconnect();
        });

        runLf(networkDisconnected, sensiParameters.getLoadFlowParameters());
        List<Line> nonDisconnectedLines = networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList());
        assertNotEquals(0, nonDisconnectedLines.size());
        for (Line line : nonDisconnectedLines) {
            assertEquals(line.getTerminal1().getP(), result.getBranchFlow1FunctionReferenceValue(contingencyId, line.getId()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefWithOneReconnection() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedSingleComponent();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2")).collect(Collectors.toList());

        List<String> contingencyBranchesId = List.of("l24", "l35");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        Network networkDisconnected = ConnectedComponentNetworkFactory.createHighlyConnectedSingleComponent();
        contingencyBranchesId.forEach(id -> {
            networkDisconnected.getLine(id).getTerminal1().disconnect();
            networkDisconnected.getLine(id).getTerminal2().disconnect();
        });

        runLf(networkDisconnected, sensiParameters.getLoadFlowParameters());
        List<Line> nonDisconnectedLines = networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList());
        assertNotEquals(0, nonDisconnectedLines.size());
        for (Line line : nonDisconnectedLines) {
            assertEquals(line.getTerminal1().getP(), result.getBranchFlow1FunctionReferenceValue(contingencyId, line.getId()), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefWithSequenceOfConnectivty() {
        // Test that the result if you compute sensitivity manually one by one,
        // or all at once does not change result on function reference
        // (especially if you lose compensation)
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        String branchId = "l36";
        String injectionId = "g3";
        Branch<?> branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12"));
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"), new BranchContingency("l56"), new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(result78.getBranchFlow1FunctionReferenceValue("l78", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l78", branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(result12.getBranchFlow1FunctionReferenceValue("l12", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l12", branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(result35and56and57.getBranchFlow1FunctionReferenceValue("l35+l56+l57", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l35+l56+l57", branchId), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionRefWithAdditionalFactors() {
        // Test that the result if you compute sensitivity manually one by one,
        // or all at once does not change result on function reference
        // (especially if you lose compensation)
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        String branchId = "l36";
        String injectionId = "g3";
        Branch<?> branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12"));
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"), new BranchContingency("l56"), new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(result78.getBranchFlow1FunctionReferenceValue("l78", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l78", branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(result12.getBranchFlow1FunctionReferenceValue("l12", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l12", branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(result35and56and57.getBranchFlow1FunctionReferenceValue("l35+l56+l57", branchId), globalResult.getBranchFlow1FunctionReferenceValue("l35+l56+l57", branchId), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testChangingCompensationThenNot() {
        // Multiple contingencies: one that lose connectivity and change the slack distribution, and others that lose connectivity without changing distribution
        // This allows us to check that changing the compensation has no side effect on the next contingencies
        // The contingency changing distribution must be done before at least one of the other.

        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        String branchId = "l36";
        String injectionId = "g3";
        Branch<?> branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12")); // change distribution
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"), new BranchContingency("l56"), new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(result78.getBranchFlow1SensitivityValue("l78", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), globalResult.getBranchFlow1SensitivityValue("l78", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result12.getBranchFlow1SensitivityValue("l12", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), globalResult.getBranchFlow1SensitivityValue("l12", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result35and56and57.getBranchFlow1SensitivityValue("l35+l56+l57", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), globalResult.getBranchFlow1SensitivityValue("l35+l56+l57", injectionId, branchId, SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l56")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("l56").size());

        assertEquals(-4d / 3d, result.getBranchFlow1FunctionReferenceValue("l56", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("l56", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, result.getBranchFlow1FunctionReferenceValue("l56", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, result.getBranchFlow1FunctionReferenceValue("l56", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, result.getBranchFlow1FunctionReferenceValue("l56", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l56", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d, result.getBranchFlow1FunctionReferenceValue("l56", "l46"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerAndAContingency() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l48+l67")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l48+l67", new BranchContingency("l48"), new BranchContingency("l67")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(-4d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerAndAContingencyInAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = Stream.concat(Stream.of(createBranchFlowPerInjectionIncrease("l12", "g2", "l48+l67")),
                                                        network.getBranchStream().filter(branch -> !branch.getId().equals("l12")).map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l48+l67")))
                                                .collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l48+l67", new BranchContingency("l48"), new BranchContingency("l67")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(-4d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l48+l67", "l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingConnectivityOnATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByATransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l34")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(7, result.getValues("l34").size());

        assertEquals(-5d / 3d, result.getBranchFlow1FunctionReferenceValue("l34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, result.getBranchFlow1FunctionReferenceValue("l34", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 3d, result.getBranchFlow1FunctionReferenceValue("l34", "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFoundAdditionalFactorContingency() {
        testInjectionNotFoundAdditionalFactorContingency(true);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerThenLosingAConnectivityBreakingContingency() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l56", "g2"));

        Contingency transformerContingency = new Contingency("l67", new BranchContingency("l67")); // losing a transformer
        Contingency connectivityLosingContingency = new Contingency("l48", new BranchContingency("l48")); // losing connectivty

        SensitivityAnalysisResult resultBoth = sensiRunner.run(network, factors, List.of(transformerContingency, connectivityLosingContingency), Collections.emptyList(), sensiParameters);

        SensitivityAnalysisResult resultLosingConnectivityAlone = sensiRunner.run(network, factors, List.of(connectivityLosingContingency), Collections.emptyList(), sensiParameters);

        SensitivityAnalysisResult resultLosingTransformerAlone = sensiRunner.run(network, factors, List.of(transformerContingency), Collections.emptyList(), sensiParameters);

        assertEquals(resultLosingConnectivityAlone.getBranchFlow1FunctionReferenceValue("l48", "l56"), resultBoth.getBranchFlow1FunctionReferenceValue("l48", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(resultLosingTransformerAlone.getBranchFlow1FunctionReferenceValue("l67", "l56"), resultBoth.getBranchFlow1FunctionReferenceValue("l67", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnAWrongHvdc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g2").map(network::getGenerator).collect(Collectors.toList()),
                                                             Stream.of("l12", "l13", "l23").map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("wrong")));

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("HVDC line 'wrong' not found in the network", e.getCause().getMessage());
    }

    @Test
    void testContingencyWithDisconnectedBranch() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<Contingency> contingencies = List.of(new Contingency("l45", new BranchContingency("l45")));

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l46", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        // different sensitivity for (g2, l46) on base case and after contingency l45
        assertEquals(0.0667d, result.getBranchFlow1SensitivityValue("g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1429d, result.getBranchFlow1SensitivityValue("l45", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        // we open l45 at both sides
        Line l45 = network.getLine("l45");
        l45.getTerminal1().disconnect();
        l45.getTerminal2().disconnect();
        runDcLf(network);

        result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        // we now have as expected the sensitivity for (g2, l46) on base case and after contingency l45
        // because l45 is already open on base case
        assertEquals(0.1429d, result.getBranchFlow1SensitivityValue("g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1429d, result.getBranchFlow1SensitivityValue("l45", "g2", "l46", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionDisconnectedBranchSide1() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        Line l45 = network.getLine("l45");
        l45.getTerminal1().disconnect();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l45", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, result.getBranchFlow1SensitivityValue("g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionDisconnectedBranchSide2() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        Line l45 = network.getLine("l45");
        l45.getTerminal2().disconnect();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l45", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, result.getBranchFlow1SensitivityValue("g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionDisconnectedBranchBothSides() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        Line l45 = network.getLine("l45");
        l45.getTerminal1().disconnect();
        l45.getTerminal2().disconnect();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l45", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, result.getBranchFlow1SensitivityValue("g2", "l45", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDebug() throws IOException {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        network.setCaseDate(ZonedDateTime.parse("2021-04-25T13:47:34.697+02:00"));
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        String debugDir = "/work";
        OpenSensitivityAnalysisParameters sensiParametersExt = new OpenSensitivityAnalysisParameters()
                .setDebugDir(debugDir);
        sensiParameters.addExtension(OpenSensitivityAnalysisParameters.class, sensiParametersExt);

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk",
                List.of(new WeightedSensitivityVariable("g2", 25f),
                        new WeightedSensitivityVariable("g6", 40f),
                        new WeightedSensitivityVariable("d3", 35f))));

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l45", "glsk"),
                                                  createBranchFlowPerInjectionIncrease("l12", "g2"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        Path contingenciesFile = null;
        Path factorsFile = null;
        Path networkFile = null;
        Path parametersFile = null;
        Path variableSetsFile = null;
        FileSystem fileSystem = PlatformConfig.defaultConfig().getConfigDir().map(Path::getFileSystem).orElseThrow(PowsyblException::new);
        PathMatcher contingenciesMatcher = fileSystem.getPathMatcher("glob:contingencies-*.json");
        PathMatcher factorsMatcher = fileSystem.getPathMatcher("glob:factors-*.json");
        PathMatcher networkMatcher = fileSystem.getPathMatcher("glob:network-*.xiidm");
        PathMatcher parametersMatcher = fileSystem.getPathMatcher("glob:parameters-*.json");
        PathMatcher variableSetsMatcher = fileSystem.getPathMatcher("glob:variable-sets-*.json");
        for (Path path : Files.list(fileSystem.getPath(debugDir)).collect(Collectors.toList())) {
            if (contingenciesMatcher.matches(path.getFileName())) {
                contingenciesFile = path;
            }
            if (factorsMatcher.matches(path.getFileName())) {
                factorsFile = path;
            }
            if (networkMatcher.matches(path.getFileName())) {
                networkFile = path;
            }
            if (parametersMatcher.matches(path.getFileName())) {
                parametersFile = path;
            }
            if (variableSetsMatcher.matches(path.getFileName())) {
                variableSetsFile = path;
            }
        }
        assertNotNull(contingenciesFile);
        assertNotNull(factorsFile);
        assertNotNull(networkFile);
        assertNotNull(parametersFile);
        assertNotNull(variableSetsFile);
        try (InputStream is = Files.newInputStream(contingenciesFile)) {
            ComparisonUtils.assertTxtEquals(Objects.requireNonNull(getClass().getResourceAsStream("/debug-contingencies.json")), is);
        }
        try (InputStream is = Files.newInputStream(factorsFile)) {
            ComparisonUtils.assertTxtEquals(Objects.requireNonNull(getClass().getResourceAsStream("/debug-factors.json")), is);
        }
        try (InputStream is = Files.newInputStream(networkFile)) {
            ComparisonUtils.assertXmlEquals(Objects.requireNonNull(getClass().getResourceAsStream("/debug-network.xiidm")), is);
        }
        try (InputStream is = Files.newInputStream(parametersFile)) {
            ComparisonUtils.assertTxtEquals(Objects.requireNonNull(getClass().getResourceAsStream("/debug-parameters.json")), is);
        }
        try (InputStream is = Files.newInputStream(variableSetsFile)) {
            ComparisonUtils.assertTxtEquals(Objects.requireNonNull(getClass().getResourceAsStream("/debug-variable-sets.json")), is);
        }

        String fileName = contingenciesFile.getFileName().toString();
        String dateStr = fileName.substring(14, fileName.length() - 5);
        ZonedDateTime date = LocalDateTime.parse(dateStr, DebugUtil.DATE_TIME_FORMAT).atZone(ZoneId.of("UTC"));

        List<SensitivityValue> values2 = sensiProvider.replay(date, fileSystem.getPath(debugDir)).resultWriter().getValues();

        // assert we have exactly the same result with replay
        assertEquals(result.getValues().size(), values2.size());
        Iterator<SensitivityValue> itExpected = values2.iterator();

        for (SensitivityValue actual : result.getValues()) {
            SensitivityValue expected = itExpected.next();
            assertEquals(actual.getContingencyIndex(), expected.getContingencyIndex());
            assertEquals(actual.getValue(), expected.getValue(), LoadFlowAssert.DELTA_POWER);
            assertEquals(actual.getFunctionReference(), expected.getFunctionReference(), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnLoads() {
        Network network = BoundaryFactory.createWithLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "g1"));

        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(0.1875, result.getBranchFlow1SensitivityValue("g1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(75.881, result.getBranchFlow1FunctionReferenceValue("l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1875, result.getBranchFlow1SensitivityValue("dl1", "g1", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(19.006, result.getBranchFlow1FunctionReferenceValue("dl1", "l1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        runLf(network, sensiParameters.getLoadFlowParameters(), ReportNode.NO_OP);
        double initialP = l1.getTerminal1().getP();
        assertEquals(19.006, initialP, LoadFlowAssert.DELTA_POWER);
        network.getGenerator("g1").setTargetP(network.getGenerator("g1").getTargetP() + 1);
        runLf(network, sensiParameters.getLoadFlowParameters(), ReportNode.NO_OP);
        double finalP = l1.getTerminal1().getP();
        assertEquals(19.194, finalP, LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1875, finalP - initialP, LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnGenerators() {
        Network network = BoundaryFactory.createWithLoad();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "load3"));

        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));

        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result2.getPreContingencyValues().size());
        assertEquals(-0.1874, result2.getBranchFlow1SensitivityValue("load3", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(75.813, result2.getBranchFlow1FunctionReferenceValue("l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1875, result2.getBranchFlow1SensitivityValue("dl1", "load3", "l1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.875, result2.getBranchFlow1FunctionReferenceValue("dl1", "l1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        runLf(network, sensiParameters.getLoadFlowParameters(), ReportNode.NO_OP);
        double initialP = l1.getTerminal1().getP();
        assertEquals(1.875, initialP, LoadFlowAssert.DELTA_POWER);
        network.getLoad("load3").setP0(network.getLoad("load3").getP0() + 1);
        runLf(network, sensiParameters.getLoadFlowParameters(), ReportNode.NO_OP);
        double finalP = l1.getTerminal1().getP();
        assertEquals(2.0624, finalP, LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1875, finalP - initialP, LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void contingencyOnPhaseTapChangerTest() {
        Network network = PhaseShifterTestCaseFactory.create();

        SensitivityAnalysisParameters parameters = createParameters(true, "VL1_0", true);
        parameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("PS1");
        List<SensitivityFactor> factors = List.of(createBranchFlowPerPSTAngle("L1", ps1.getId()));

        List<Contingency> contingencies = List.of(new Contingency("PS1", new BranchContingency("PS1")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), parameters);

        assertEquals(100.0, result.getBranchFlow1FunctionReferenceValue("PS1", "L1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, result.getBranchFlow1SensitivityValue("PS1", "PS1", "L1", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingency() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l12", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(100.000, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.000, result.getBranchFlow1FunctionReferenceValue("additionnalline_0", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyOnWatchedLine() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l12", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        List<Contingency> contingencies = List.of(new Contingency("l12", new BranchContingency("l12")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(100.0, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l12", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyOnWatchedLine2() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("additionnalline_0", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "additionnalline_0", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0, result.getBranchFlow1FunctionReferenceValue("additionnalline_0"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("additionnalline_0", "additionnalline_0"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyBreakingConnectivity() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine2();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("additionnalline_10", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        assertEquals(2, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "additionnalline_10", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(0, result.getBranchFlow1FunctionReferenceValue("additionnalline_10"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("additionnalline_0", "additionnalline_10"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLoadContingencyNotInMainComponent() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.getTwoWindingsTransformer("T2wT").getTerminal1().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL_1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("LINE_12", "GEN_1"));

        List<Contingency> contingencies = List.of(new Contingency("LOAD_3", new LoadContingency("LOAD_3")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(1.0, result.getBranchFlow1SensitivityValue("GEN_1", "LINE_12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(25.0, result.getBranchFlow1FunctionReferenceValue("LINE_12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, result.getBranchFlow1SensitivityValue("LOAD_3", "GEN_1", "LINE_12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(25.0, result.getBranchFlow1FunctionReferenceValue("LOAD_3", "LINE_12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGeneratorContingencyNotInMainComponent() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.getVoltageLevel("VL_3").newGenerator()
                .setId("GEN_3")
                .setBus("BUS_3")
                .setMinP(0.0)
                .setMaxP(10)
                .setTargetP(5)
                .setTargetV(30)
                .setVoltageRegulatorOn(true)
                .add();
        network.getTwoWindingsTransformer("T2wT").getTerminal1().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL_1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("LINE_12", "GEN_1"));

        List<Contingency> contingencies = List.of(new Contingency("GEN_3", new GeneratorContingency("GEN_3")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(1.0, result.getBranchFlow1SensitivityValue("GEN_1", "LINE_12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(25.0, result.getBranchFlow1FunctionReferenceValue("LINE_12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.0, result.getBranchFlow1SensitivityValue("GEN_3", "GEN_1", "LINE_12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(25.0, result.getBranchFlow1FunctionReferenceValue("GEN_3", "LINE_12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGeneratorContingencyNotInMainComponentAndMonitoredBranchNotInMainComponent() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.getVoltageLevel("VL_3").newGenerator()
                .setId("GEN_3")
                .setBus("BUS_3")
                .setMinP(0.0)
                .setMaxP(10)
                .setTargetP(5)
                .setTargetV(30)
                .setVoltageRegulatorOn(true)
                .add();
        network.getTwoWindingsTransformer("T2wT").getTerminal1().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL_1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("T2wT", "GEN_1"));

        List<Contingency> contingencies = List.of(new Contingency("GEN_3", new GeneratorContingency("GEN_3")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(0.0, result.getBranchFlow1SensitivityValue("GEN_1", "T2wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("T2wT"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testVariableNotInMainComponentAndMonitoredBranchNotInMainComponent() {
        Network network = VoltageControlNetworkFactory.createNetworkWithT2wt();
        network.getVoltageLevel("VL_3").newGenerator()
                .setId("GEN_3")
                .setBus("BUS_3")
                .setMinP(0.0)
                .setMaxP(10)
                .setTargetP(5)
                .setTargetV(30)
                .setVoltageRegulatorOn(true)
                .add();
        network.getTwoWindingsTransformer("T2wT").getTerminal1().disconnect();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL_1_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("T2wT", "LOAD_3"));

        List<Contingency> contingencies = List.of(new Contingency("GEN_3", new GeneratorContingency("GEN_3")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("LOAD_3", "T2wT", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("T2wT"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGLSK() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("g1", new GeneratorContingency("g1")), new Contingency("g4", new GeneratorContingency("g4")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        network.getGenerator("g1").getTerminal().disconnect();
        List<WeightedSensitivityVariable> variables2 = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f));
        List<SensitivityVariableSet> variableSets2 = Collections.singletonList(new SensitivityVariableSet("glsk", variables2));
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), variableSets2, sensiParameters);

        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g1", "glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l14"), result.getBranchFlow1FunctionReferenceValue("g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l12"), result.getBranchFlow1FunctionReferenceValue("g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l23"), result.getBranchFlow1FunctionReferenceValue("g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l34"), result.getBranchFlow1FunctionReferenceValue("g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l34"), result.getBranchFlow1FunctionReferenceValue("g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l13"), result.getBranchFlow1FunctionReferenceValue("g1", "l13"), LoadFlowAssert.DELTA_POWER);

        network.getGenerator("g1").getTerminal().connect();
        network.getGenerator("g4").getTerminal().disconnect();
        SensitivityAnalysisResult result3 = sensiRunner.run(network, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(result3.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("g4", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGLSK2() {
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("d3", new LoadContingency("d3")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        network.getLoad("d3").getTerminal().disconnect();
        List<WeightedSensitivityVariable> variables2 = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets2 = Collections.singletonList(new SensitivityVariableSet("glsk", variables2));
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), variableSets2, sensiParameters);

        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d3", "glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l14"), result.getBranchFlow1FunctionReferenceValue("d3", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l12"), result.getBranchFlow1FunctionReferenceValue("d3", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l23"), result.getBranchFlow1FunctionReferenceValue("d3", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l34"), result.getBranchFlow1FunctionReferenceValue("d3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l34"), result.getBranchFlow1FunctionReferenceValue("d3", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l13"), result.getBranchFlow1FunctionReferenceValue("d3", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGLSK3() {
        // FIXME
        // In case of a contingency that involves a generator or a load. The GLSK should contains
        // this contingency element and an other one connected to the same bus.
        Network network = FourBusNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<WeightedSensitivityVariable> variables = List.of(new WeightedSensitivityVariable("d2", 30f),
                new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("d2", new LoadContingency("d2")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);

        network.getLoad("d2").getTerminal().disconnect();
        List<WeightedSensitivityVariable> variables2 = List.of(new WeightedSensitivityVariable("g2", 10f),
                new WeightedSensitivityVariable("d3", 50f),
                new WeightedSensitivityVariable("g1", 10f));
        List<SensitivityVariableSet> variableSets2 = Collections.singletonList(new SensitivityVariableSet("glsk", variables2));
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), variableSets2, sensiParameters);

        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l23", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l34", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertNotEquals(result2.getBranchFlow1SensitivityValue("glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("d2", "glsk", "l13", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingALineButBothEndsInMainComponent() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorOnOneSide();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", false);

        List<Contingency> contingencies = List.of(new Contingency("l34+l12", new BranchContingency("l34"), new BranchContingency("l12")));

        List<SensitivityFactor> factors = createFactorMatrix(List.of(network.getGenerator("g3")),
                List.of(network.getLine("l12")),
                "l34+l12");

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues("l34+l12").size());

        assertEquals(0.0, result.getBranchFlow1SensitivityValue("l34+l12", "g3", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l34+l12", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getContingencyStatus("l34+l12"));
    }

    @Test
    void testContingencyOnHvdcInAcEmulation() {
        Network network = HvdcNetworkFactory.createWithHvdcInAcEmulation();
        network.getHvdcLine("hvdc34").newExtension(HvdcAngleDroopActivePowerControlAdder.class)
                .withDroop(180)
                .withP0(0.f)
                .withEnabled(true)
                .add();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);

        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("g1", "g5").map(network::getGenerator).collect(Collectors.toList()),
                Stream.of("l12", "l25", "l56").map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getContingencyStatus("hvdc34"));

        network.getHvdcLine("hvdc34").getConverterStation1().getTerminal().disconnect();
        network.getHvdcLine("hvdc34").getConverterStation2().getTerminal().disconnect();
        SensitivityAnalysisResult result2 = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(6, result.getValues("hvdc34").size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(result2.getBranchFlow1SensitivityValue("g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g1", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("g5", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g5", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("g5", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g5", "l25", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1SensitivityValue("g5", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), result.getBranchFlow1SensitivityValue("hvdc34", "g5", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);

        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l12"), result.getBranchFlow1FunctionReferenceValue("hvdc34", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l25"), result.getBranchFlow1FunctionReferenceValue("hvdc34", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(result2.getBranchFlow1FunctionReferenceValue(null, "l56"), result.getBranchFlow1FunctionReferenceValue("hvdc34", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testIeeeCdf14() {
        Network network = IeeeCdfNetworkFactory.create14();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(5000));
        SensitivityAnalysisParameters sensiParameters = createParameters(true);
        List<SensitivityFactor> factors = createFactorMatrix(Stream.of("B1-G", "B2-G", "B3-G").map(network::getGenerator).collect(Collectors.toList()),
                Stream.of("L1-5-1", "L2-3-1").map(network::getBranch).collect(Collectors.toList()));
        List<Contingency> contingencies = List.of(new Contingency("L1-2-1", new BranchContingency("L1-2-1")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(72.246, result.getBranchFlow1FunctionReferenceValue(null, "L1-5-1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(69.831, result.getBranchFlow1FunctionReferenceValue(null, "L2-3-1"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testSwitchContingency() {
        Network network = NodeBreakerNetworkFactory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("L1", "LD"));

        List<Contingency> contingencies = List.of(new Contingency("C", new SwitchContingency("C")));

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters);
        assertEquals(-0.500, result.getBranchFlow1SensitivityValue("LD", "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1.000, result.getBranchFlow1SensitivityValue("C", "LD", "L1", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(300.0, result.getBranchFlow1FunctionReferenceValue("L1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-3.770, result.getBranchFlow1FunctionReferenceValue("C", "L1"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionDisconnectedBranchBothSidesWithContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        Line l45 = network.getLine("l45");
        l45.getTerminal1().disconnect();
        l45.getTerminal2().disconnect();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l45", "g2"));
        List<Contingency> contingencies = List.of(new Contingency("C", List.of(new BranchContingency("l24"), new BranchContingency("l35"))));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getContingencyStatus("C"));
    }

    @Test
    void testPredefinedResults() {
        // Load and generator in contingency
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l14", "g1"),
                createBranchFlowPerInjectionIncrease("l14", "d2"));
        List<Contingency> contingencies = List.of(new Contingency("g1", new GeneratorContingency("g1")), new Contingency("d2", new LoadContingency("d2")));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(0, result.getBranchFlow1SensitivityValue("g1", "g1", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue("d2", "d2", "l14", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testPredefinedResults2() {
        // LCC line in contingency
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().getExtension(OpenLoadFlowParameters.class).setSlackBusPMaxMismatch(0.001);
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("l25"),
                SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"),
                false, ContingencyContext.all());
        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(0.9889, result.getBranchFlow1SensitivityValue("hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        // VSC line in contingency
        Network network2 = HvdcNetworkFactory.createNetworkWithGenerators2();
        SensitivityAnalysisResult result2 = sensiRunner.run(network2, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(0, result2.getBranchFlow1SensitivityValue("hvdc34", "hvdc34", "l25", SensitivityVariableType.HVDC_LINE_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testPredefinedResults3() {
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        Network network = FourBusNetworkFactory.createWithPhaseTapChangerAndGeneratorAtBus2();
        List<SensitivityFactor> factors = List.of(createBranchFlowPerPSTAngle("l12", "l23", "l23"),
                createBranchFlowPerPSTAngle("l23", "l23", "l23"));
        List<Contingency> contingencies = Collections.singletonList(new Contingency("l23", new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(0, result.getBranchFlow1SensitivityValue("l23", "l23", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("l23", "l23", "l23", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDcUseTransformerRatioIssue() {
        testDcUseTransformerRatioIssue(false);
        testDcUseTransformerRatioIssue(true);
    }

    private void testDcUseTransformerRatioIssue(boolean dcUseTransformerRatio) {
        Network network = IeeeCdfNetworkFactory.create14();
        var ps56 = network.getTwoWindingsTransformer("T5-6-1");
        ps56.newPhaseTapChanger()
                .setTapPosition(0)
                .beginStep()
                    .setAlpha(-5)
                .endStep()
                .beginStep()
                    .setAlpha(0)
                .endStep()
                .beginStep()
                    .setAlpha(5)
                .endStep()
                .add();
        var ps49 = network.getTwoWindingsTransformer("T4-9-1");
        ps49.newPhaseTapChanger()
                .setTapPosition(0)
                .beginStep()
                .setAlpha(-6)
                .endStep()
                .beginStep()
                .setAlpha(-3)
                .endStep()
                .beginStep()
                .setAlpha(1)
                .endStep()
                .add();
        Line l1011 = network.getLine("L10-11-1");
        Generator g1 = network.getGenerator("B1-G");
        List<SensitivityFactor> factors = List.of(new SensitivityFactor(
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                l1011.getId(),
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                g1.getId(),
                false,
                ContingencyContext.all()));
        Contingency cont49And56 = new Contingency(ps49.getId() + " " + ps56.getId(), new BranchContingency(ps49.getId()), new BranchContingency(ps56.getId()));
        List<Contingency> contingencies = List.of(
                Contingency.twoWindingsTransformer(ps49.getId()),
                Contingency.twoWindingsTransformer(ps56.getId()),
                cont49And56);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VL2_0");
        sensiParameters.getLoadFlowParameters()
                .setReadSlackBus(false)
                .setDcUseTransformerRatio(dcUseTransformerRatio)
                .setDistributedSlack(false);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        double l1011p1 = result.getBranchFlow1FunctionReferenceValue(l1011.getId());
        double l1011p1ContPs49 = result.getBranchFlow1FunctionReferenceValue(ps49.getId(), l1011.getId());
        double l1011p1ContPs56 = result.getBranchFlow1FunctionReferenceValue(ps56.getId(), l1011.getId());
        double l1011p1ContPs49And56 = result.getBranchFlow1FunctionReferenceValue(cont49And56.getId(), l1011.getId());

        loadFlowRunner.run(network, sensiParameters.getLoadFlowParameters());
        assertActivePowerEquals(l1011p1, l1011.getTerminal1());

        double epsSensiCompLf = 1E-13;
        assertEquals(calculateSensiFromLf(network, g1, l1011, sensiParameters.getLoadFlowParameters()),
                     result.getBranchFlow1SensitivityValue(g1.getId(), l1011.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                     epsSensiCompLf);

        ps49.getTerminal1().disconnect();
        ps49.getTerminal2().disconnect();
        loadFlowRunner.run(network, sensiParameters.getLoadFlowParameters());
        assertActivePowerEquals(l1011p1ContPs49, l1011.getTerminal1());

        assertEquals(calculateSensiFromLf(network, g1, l1011, sensiParameters.getLoadFlowParameters()),
                     result.getBranchFlow1SensitivityValue(ps49.getId(), g1.getId(), l1011.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                     epsSensiCompLf);

        ps49.getTerminal1().connect();
        ps49.getTerminal2().connect();
        ps56.getTerminal1().disconnect();
        ps56.getTerminal2().disconnect();
        loadFlowRunner.run(network, sensiParameters.getLoadFlowParameters());
        assertActivePowerEquals(l1011p1ContPs56, l1011.getTerminal1());

        assertEquals(calculateSensiFromLf(network, g1, l1011, sensiParameters.getLoadFlowParameters()),
                     result.getBranchFlow1SensitivityValue(ps56.getId(), g1.getId(), l1011.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                     epsSensiCompLf);

        ps49.getTerminal1().disconnect();
        ps49.getTerminal2().disconnect();
        loadFlowRunner.run(network, sensiParameters.getLoadFlowParameters());
        assertActivePowerEquals(l1011p1ContPs49And56, l1011.getTerminal1());

        assertEquals(calculateSensiFromLf(network, g1, l1011, sensiParameters.getLoadFlowParameters()),
                     result.getBranchFlow1SensitivityValue(cont49And56.getId(), g1.getId(), l1011.getId(), SensitivityVariableType.INJECTION_ACTIVE_POWER),
                     epsSensiCompLf);
    }

    private double calculateSensiFromLf(Network network, Generator g, Line l, LoadFlowParameters parameters) {
        double p1Before = l.getTerminal1().getP();
        g.setTargetP(g.getTargetP() + 1);
        loadFlowRunner.run(network, parameters);
        g.setTargetP(g.getTargetP() - 1);
        return l.getTerminal1().getP() - p1Before;
    }

    /**
     *                      NHV1_NHV1
     *              --------------------------
     *             /                          \
     * NGEN --- NHV1                           NHV2 --- NLOAD
     *            \                            /
     *             ----------- NHV3 -----------
     *             NHV1_NHV2_1      NHV1_NHV2_2
     */
    @Test
    void testOneOfTwoSerialLinesContingency() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        network.getLine("NHV1_NHV2_1").remove();
        Substation p3 = network.newSubstation()
                .setId("P3")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vlhv3 = p3.newVoltageLevel()
                .setId("VLHV3")
                .setNominalV(380)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vlhv3.getBusBreakerView().newBus()
                .setId("NHV3")
                .add();
        network.newLine()
                .setId("NHV1_NHV2_1_1")
                .setVoltageLevel1("VLHV1")
                .setBus1("NHV1")
                .setVoltageLevel2("VLHV3")
                .setBus2("NHV3")
                .setR(3.0 / 2)
                .setX(33.0 / 2)
                .setB1(386E-6 / 4)
                .setB2(386E-6 / 3)
                .add();
        network.newLine()
                .setId("NHV1_NHV2_1_2")
                .setVoltageLevel1("VLHV3")
                .setBus1("NHV3")
                .setVoltageLevel2("VLHV2")
                .setBus2("NHV2")
                .setR(3.0 / 2)
                .setX(33.0 / 2)
                .setB1(386E-6 / 4)
                .setB2(386E-6 / 3)
                .add();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "VLLOAD_0");

        List<SensitivityFactor> factors = List.of(new SensitivityFactor(
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                "NHV1_NHV2_1_2",
                SensitivityVariableType.INJECTION_ACTIVE_POWER,
                "GEN",
                false,
                ContingencyContext.all())
        );

        List<Contingency> contingencies = List.of(Contingency.line("NHV1_NHV2_1_1"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);

        assertEquals(303.5d, result.getBranchFlow1FunctionReferenceValue("NHV1_NHV2_1_2"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, result.getBranchFlow1FunctionReferenceValue("NHV1_NHV2_1_1", "NHV1_NHV2_1_2"), 0d); // strict zero and not anymore a small value
    }

    @Test
    void testBusContingency() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "NGEN", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("NHV1_NHV2_1", "LOAD"),
                createBranchFlowPerInjectionIncrease("NHV1_NHV2_2", "LOAD"),
                createBranchFlowPerInjectionIncrease("NHV2_NLOAD", "LOAD"),
                createBranchFlowPerInjectionIncrease("NGEN_NHV1", "LOAD"));
        List<Contingency> contingencies = network.getBusBreakerView().getBusStream()
                .map(bus -> new Contingency(bus.getId(), new BusContingency(bus.getId())))
                .collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(300.0, result.getBranchFlow1FunctionReferenceValue("NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(300.0, result.getBranchFlow1FunctionReferenceValue("NHV1_NHV2_2"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, result.getBranchFlow1FunctionReferenceValue("NLOAD", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        // Isolated slack bus, supported in DC.
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("NHV1", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER); // isolated slack bus, supported.
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("NHV1", "NGEN_NHV1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(SensitivityAnalysisResult.Status.SUCCESS, result.getContingencyStatus("NHV1"));
        assertEquals(0.0, result.getBranchFlow1FunctionReferenceValue("NHV2", "NGEN_NHV1"), LoadFlowAssert.DELTA_POWER);
        // FIXME
        // Contingency 'NGEN' leads to the loss of a slack bus: slack bus kept
        // we clean the contingency in order to keep the slack bus in the network, leading to a wrong computation.
        assertEquals(SensitivityAnalysisResult.Status.NO_IMPACT, result.getContingencyStatus("NGEN"));
        assertEquals(300.0, result.getBranchFlow1FunctionReferenceValue("NGEN", "NHV1_NHV2_1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(600.0, result.getBranchFlow1FunctionReferenceValue("NGEN", "NGEN_NHV1"), LoadFlowAssert.DELTA_POWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testVSCLoss(boolean withFictiveLoad) {
        Network network = HvdcNetworkFactory.createHvdcLinkedByTwoLinesAndSwitch(HvdcConverterStation.HvdcType.VSC);
        if (withFictiveLoad) {
            network.getBusBreakerView().getBus("b2").getVoltageLevel()
                    .newLoad()
                    .setId("fictiveLoad")
                    .setP0(5)
                    .setQ0(1)
                    .setBus("b2")
                    .setConnectableBus("b2")
                    .setFictitious(true)
                    .add();
        }
        List<Contingency> contingencies = List.of(new Contingency("contingency", new LineContingency("l12")),
                                                  new Contingency("bus", new BusContingency("b2")));
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l34", "l4"));
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1", true);
        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(-200.0, result.getBranchFlow1FunctionReferenceValue("l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, result.getBranchFlow1FunctionReferenceValue("contingency", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, result.getBranchFlow1FunctionReferenceValue("bus", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingenciesWithConnectivityBreak() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b3_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), "d5")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(
                new Contingency("l67+l57+l56", new BranchContingency("l67"), new BranchContingency("l57"), new BranchContingency("l56")),
                new Contingency("l67+l57", new BranchContingency("l67"), new BranchContingency("l57")));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, contingencies, Collections.emptyList(), sensiParameters);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l67+l57+l56", "l56"));
        assertEquals(-0.296, result.getBranchFlow1FunctionReferenceValue("l67+l57", "l56"), LoadFlowAssert.DELTA_POWER);
    }
}
