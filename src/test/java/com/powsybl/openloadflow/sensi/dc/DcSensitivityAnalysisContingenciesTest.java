/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.*;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.sensi.*;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;

/**
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
class DcSensitivityAnalysisContingenciesTest extends AbstractSensitivityAnalysisTest {

    @Test
    void testContingencyWithOneElementAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l23");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault(), Reporter.NO_OP)
                                                        .join();

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(5, result.getValues("l23").size());
        assertEquals(2d / 15d, getContingencyValue(result, "l23", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6d, getContingencyValue(result, "l23", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 15d, getContingencyValue(result, "l23", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 15d, getContingencyValue(result, "l23", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithOneElementAwayOnSlack() {
        //remove a branch connected to slack
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l12");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(5, result.getValues("l12").size());
        assertEquals(-1d / 15d, getContingencyValue(result, "l12", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l12", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.6d, getContingencyValue(result, "l12", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 15d, getContingencyValue(result, "l12", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l12", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyWithOneElementAwayOnSlackWithAdditionalFactors() {
        //remove a branch connected to slack
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l12");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.add(createBranchFlowPerInjectionIncrease("l13", "g2"));
        factors.add(createBranchFlowPerInjectionIncrease("l14", "g2"));
        factors.add(createBranchFlowPerInjectionIncrease("l34", "g2"));
        factors.add(createBranchFlowPerInjectionIncrease("l12", "g2"));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(4, result.getPreContingencyValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(4, result.getValues("l12").size());
        assertEquals(0d, getContingencyValue(result, "l12", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 15d, getContingencyValue(result, "l12", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l12", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.0666, getContingencyValue(result, "l12", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionRefOnOneElement() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l23");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        Network networkDisconnected = FourBusNetworkFactory.create();
        networkDisconnected.getLine("l23").getTerminal1().disconnect();
        networkDisconnected.getLine("l23").getTerminal2().disconnect();
        runDcLf(networkDisconnected);

        for (Line line : networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList())) {
            assertEquals(line.getTerminal1().getP(), getContingencyFunctionReference(result, line.getId(), "l23"), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefOnTwoElement() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<Contingency> contingencies = List.of(new Contingency("l23+l34", new BranchContingency("l23"), new BranchContingency("l34")));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        Network networkDisconnected = FourBusNetworkFactory.create();
        networkDisconnected.getLine("l23").getTerminal1().disconnect();
        networkDisconnected.getLine("l23").getTerminal2().disconnect();
        networkDisconnected.getLine("l34").getTerminal1().disconnect();
        networkDisconnected.getLine("l34").getTerminal2().disconnect();

        runDcLf(networkDisconnected);

        for (Line line : networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList())) {
            assertEquals(line.getTerminal1().getP(), getContingencyFunctionReference(result, line.getId(), "l23+l34"), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testContingencyWithTwoElementsAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<Contingency> contingencies = List.of(new Contingency("l23+l34", new BranchContingency("l23"), new BranchContingency("l34")));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(5, result.getValues("l23+l34").size());
        assertEquals(0.2, getContingencyValue(result, "l23+l34", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.6d, getContingencyValue(result, "l23+l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23+l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23+l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23+l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLine() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(14, result.getValues("l34").size());
        assertEquals(-2d / 3d, getContingencyValue(result, "l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(result, "l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-4d / 3d, getFunctionReference(result, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 3d, getContingencyFunctionReference(result, "l12", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLineWithDistributedSlack() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(14, result.getValues("l34").size());
        assertEquals(-0.5d, getContingencyValue(result, "l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getContingencyValue(result, "l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void slackRedistributionInAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        Branch branch = network.getBranch("l12");
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()), Collections.singletonList(branch));
        factors.addAll(createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().filter(b -> !b.equals(branch)).collect(Collectors.toList())));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(14, result.getValues("l34").size());
        assertEquals(-0.5d, getContingencyValue(result, "l34", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getContingencyValue(result, "l34", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnTwoComponentAtATime() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34+l48", new BranchContingency("l34"), new BranchContingency("l48")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34+l48");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(36, result.getValues("l34+l48").size());

        assertEquals(-2d / 3d, getContingencyValue(result, "l34+l48", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(result, "l34+l48", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l48", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l48"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l810"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g2", "l910"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l48"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l810"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g6", "l910"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l67"), LoadFlowAssert.DELTA_POWER);
        // FIXME: Next line is not working with EvenShiloach, it feels like the connectivity check is wrong (in the predefinedResults definition)
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l48"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l810"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l48", "g10", "l910"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingTheSameConnectivityTwice() {
        Network network = ConnectedComponentNetworkFactory.createTwoConnectedComponentsLinkedByASerieOfTwoBranches();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34+l45", new BranchContingency("l34"), new BranchContingency("l45")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34+l45");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(16, result.getValues("l34+l45").size());

        assertEquals(-2d / 3d, getContingencyValue(result, "l34+l45", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(result, "l34+l45", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l45", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g2", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g2", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g2", "l67"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l45", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l45", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l45", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l45", "g6", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l45", "g6", "l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testLosingConnectivityOnTwoBranches() {
        Network network = ConnectedComponentNetworkFactory.createThreeCc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34+l47", new BranchContingency("l34"), new BranchContingency("l47")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34+l47");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(33, result.getValues("l34+l47").size());

        assertEquals(-2d / 3d, getContingencyValue(result, "l34+l47", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l47", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(result, "l34+l47", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g2", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g6", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34+l47", "g9", "l89"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault()).join();

        assertEquals(0d, getContingencyValue(result, "l34", "d1", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "d1", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "d1", "l56"), LoadFlowAssert.DELTA_POWER);

        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d5", "l45")));
        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d5", "l46")));
        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d5", "l56")));

        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d6", "l45")));
        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d6", "l46")));
        assertTrue(Double.isNaN(getContingencyValue(result, "l34", "d6", "l56")));
    }

    @Test
    void testPhaseShifterUnrelatedContingency() {
        Network network = FourBusNetworkFactory.createWithPhaseTapChanger();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("l23");
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerPSTAngle(branch.getId(), ps1.getId())));

        List<Contingency> contingencies = List.of(new Contingency("l14", new BranchContingency("l14")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getPreContingencyValues().size());
        assertEquals(15d / 4d * Math.PI / 180d, getValue(result, "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-10d / 4d * Math.PI / 180d, getValue(result, "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 4d * Math.PI / 180d, getValue(result, "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(15d / 4d * Math.PI / 180d, getValue(result, "l23", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 4d * Math.PI / 180d, getValue(result, "l23", "l34"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getValuesByContingencyId().keySet().stream().filter(v -> v != null).count());
        assertEquals(5, result.getValues("l14").size());

        assertEquals(10d / 3d * Math.PI / 180d, getContingencyValue(result, "l14", "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-10d / 3d * Math.PI / 180d, getContingencyValue(result, "l14", "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l14", "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(10d / 3d * Math.PI / 180d, getContingencyValue(result, "l14", "l23", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l14", "l23", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testPhaseShifterConnectivityLoss() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("l56");
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerPSTAngle(branch.getId(), ps1.getId(), "l34")));

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l34").size());

        assertEquals(0d, getContingencyValue(result, "l34", "l56", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "l56", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "l56", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "l56", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "l56", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "l56", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "l56", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnTransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("l56");
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerPSTAngle(branch.getId(), ps1.getId(), "l56")));

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l56").size());

        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLcc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(1d / 3d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcVsc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(1d / 3d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcVscDistributedOnLoad() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(2d / 3d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d / 3d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccWithoutLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(7, contingencyResult.size());
        assertEquals(0.5d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.25d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getValue(contingencyResult, "g1", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 12d, getValue(contingencyResult, "g1", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 12d, getValue(contingencyResult, "g1", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g1", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d / 3d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d, getFunctionReference(contingencyResult, "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getFunctionReference(contingencyResult, "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getFunctionReference(contingencyResult, "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d / 3d, getFunctionReference(contingencyResult, "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l25")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(7, contingencyResult.size());
        assertEquals(1d / 3d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 6d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 6d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(2d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndTransformerWithoutLosingConnectivity() {
        Network network = HvdcNetworkFactory.createNetworkWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l23")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(6, contingencyResult.size());
        assertEquals(0.5d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.5d, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(3d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnHvdcLccAndTransformerAndLosingConnectivity() {
        Network network = HvdcNetworkFactory.createLinkedNetworkWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1").stream().map(network::getGenerator).collect(Collectors.toList()),
            List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56").stream().map(network::getBranch).collect(Collectors.toList()), "hvdc34");

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"), new BranchContingency("l23"), new BranchContingency("l25")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        List<SensitivityValue> contingencyResult = result.getValues("hvdc34");
        assertEquals(7, contingencyResult.size());
        assertEquals(0.5d, getValue(contingencyResult, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getValue(contingencyResult, "g1", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(3d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getFunctionReference(contingencyResult, "l25"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getFunctionReference(contingencyResult, "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testTrivialContingencyOnGenerator() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1", true);
        LoadFlowParameters loadFlowParameters = new LoadFlowParameters();
        loadFlowParameters.setDc(true);
        loadFlowParameters.setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        sensiParameters.setLoadFlowParameters(loadFlowParameters);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g2").stream().map(network::getGenerator).collect(Collectors.toList()),
                    List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("g6", new GeneratorContingency("g6")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        List<SensitivityValue> contingencyResult = result.getValues("g6");
        assertEquals(3, contingencyResult.size());
        assertEquals(0, getValue(contingencyResult, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, getValue(contingencyResult, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, getValue(contingencyResult, "g2", "l23"), LoadFlowAssert.DELTA_POWER);

        assertEquals(-4d / 3d, getFunctionReference(contingencyResult, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getFunctionReference(contingencyResult, "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, getFunctionReference(contingencyResult, "l23"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyMultipleLinesBreaksOneContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l24+l35", new BranchContingency("l24"), new BranchContingency("l35")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l24+l35");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(16, result.getValues("l24+l35").size());
        assertEquals(-0.5d, getContingencyValue(result, "l24+l35", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.5d, getContingencyValue(result, "l24+l35", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g2", "l56"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l24+l35", "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g6", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l24+l35", "g6", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l24+l35", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l24+l35", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l24+l35", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testCircularLossOfConnectivity() {
        Network network = ConnectedComponentNetworkFactory.createThreeCircularCc();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34+l27+l58", new BranchContingency("l34"), new BranchContingency("l27"), new BranchContingency("l58")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l34+l27+l58");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(36, result.getValues("l34+l27+l58").size());
        List<SensitivityValue> contingencyValues = result.getValues("l34+l27+l58");
        assertEquals(-2d / 3d, getContingencyValue(contingencyValues, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(contingencyValues, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(contingencyValues, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l45"));
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l79"));

        // Components that are not linked to slack should be NaN
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l45"));
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l79"));
    }

    @Test
    void testAsymetricLossOnMultipleComponents() {
        Network network = ConnectedComponentNetworkFactory.createAsymetricNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l27+l18+l39+l14", new BranchContingency("l27"), new BranchContingency("l18"), new BranchContingency("l39"), new BranchContingency("l14")));
        List<SensitivityFactor> factors = createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()), "l27+l18+l39+l14");
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(39, result.getValues("l27+l18+l39+l14").size());
        List<SensitivityValue> contingencyValues = result.getValues("l27+l18+l39+l14");
        assertEquals(-2d / 3d, getContingencyValue(contingencyValues, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(contingencyValues, "g2", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l18"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(contingencyValues, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l27"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l39"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g2", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l18"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l27"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l39"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g6", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l18"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l27"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l39"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(contingencyValues, "g9", "l89"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testRemainingGlskFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("g2", 25f));
        variables.add(new WeightedSensitivityVariable("g6", 40f));
        variables.add(new WeightedSensitivityVariable("d3", 35f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", "l34")).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies,
                variableSets, sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l34").size());
        assertEquals(-17d / 36d, getContingencyValue(result, "l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 18d, getContingencyValue(result, "l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-19d / 36d, getContingencyValue(result, "l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskFactorBug() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("g2", 25f));
        variables.add(new WeightedSensitivityVariable("g6", 40f));
        variables.add(new WeightedSensitivityVariable("g10", 35f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk")).collect(Collectors.toList());

        List<Contingency> contingencies = List.of(new Contingency("l45", new BranchContingency("l45")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies,
                variableSets, sensiParameters, LocalComputationManager.getDefault())
                .join();

        List<Contingency> contingencies2 = List.of(new Contingency("l34", new BranchContingency("l34")),
                                                   new Contingency("l45", new BranchContingency("l45")));
        SensitivityAnalysisResult result2 = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies2,
                variableSets, sensiParameters, LocalComputationManager.getDefault())
                .join();

        // result for l45 contingency should exactly be the same as l34 contingency for simulation 2 should not impact
        // l45 contingency.
        // an issue has been identified that is responsible in case of 2 consecutive GLSK sensitivity loosing connectivity
        // of bad reset of state
        for (Branch branch : network.getBranches()) {
            assertEquals(getContingencyValue(result, "l45", "glsk", branch.getId()),
                         getContingencyValue(result2, "l45", "glsk", branch.getId()),
                         0d);
        }
    }

    @Test
    void testRemainingGlskFactorsAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));

        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("g2", 25f));
        variables.add(new WeightedSensitivityVariable("g6", 40f));
        variables.add(new WeightedSensitivityVariable("d3", 35f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        List<SensitivityFactor> factors = new ArrayList<>();
        for (Contingency c : contingencies) {
            factors.addAll(network.getBranchStream().map(branch -> createBranchFlowPerLinearGlsk(branch.getId(), "glsk", c.getId())).collect(Collectors.toList()));
        }

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies,
            variableSets, sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l34").size());
        assertEquals(-17d / 36d, getContingencyValue(result, "l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 18d, getContingencyValue(result, "l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-19d / 36d, getContingencyValue(result, "l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testHvdcSensiRescale() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Network network1 = HvdcNetworkFactory.createNetworkWithGenerators();
        network1.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network1.getLine("l25").getTerminal1().disconnect();
        network1.getLine("l25").getTerminal2().disconnect();
        runLf(network1, sensiParameters.getLoadFlowParameters());
        Network network2 = HvdcNetworkFactory.createNetworkWithGenerators();
        network2.getHvdcLine("hvdc34").setActivePowerSetpoint(network1.getHvdcLine("hvdc34").getActivePowerSetpoint() + sensiChange);
        network2.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        network2.getLine("l25").getTerminal1().disconnect();
        network2.getLine("l25").getTerminal2().disconnect();
        runLf(network2, sensiParameters.getLoadFlowParameters());
        Map<String, Double> loadFlowDiff = network.getLineStream().map(line -> line.getId())
            .collect(Collectors.toMap(
                lineId -> lineId,
                line -> (network1.getLine(line).getTerminal1().getP() - network2.getLine(line).getTerminal1().getP()) / sensiChange
            ));

        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                           SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);
        SensitivityAnalysisResult result = sensiProvider.run(network, Collections.singletonList(new Contingency("l25", new BranchContingency("l25"))), Collections.emptyList(),
                sensiParameters, factors);

        assertEquals(loadFlowDiff.get("l12"), result.getValue("l25", "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l13"), result.getValue("l25", "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(loadFlowDiff.get("l23"), result.getValue("l25", "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("l25", "l25", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getValue("l25", "l45", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getValue("l25", "l46", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getValue("l25", "l56", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testNullValue() {
        double sensiChange = 10e-4;
        // test injection increase on loads
        Network network = HvdcNetworkFactory.createNetworkWithGenerators();
        network.getGeneratorStream().forEach(gen -> gen.setMaxP(2 * gen.getMaxP()));
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        ContingencyContext contingencyContext = ContingencyContext.all();
        List<SensitivityFactor> factors = SensitivityFactor.createMatrix(SensitivityFunctionType.BRANCH_ACTIVE_POWER, List.of("l12", "l13", "l23", "l25", "l45", "l46", "l56"),
                                                                           SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, List.of("hvdc34"), false, contingencyContext);

        SensitivityAnalysisResult result = sensiProvider.run(network, Collections.singletonList(new Contingency("hvdc34", new HvdcLineContingency("hvdc34"))), Collections.emptyList(),
                sensiParameters, factors);

        assertEquals(0d, result.getValue("hvdc34", "l12", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l13", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l23", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l25", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l45", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l46", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, result.getValue("hvdc34", "l56", "hvdc34").getValue(), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReconnectingMultipleLinesToRestoreConnectivity() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = new ArrayList<>();
        network.getBranchStream().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "d5", "l23+l24+l36+l35+l46")));

        List<Contingency> contingencies = List.of(new Contingency(
                "l23+l24+l36+l35+l46",
                new BranchContingency("l23"),
                new BranchContingency("l24"),
                new BranchContingency("l36"),
                new BranchContingency("l35"),
                new BranchContingency("l46")
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(11, result.getValues("l23+l24+l36+l35+l46").size());
        List<SensitivityValue> contingencyValues = result.getValues("l23+l24+l36+l35+l46");
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l24"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l35"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l36"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, getContingencyValue(contingencyValues, "d5", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, getContingencyValue(contingencyValues, "d5", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "d5", "l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 4d, getContingencyValue(contingencyValues, "d5", "l78"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionRefWithMultipleReconnections() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = new ArrayList<>();
        network.getBranchStream().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "d5")));

        List<String> contingencyBranchesId = Arrays.asList("l23", "l24", "l36", "l35", "l46");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();
        Network networkDisconnected = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        contingencyBranchesId.forEach(id -> {
            networkDisconnected.getLine(id).getTerminal1().disconnect();
            networkDisconnected.getLine(id).getTerminal2().disconnect();
        });

        runLf(networkDisconnected, sensiParameters.getLoadFlowParameters());
        List<Line> nonDisconnectedLines = networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList());
        assertNotEquals(0, nonDisconnectedLines.size());
        for (Line line : nonDisconnectedLines) {
            assertEquals(line.getTerminal1().getP(), getContingencyFunctionReference(result, line.getId(), contingencyId), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefWithOneReconnection() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedSingleComponent();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = new ArrayList<>();
        network.getBranchStream().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "g2")));

        List<String> contingencyBranchesId = Arrays.asList("l24", "l35");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        Network networkDisconnected = ConnectedComponentNetworkFactory.createHighlyConnectedSingleComponent();
        contingencyBranchesId.forEach(id -> {
            networkDisconnected.getLine(id).getTerminal1().disconnect();
            networkDisconnected.getLine(id).getTerminal2().disconnect();
        });

        runLf(networkDisconnected, sensiParameters.getLoadFlowParameters());
        List<Line> nonDisconnectedLines = networkDisconnected.getLineStream().filter(line -> !Double.isNaN(line.getTerminal1().getP())).collect(Collectors.toList());
        assertNotEquals(0, nonDisconnectedLines.size());
        for (Line line : nonDisconnectedLines) {
            assertEquals(line.getTerminal1().getP(), getContingencyFunctionReference(result, line.getId(), contingencyId), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testFunctionRefWithSequenceOfConnectivty() {
        // Test that the result if you compute sensitivity manually one by one,
        // or all at once does not change result on function reference
        // (especially if you loose compensation)
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        String branchId = "l36";
        String injectionId = "g3";
        Branch branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12"));
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"),  new BranchContingency("l56"),  new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(getContingencyFunctionReference(result78, branchId, "l78"), getContingencyFunctionReference(globalResult, branchId, "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result12, branchId, "l12"), getContingencyFunctionReference(globalResult, branchId, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result35and56and57, branchId, "l35+l56+l57"), getContingencyFunctionReference(globalResult, branchId, "l35+l56+l57"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionRefWithAdditionalFactors() {
        // Test that the result if you compute sensitivity manually one by one,
        // or all at once does not change result on function reference
        // (especially if you loose compensation)
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        String branchId = "l36";
        String injectionId = "g3";
        Branch branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12"));
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"),  new BranchContingency("l56"),  new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(getContingencyFunctionReference(result78, branchId, "l78"), getContingencyFunctionReference(globalResult, branchId, "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result12, branchId, "l12"), getContingencyFunctionReference(globalResult, branchId, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result35and56and57, branchId, "l35+l56+l57"), getContingencyFunctionReference(globalResult, branchId, "l35+l56+l57"), LoadFlowAssert.DELTA_POWER);
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
        Branch branch = network.getBranch(branchId);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease(branch.getId(), injectionId));

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12")); // change distribution
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"),  new BranchContingency("l56"),  new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(getContingencyValue(result78, "l78", injectionId, branchId), getContingencyValue(globalResult, "l78", injectionId, branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyValue(result12, "l12", injectionId, branchId), getContingencyValue(globalResult, "l12", injectionId, branchId), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyValue(result35and56and57, "l35+l56+l57", injectionId, branchId), getContingencyValue(globalResult, "l35+l56+l57", injectionId, branchId), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l56")));

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l56").size());

        assertEquals(-4d / 3d, getContingencyFunctionReference(result, "l12", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyFunctionReference(result, "l13", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, getContingencyFunctionReference(result, "l23", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getContingencyFunctionReference(result, "l34", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, getContingencyFunctionReference(result, "l45", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l56", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d, getContingencyFunctionReference(result, "l46", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerAndAContingency() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l48+l67")));

        List<Contingency> contingencies = List.of(new Contingency("l48+l67", new BranchContingency("l48"), new BranchContingency("l67")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());

        assertEquals(-4d / 3d, getContingencyFunctionReference(result, "l12", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyFunctionReference(result, "l13", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, getContingencyFunctionReference(result, "l23", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getContingencyFunctionReference(result, "l34", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l45", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4d, getContingencyFunctionReference(result, "l56", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, getContingencyFunctionReference(result, "l57", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyFunctionReference(result, "l89", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l67", "l48+l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerAndAContingencyInAdditionalFactors() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);

        List<SensitivityFactor> factors = new ArrayList<>();
        factors.add(createBranchFlowPerInjectionIncrease("l12", "g2", "l48+l67"));
        network.getBranchStream().filter(branch -> !branch.getId().equals("l12")).forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l48+l67")));

        List<Contingency> contingencies = List.of(new Contingency("l48+l67", new BranchContingency("l48"), new BranchContingency("l67")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());

        assertEquals(-4d / 3d, getContingencyFunctionReference(result, "l12", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyFunctionReference(result, "l13", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, getContingencyFunctionReference(result, "l23", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getContingencyFunctionReference(result, "l34", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l45", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4d, getContingencyFunctionReference(result, "l56", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, getContingencyFunctionReference(result, "l57", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyFunctionReference(result, "l89", "l48+l67"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l67", "l48+l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingConnectivityOnATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByATransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        List<SensitivityFactor> factors = new LinkedList<>();
        network.getBranches().forEach(branch -> factors.add(createBranchFlowPerInjectionIncrease(branch.getId(), "g2", "l34")));

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getValuesByContingencyId().size());
        assertEquals(7, result.getValues("l34").size());

        assertEquals(-5d / 3d, getContingencyFunctionReference(result, "l12", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyFunctionReference(result, "l13", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 3d, getContingencyFunctionReference(result, "l23", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testInjectionNotFoundAdditionalFactorContingency() {
        testInjectionNotFoundAdditionalFactorContingency(true);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformerThenLosingAConnectivityBreakingContingency() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBusWithTransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = new LinkedList<>();
        factors.add(createBranchFlowPerInjectionIncrease("l56", "g2"));

        Contingency transformerContingency = new Contingency("l67", new BranchContingency("l67")); // losing a transformer
        Contingency connectivityLosingContingency = new Contingency("l48", new BranchContingency("l48")); // losing connectivty

        SensitivityAnalysisResult resultBoth = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, List.of(transformerContingency, connectivityLosingContingency),
            Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault())
            .join();

        SensitivityAnalysisResult resultLosingConnectivityAlone = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, List.of(connectivityLosingContingency),
            Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault())
            .join();

        SensitivityAnalysisResult resultLosingTransformerAlone = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, List.of(transformerContingency),
            Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(getContingencyFunctionReference(resultLosingConnectivityAlone, "l56", "l48"), getContingencyFunctionReference(resultBoth, "l56", "l48"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(resultLosingTransformerAlone, "l56", "l67"), getContingencyFunctionReference(resultBoth, "l56", "l67"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyOnAWrongHvdc() {
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcVscWithGenerators();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        List<SensitivityFactor> factors = createFactorMatrix(List.of("g1", "g2").stream().map(network::getGenerator).collect(Collectors.toList()),
                List.of("l12", "l13", "l23").stream().map(network::getBranch).collect(Collectors.toList()));

        List<Contingency> contingencies = List.of(new Contingency("hvdc34", new HvdcLineContingency("wrong")));
        CompletionException e = assertThrows(CompletionException.class, () -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies,
            Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault())
            .join());
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        // different sensitivity for (g2, l46) on base case and after contingency l45
        assertEquals(0.133d, getValue(result, "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4d, getContingencyValue(result, "l45", "g2", "l46"), LoadFlowAssert.DELTA_POWER);

        // we open l45 at both sides
        Line l45 = network.getLine("l45");
        l45.getTerminal1().disconnect();
        l45.getTerminal2().disconnect();
        runDcLf(network);

        result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();

        // we now have as expected the sensitivity for (g2, l46) on base case and after contingency l45
        // because l45 is already open on base case
        assertEquals(0.4d, getValue(result, "g2", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.4d, getContingencyValue(result, "l45", "g2", "l46"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, getValue(result, "g2", "l45"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, getValue(result, "g2", "l45"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, Collections.emptyList(), Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getValues().size());
        // sensitivity on an open branch is zero
        assertEquals(0, getValue(result, "g2", "l45"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDebug() throws IOException {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        network.setCaseDate(DateTime.parse("2021-04-25T13:47:34.697+02:00"));
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

        SensitivityAnalysisResult result = sensiProvider.run(network, contingencies, variableSets, sensiParameters, factors);

        Path contingenciesFile = null;
        Path factorsFile = null;
        Path networkFile = null;
        Path parametersFile = null;
        Path variableSetsFile = null;
        FileSystem fileSystem = PlatformConfig.defaultConfig().getConfigDir().getFileSystem();
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
            compareTxt(Objects.requireNonNull(getClass().getResourceAsStream("/debug-contingencies.json")), is);
        }
        try (InputStream is = Files.newInputStream(factorsFile)) {
            compareTxt(Objects.requireNonNull(getClass().getResourceAsStream("/debug-factors.json")), is);
        }
        try (InputStream is = Files.newInputStream(networkFile)) {
            compareTxt(Objects.requireNonNull(getClass().getResourceAsStream("/debug-network.xiidm")), is);
        }
        try (InputStream is = Files.newInputStream(parametersFile)) {
            compareTxt(Objects.requireNonNull(getClass().getResourceAsStream("/debug-parameters.json")), is);
        }
        try (InputStream is = Files.newInputStream(variableSetsFile)) {
            compareTxt(Objects.requireNonNull(getClass().getResourceAsStream("/debug-variable-sets.json")), is);
        }

        String dateStr = contingenciesFile.getFileName().toString().substring(14, 37);
        DateTime date = DateTime.parse(dateStr, DateTimeFormat.forPattern(OpenSensitivityAnalysisProvider.DATE_TIME_FORMAT));

        List<SensitivityValue> values2 = sensiProvider.replay(date, fileSystem.getPath(debugDir));

        // assert we have exactly the same result with replay
        assertEquals(result.getValues().size(), values2.size());
        Iterator<SensitivityValue> itExpected = values2.iterator();

        for (SensitivityValue actual : result.getValues()) {
            SensitivityValue expected = itExpected.next();
            assertEquals(actual.getContingencyId(), expected.getContingencyId());
            assertEquals(actual.getValue(), expected.getValue(), LoadFlowAssert.DELTA_POWER);
            assertEquals(actual.getFunctionReference(), expected.getFunctionReference(), LoadFlowAssert.DELTA_POWER);
        }
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnLoads() {
        Network network = DanglingLineFactory.createWithLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "g1"));
        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result.getPreContingencyValues().size());
        assertEquals(0.1875, getValue(result, "g1", "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(75.881, getFunctionReference(result, "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1875, getContingencyValue(result, "dl1", "g1", "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(19.006, getContingencyFunctionReference(result, "l1", "dl1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);
        double initialP = l1.getTerminal1().getP();
        assertEquals(19.006, initialP, LoadFlowAssert.DELTA_POWER);
        network.getGenerator("g1").setTargetP(network.getGenerator("g1").getTargetP() + 1);
        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);
        double finalP = l1.getTerminal1().getP();
        assertEquals(19.194, finalP, LoadFlowAssert.DELTA_POWER);
        assertEquals(0.1875, finalP - initialP, LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testDanglingLineContingencyDistributedSlackOnGenerators() {
        Network network = DanglingLineFactory.createWithLoad();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl3_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l1", "load3"));
        List<Contingency> contingencies = List.of(new Contingency("dl1", new DanglingLineContingency("dl1")));
        SensitivityAnalysisResult result2 = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                sensiParameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(1, result2.getPreContingencyValues().size());
        assertEquals(-0.1874, getValue(result2, "load3", "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(75.813, getFunctionReference(result2, "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1875, getContingencyValue(result2, "dl1", "load3", "l1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1.875, getContingencyFunctionReference(result2, "l1", "dl1"), LoadFlowAssert.DELTA_POWER);

        network.getDanglingLine("dl1").getTerminal().disconnect();
        Line l1 = network.getLine("l1");
        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);
        double initialP = l1.getTerminal1().getP();
        assertEquals(1.875, initialP, LoadFlowAssert.DELTA_POWER);
        network.getLoad("load3").setP0(network.getLoad("load3").getP0() + 1);
        runLf(network, sensiParameters.getLoadFlowParameters(), Reporter.NO_OP);
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
        List<SensitivityFactor> factors = new ArrayList<>();
        factors.add(createBranchFlowPerPSTAngle("L1", ps1.getId()));

        List<Contingency> contingencies = List.of(new Contingency("PS1", new BranchContingency("PS1")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factors, contingencies, Collections.emptyList(),
                parameters, LocalComputationManager.getDefault())
                .join();
        assertEquals(100.0, getContingencyFunctionReference(result, "L1", "PS1"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.0, getContingencyValue(result, "PS1", "PS1", "L1"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingency() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("g6", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("l12", "l12", "l12"),
                    new LinearGlsk("glsk", "glsk", glskMap)));
        };
        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);

        assertEquals(100.050, getFunctionReference(result, network.getBranch("l12").getId()), LoadFlowAssert.DELTA_POWER);
        assertEquals(100.050, getContingencyFunctionReference(result, network.getBranch("l12").getId(), "additionnalline_0"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyOnWatchedLine() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("g6", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("l12", "l12", "l12"),
                    new LinearGlsk("glsk", "glsk", glskMap)));
        };
        List<Contingency> contingencies = List.of(new Contingency("l12", new BranchContingency("l12")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);

        assertEquals(100.050, getFunctionReference(result, network.getBranch("l12").getId()), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, getContingencyFunctionReference(result, network.getBranch("l12").getId(), "l12"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyOnWatchedLine2() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("g6", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("additionnalline_0", "additionnalline_0", "additionnalline_0"),
                    new LinearGlsk("glsk", "glsk", glskMap)));
        };
        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0, getValue(result, "glsk", "additionnalline_0"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0, getFunctionReference(result, network.getBranch("additionnalline_0").getId()), LoadFlowAssert.DELTA_POWER);
        assertEquals(0, getContingencyFunctionReference(result, network.getBranch("additionnalline_0").getId(), "additionnalline_0"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testGlskOutsideMainComponentWithContingencyBreakingConnectivity() {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponentsAndAdditionalLine2();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("g6", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("additionnalline_10", "additionnalline_10", "additionnalline_10"),
                    new LinearGlsk("glsk", "glsk", glskMap)));
        };
        List<Contingency> contingencies = List.of(new Contingency("additionnalline_0", new BranchContingency("additionnalline_0")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0, getValue(result, "glsk", "additionnalline_10"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0, getFunctionReference(result, network.getBranch("additionnalline_10").getId()), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyFunctionReference(result, network.getBranch("additionnalline_10").getId(), "additionnalline_0"), LoadFlowAssert.DELTA_POWER);
    }
}
