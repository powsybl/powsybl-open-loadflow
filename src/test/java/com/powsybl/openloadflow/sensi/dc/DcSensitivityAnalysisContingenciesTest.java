package com.powsybl.openloadflow.sensi.dc;

import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.network.ConnectedComponentNetworkFactory;
import com.powsybl.openloadflow.network.FourBusNetworkFactory;
import com.powsybl.openloadflow.sensi.AbstractSensitivityAnalysisTest;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DcSensitivityAnalysisContingenciesTest extends AbstractSensitivityAnalysisTest {
    @Test
    void testContingencyWithOneElementAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            Branch l = network.getBranch("l23");
            contingencies.add(new Contingency(l.getId(), new BranchContingency(l.getId())));
            return contingencies;
        };
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            Branch l = network.getBranch("l12");
            contingencies.add(new Contingency(l.getId(), new BranchContingency(l.getId())));
            return contingencies;
        };
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
    void testContingencyWithTwoElementsAwayFromSlack() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            contingencies.add(new Contingency("l23+l34", new BranchContingency("l23"), new BranchContingency("l34")));
            return contingencies;
        };
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g2")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
    void testConnectivityLoss() {
        Network network = FourBusNetworkFactory.create();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            contingencies.add(new Contingency("l23+l12", new BranchContingency("l23"), new BranchContingency("l12")));
            return contingencies;
        };
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX);
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().filter(gen -> gen.getId().equals("g1")).collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
                sensiParameters, LocalComputationManager.getDefault())
                                                        .join();

        assertEquals(5, result.getSensitivityValues().size());
        assertEquals(0.175d, getValue(result, "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.275d, getValue(result, "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-0.125d, getValue(result, "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.025d, getValue(result, "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0.15d, getValue(result, "g1", "l13"), LoadFlowAssert.DELTA_POWER);

        assertEquals(1, result.getSensitivityValuesContingencies().size());
        assertEquals(5, result.getSensitivityValuesContingencies().get("l23+l12").size());
        assertEquals(2d / 9d, getContingencyValue(result, "l23+l12", "g1", "l14"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23+l12", "g1", "l12"), LoadFlowAssert.DELTA_POWER);
        assertEquals(0d, getContingencyValue(result, "l23+l12", "g1", "l23"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 9d, getContingencyValue(result, "l23+l12", "g1", "l34"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 9d, getContingencyValue(result, "l23+l12", "g1", "l13"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLine() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            contingencies.add(new Contingency("l34", new BranchContingency("l34")));
            return contingencies;
        };
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
        assertEquals(-1d / 3d, getContingencyValue(result, "l34", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d / 3d, getContingencyValue(result, "l34", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnSingleLineWithDistributedSlack() {
        Network network = ConnectedComponentNetworkFactory.createTwoComponentWithGeneratorAndLoad();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", true);
        sensiParameters.getLoadFlowParameters().setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            contingencies.add(new Contingency("l34", new BranchContingency("l34")));
            return contingencies;
        };
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
        assertEquals(1d / 9d, getContingencyValue(result, "l34", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-4d / 9d, getContingencyValue(result, "l34", "g6", "l46"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-5d / 9d, getContingencyValue(result, "l34", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
    }

    @Test
    void testConnectivityLossOnTwoComponentAtATime() {
        Network network = ConnectedComponentNetworkFactory.createThreeCcLinkedByASingleBus();
        runDcLf(network);
        SensitivityAnalysisParameters sensiParameters = createParameters(true, "b1_vl_0", false);
        ContingenciesProvider contingenciesProvider = n -> {
            List<Contingency> contingencies = new ArrayList<>();
            contingencies.add(new Contingency("l34+l48", new BranchContingency("l34"), new BranchContingency("l48")));
            return contingencies;
        };
        SensitivityFactorsProvider factorsProvider = n -> createFactorMatrix(network.getGeneratorStream().collect(Collectors.toList()),
                network.getBranchStream().collect(Collectors.toList()));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, contingenciesProvider,
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
        assertEquals(-1d, getContingencyValue(result, "l34+l48", "g6", "l45"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d / 3d, getContingencyValue(result, "l34+l48", "g6", "l56"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l48", "g6", "l57"), LoadFlowAssert.DELTA_POWER);
        assertEquals(1d / 3d, getContingencyValue(result, "l34+l48", "g6", "l67"), LoadFlowAssert.DELTA_POWER);
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
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l48", "g10", "l89"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-2d / 3d, getContingencyValue(result, "l34+l48", "g10", "l810"), LoadFlowAssert.DELTA_POWER);
        assertEquals(-1d / 3d, getContingencyValue(result, "l34+l48", "g10", "l910"), LoadFlowAssert.DELTA_POWER);
    }
}
