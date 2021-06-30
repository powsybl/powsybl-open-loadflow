/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSensitivityAnalysisTest extends AbstractConverterTest {

    protected final DenseMatrixFactory matrixFactory = new DenseMatrixFactory();

    protected final OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId, boolean distributedSlack) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc);
        lfParameters.setDistributedSlack(distributedSlack);
        OpenLoadFlowParameters lfParametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusId(slackBusId);
        lfParameters.addExtension(OpenLoadFlowParameters.class, lfParametersExt);
        return sensiParameters;
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId) {
        return createParameters(dc, slackBusId, false);
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc);
        lfParameters.setDistributedSlack(true);
        OpenLoadFlowParameters lfParametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        lfParameters.addExtension(OpenLoadFlowParameters.class, lfParametersExt);
        return sensiParameters;
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, String contingencyId) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return injections.stream().flatMap(injection -> branches.stream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), injection.getId(), contingencyId))).collect(Collectors.toList());
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return createFactorMatrix(injections, branches, null);
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId, String contingencyId) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId) {
        return createBranchFlowPerInjectionIncrease(functionId, variableId, null);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, String contingencyId) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, true, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, null);
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId, String contingencyId) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, functionId, SensitivityVariableType.TRANSFORMER_PHASE, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId) {
        return createBranchFlowPerPSTAngle(functionId, variableId, null);
    }

    protected static SensitivityFactor createBranchIntensityPerPSTAngle(String functionId, String variableId) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_CURRENT, functionId, SensitivityVariableType.TRANSFORMER_PHASE, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createBusVoltagePerTargetV(String functionId, String variableId, String contingencyId) {
        return new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, functionId, SensitivityVariableType.BUS_TARGET_VOLTAGE, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBusVoltagePerTargetV(String functionId, String variableId) {
        return createBusVoltagePerTargetV(functionId, variableId, null);
    }

    protected static SensitivityFactor createHvdcInjection(String functionId, String variableId) {
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER, functionId, SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, variableId, false, ContingencyContext.all());
    }

    protected static double getValue(SensitivityAnalysisResult result, String variableId, String functionId) {
        return getValue(result.getValues(), variableId, functionId);
    }

    protected static double getValue(Collection<SensitivityValue> result, String variableId, String functionId) {
        return result.stream().filter(value -> value.getFactor().getVariableId().equals(variableId) && value.getFactor().getFunctionId().equals(functionId))
            .findFirst()
            .map(SensitivityValue::getValue)
            .orElseThrow();
    }

    protected static double getContingencyValue(SensitivityAnalysisResult result, String contingencyId, String variableId, String functionId) {
        return result.getValues(contingencyId).stream().filter(value -> value.getFactor().getVariableId().equals(variableId) && value.getFactor().getFunctionId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElseThrow();
    }

    protected static double getContingencyValue(List<SensitivityValue> result, String variableId, String functionId) {
        return result.stream().filter(value -> value.getFactor().getVariableId().equals(variableId) && value.getFactor().getFunctionId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElseThrow();
    }

    protected static double getFunctionReference(SensitivityAnalysisResult result, String functionId) {
        return getFunctionReference(result.getValues(), functionId);
    }

    protected static double getContingencyFunctionReference(SensitivityAnalysisResult result, String functionId, String contingencyId) {
        return getFunctionReference(result.getValues(contingencyId), functionId);
    }

    protected static double getFunctionReference(Collection<SensitivityValue> result, String functionId) {
        return result.stream().filter(value -> value.getFactor().getFunctionId().equals(functionId))
            .findFirst()
            .map(SensitivityValue::getFunctionReference)
            .orElseThrow();
    }

    protected void runAcLf(Network network) {
        runAcLf(network, Reporter.NO_OP);
    }

    protected void runAcLf(Network network, Reporter reporter) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, new LoadFlowParameters(), reporter)
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("AC LF diverged");
        }
    }

    protected void runDcLf(Network network) {
        runDcLf(network, Reporter.NO_OP);
    }

    protected void runDcLf(Network network, Reporter reporter) {
        LoadFlowParameters parameters =  new LoadFlowParameters().setDc(true);
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, parameters, reporter)
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("DC LF failed");
        }
    }

    protected void runLf(Network network, LoadFlowParameters loadFlowParameters) {
        runLf(network, loadFlowParameters, Reporter.NO_OP);
    }

    protected void runLf(Network network, LoadFlowParameters loadFlowParameters, Reporter reporter) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, loadFlowParameters, reporter)
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("LF failed");
        }
    }

    protected void testInjectionNotFound(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Branch branch = n.getBranch("NHV1_NHV2_1");
            return Collections.singletonList(createBranchFlowPerInjectionIncrease(branch.getId(), "a"));
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testInjectionNotFoundAdditionalFactor(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network) {
                Branch branch = network.getBranch("NHV1_NHV2_1");
                return Collections.singletonList(createBranchFlowPerInjectionIncrease(branch.getId(), "a"));
            }
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
            factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testInjectionNotFoundAdditionalFactorContingency(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = new SensitivityFactorsProvider() {
            @Override
            public List<SensitivityFactor> getCommonFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network) {
                return Collections.emptyList();
            }

            @Override
            public List<SensitivityFactor> getAdditionalFactors(Network network, String contingencyId) {
                Branch branch = network.getBranch("NHV1_NHV2_1");
                return Collections.singletonList(createBranchFlowPerInjectionIncrease(branch.getId(), "a"));
            }
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
            factorsProvider, Collections.singletonList(new Contingency("a", new BranchContingency("NHV1_NHV2_2"))), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testGlskInjectionNotFound(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Branch branch = n.getBranch("NHV1_NHV2_1");
            return Collections.singletonList(createBranchFlowPerLinearGlsk(branch.getId(), "glsk"));
        };
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", Collections.singletonList(new WeightedSensitivityVariable("a", 10f))));
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), variableSets, sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testHvdcInjectionNotFound(boolean dc) {
        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "b1_vl_0", true);
        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();
        List<SensitivityFactor> factors = List.of(createHvdcInjection("l12", "nop"));

        PowsyblException e = assertThrows(PowsyblException.class, () -> sensiProvider.run(network, Collections.emptyList(), Collections.emptyList(), sensiParameters, factors));
        assertEquals("HVDC line 'nop' cannot be found in the network.", e.getMessage());
    }

    protected void testBranchNotFound(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("GEN");

            return Collections.singletonList(createBranchFlowPerInjectionIncrease("b", gen.getId()));
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Branch 'b' not found", e.getCause().getMessage());
    }

    protected void testEmptyFactors(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.emptyList();
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getValues().isEmpty());
    }

    protected void testBranchFunctionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("g1");
            return Collections.singletonList(createBranchFlowPerInjectionIncrease("l56", gen.getId()));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getValues().size());
        assertEquals(0d, result.getValues().iterator().next().getValue());
    }

    protected void testInjectionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("g3");
            return Collections.singletonList(createBranchFlowPerInjectionIncrease("l12", gen.getId()));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getValues().size());
        assertEquals(0f, getValue(result, "g3", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    protected void testPhaseShifterOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.singletonList(createBranchFlowPerPSTAngle("l12", "l45"));
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getValues().size());
        assertEquals(0d, getValue(result, "l45", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    protected void testGlskOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.singletonList(createBranchFlowPerLinearGlsk("l12", "glsk"));
        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("g6", 1f));
        variables.add(new WeightedSensitivityVariable("g3", 2f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), variableSets, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getValues().size());
        assertEquals(0, getValue(result, "glsk", "l12"), LoadFlowAssert.DELTA_POWER);
    }

    protected void testGlskPartiallyOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.singletonList(createBranchFlowPerLinearGlsk("l12", "glsk"));
        List<WeightedSensitivityVariable> variables = new ArrayList<>();
        variables.add(new WeightedSensitivityVariable("ld2", 1f));
        variables.add(new WeightedSensitivityVariable("g3", 2f));
        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", variables));

        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), variableSets, sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getValues().size());

        SensitivityFactorsProvider factorsProviderInjection = n -> Collections.singletonList(createBranchFlowPerInjectionIncrease("l12", "ld2"));

        SensitivityAnalysisResult resultInjection = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProviderInjection, Collections.emptyList(), Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(resultInjection.getValues().iterator().next().getValue(), result.getValues().iterator().next().getValue(), LoadFlowAssert.DELTA_POWER);
    }
}
