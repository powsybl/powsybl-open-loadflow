/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.ConnectedComponentNetworkFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(5, result.getSensitivityValuesContingencies().get("l23").size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(5, result.getSensitivityValuesContingencies().get("l12").size());
        assertEquals(-1d / 15d, getContingencyValue(result, "l12", "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l12", "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.6d, getContingencyValue(result, "l12", "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 15d, getContingencyValue(result, "l12", "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l12", "g2", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionRefOnOneElement() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l23");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(0.05d, getValue(result, "g2", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.35d, getValue(result, "g2", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.25d, getValue(result, "g2", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g2", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.1d, getValue(result, "g2", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(5, result.getSensitivityValuesContingencies().get("l23+l34").size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(14, result.getSensitivityValuesContingencies().get("l34").size());
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
    }

    @Test
    void testConnectivityLossOnSingleLineWithDistributedSlack() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(14, result.getSensitivityValuesContingencies().get("l34").size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(36, result.getSensitivityValuesContingencies().get("l34+l48").size());

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
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l810"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g6", "l910"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l48", "g10", "l67"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(16, result.getSensitivityValuesContingencies().get("l34+l45").size());

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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(33, result.getSensitivityValuesContingencies().get("l34+l47").size());

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
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g6", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34+l47", "g9", "l56"), LoadFlowAssert.DELTA_POWER);
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getLoadStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
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
        Network network = FourBusNetworkFactory.createWithTransfo();
        runDcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        TwoWindingsTransformer ps1 = network.getTwoWindingsTransformer("l23");
        SensitivityFactorsProvider factorsProvider = n -> {
            List<SensitivityFactor> factors = new LinkedList<>();
            network.getBranches().forEach(branch -> factors.add(new BranchFlowPerPSTAngle(createBranchFlow(branch),
                    new PhaseTapChangerAngle(ps1.getId(), ps1.getNameOrId(), ps1.getId()))));
            return factors;
        };

        List<Contingency> contingencies = List.of(new Contingency("l14", new BranchContingency("l14")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(15d / 4d * Math.PI / 180d, getValue(result, "l23", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-10d / 4d * Math.PI / 180d, getValue(result, "l23", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 4d * Math.PI / 180d, getValue(result, "l23", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(15d / 4d * Math.PI / 180d, getValue(result, "l23", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 4d * Math.PI / 180d, getValue(result, "l23", "l34"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(5, result.getSensitivityValuesContingencies().get("l14").size());

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
        SensitivityFactorsProvider factorsProvider = n -> {
            List<SensitivityFactor> factors = new LinkedList<>();
            network.getBranches().forEach(branch -> factors.add(new BranchFlowPerPSTAngle(createBranchFlow(branch),
                    new PhaseTapChangerAngle(ps1.getId(), ps1.getNameOrId(), ps1.getId()))));
            return factors;
        };

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l34").size());

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
        SensitivityFactorsProvider factorsProvider = n -> {
            List<SensitivityFactor> factors = new LinkedList<>();
            network.getBranches().forEach(branch -> factors.add(new BranchFlowPerPSTAngle(createBranchFlow(branch),
                    new PhaseTapChangerAngle(ps1.getId(), ps1.getNameOrId(), ps1.getId()))));
            return factors;
        };

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l56").size());

        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l56", "l56", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testContingencyMultipleLinesBreaksOneContingency() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByTwoLines();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        List<Contingency> contingencies = List.of(new Contingency("l24+l35", new BranchContingency("l24"), new BranchContingency("l35")));
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(16, result.getSensitivityValuesContingencies().get("l24+l35").size());
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(36, result.getSensitivityValuesContingencies().get("l34+l27+l58").size());
        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l34+l27+l58");
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
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(39, result.getSensitivityValuesContingencies().get("l27+l18+l39+l14").size());
        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l27+l18+l39+l14");
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
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l79"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g6", "l89"), LoadFlowAssert.DELTA_POWER);

        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l18"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l27"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l39"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(contingencyValues, "g9", "l56"), LoadFlowAssert.DELTA_POWER);
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

        Map<String, Float> glskMap = new HashMap<>();
        glskMap.put("g2", 25f);
        glskMap.put("g6", 40f);
        glskMap.put("d3", 35f);
        LinearGlsk linearGlsk = new LinearGlsk("glsk", "glsk", glskMap);
        SensitivityFactorsProvider factorsProvider = n -> network.getBranchStream().map(branch -> new BranchFlowPerLinearGlsk(
            createBranchFlow(branch),
            linearGlsk
        )).collect(Collectors.toList());
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l34").size());
        assertEquals(-17d / 36d, getContingencyValue(result, "l34", "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 18d, getContingencyValue(result, "l34", "glsk", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-19d / 36d, getContingencyValue(result, "l34", "glsk", "l13"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l34", "glsk", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, getContingencyValue(result, "l34", "glsk", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testReconnectingMultipleLinesToRestoreConnectivity() {
        Network network = ConnectedComponentNetworkFactory.createHighlyConnectedNetwork();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b6_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);

        List<SensitivityFactor> factors = new ArrayList<>();
        network.getBranchStream().forEach(branch -> factors.add(new BranchFlowPerInjectionIncrease(
                createBranchFlow(branch),
                new InjectionIncrease("d5", "d5", "d5")
        )));

        SensitivityFactorsProvider factorsProvider = net -> factors;

        List<Contingency> contingencies = List.of(new Contingency(
                "l23+l24+l36+l35+l46",
                new BranchContingency("l23"),
                new BranchContingency("l24"),
                new BranchContingency("l36"),
                new BranchContingency("l35"),
                new BranchContingency("l46")
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(11, result.getSensitivityValuesContingencies().get("l23+l24+l36+l35+l46").size());
        List<SensitivityValue> contingencyValues = result.getSensitivityValuesContingencies().get("l23+l24+l36+l35+l46");
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
        network.getBranchStream().forEach(branch -> factors.add(new BranchFlowPerInjectionIncrease(
                createBranchFlow(branch),
                new InjectionIncrease("d5", "d5", "d5")
        )));

        SensitivityFactorsProvider factorsProvider = net -> factors;
        List<String> contingencyBranchesId = Arrays.asList("l23", "l24", "l36", "l35", "l46");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
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
        network.getBranchStream().forEach(branch -> factors.add(new BranchFlowPerInjectionIncrease(
                createBranchFlow(branch),
                new InjectionIncrease("g2", "g2", "g2")
        )));

        SensitivityFactorsProvider factorsProvider = net -> factors;
        List<String> contingencyBranchesId = Arrays.asList("l24", "l35");
        String contingencyId = String.join("+", contingencyBranchesId);
        List<Contingency> contingencies = List.of(new Contingency(
                contingencyId,
                contingencyBranchesId.stream().map(BranchContingency::new).collect(Collectors.toList())
        ));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
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
        List<SensitivityFactor> factors = List.of(new BranchFlowPerInjectionIncrease(
                createBranchFlow(branch),
                new InjectionIncrease(injectionId, injectionId, injectionId)
        ));

        SensitivityFactorsProvider factorsProvider = net -> factors;

        Contingency contingency78 = new Contingency("l78", new BranchContingency("l78"));
        Contingency contingency12 = new Contingency("l12", new BranchContingency("l12"));
        Contingency contingency35and56and57 = new Contingency("l35+l56+l57", new BranchContingency("l35"),  new BranchContingency("l56"),  new BranchContingency("l57"));

        Function<List<Contingency>, SensitivityAnalysisResult> resultProvider = contingencies -> sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault()).join();

        SensitivityAnalysisResult result78 = resultProvider.apply(List.of(contingency78));
        SensitivityAnalysisResult result12 = resultProvider.apply(List.of(contingency12));
        SensitivityAnalysisResult result35and56and57 = resultProvider.apply(List.of(contingency35and56and57));
        SensitivityAnalysisResult globalResult = resultProvider.apply(List.of(contingency12, contingency78, contingency35and56and57));

        assertEquals(getContingencyFunctionReference(result78, branchId, "l78"), getContingencyFunctionReference(globalResult, branchId, "l78"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result12, branchId, "l12"), getContingencyFunctionReference(globalResult, branchId, "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(getContingencyFunctionReference(result35and56and57, branchId, "l35+l56+l57"), getContingencyFunctionReference(globalResult, branchId, "l35+l56+l57"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcWithATransformerLinkedByASingleLine();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        SensitivityFactorsProvider factorsProvider = n -> {
            List<SensitivityFactor> factors = new LinkedList<>();
            network.getBranches().forEach(branch -> factors.add(new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                new InjectionIncrease("g2", "g2", "g2"))));
            return factors;
        };

        List<Contingency> contingencies = List.of(new Contingency("l56", new BranchContingency("l56")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l56").size());

        assertEquals(-4d / 3d, getContingencyFunctionReference(result, "l12", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyFunctionReference(result, "l13", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(5d / 3d, getContingencyFunctionReference(result, "l23", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d, getContingencyFunctionReference(result, "l34", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(2d, getContingencyFunctionReference(result, "l45", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyFunctionReference(result, "l56", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d, getContingencyFunctionReference(result, "l46", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testFunctionReferenceWhenLosingConnectivityOnATransformer() {
        Network network = ConnectedComponentNetworkFactory.createTwoCcLinkedByATransformer();

        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            List<SensitivityFactor> factors = new LinkedList<>();
            network.getBranches().forEach(branch -> factors.add(new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                new InjectionIncrease("g2", "g2", "g2"))));
            return factors;
        };

        List<Contingency> contingencies = List.of(new Contingency("l34", new BranchContingency("l34")));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies,
            sensiParameters, LocalComputationManager.getDefault())
            .join();

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(7, result.getSensitivityValuesContingencies().get("l34").size());

        assertEquals(-5d / 3d, getContingencyFunctionReference(result, "l12", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyFunctionReference(result, "l13", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(4d / 3d, getContingencyFunctionReference(result, "l23", "l34"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testAdditionalFactorsNotSupported() {
        Network network = FourBusNetworkFactory.create();
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        Branch l = network.getBranch("l23");
        Generator g = network.getGenerator("g1");
        List<Contingency> contingencies = List.of(new Contingency(l.getId(), new BranchContingency(l.getId())));
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network) {
                return List.of(new BranchFlowPerInjectionIncrease(createBranchFlow(l), createInjectionIncrease(g)));
            }
        };
        CompletableFuture<SensitivityAnalysisResult> result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingencies, sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, result::join);
        assertEquals("Factors specific to base case not yet supported", e.getCause().getMessage());

        SensitivityFactorsProvider factorsProvider2 = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                return List.of(new BranchFlowPerInjectionIncrease(createBranchFlow(l), createInjectionIncrease(g)));
            }
        };

        CompletableFuture<SensitivityAnalysisResult> result2 = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider2, contingencies, sensiParameters, LocalComputationManager.getDefault());
        e = assertThrows(CompletionException.class, result2::join);
        assertEquals("Factors specific to one contingency not yet supported", e.getCause().getMessage());
    }
}
