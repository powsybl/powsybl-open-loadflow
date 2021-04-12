/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSensitivityAnalysisTest {

    protected final DenseMatrixFactory matrixFactory = new DenseMatrixFactory();

    protected final OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId, boolean distributedSlack) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc);
        lfParameters.setDistributedSlack(distributedSlack);
        OpenLoadFlowParameters lfParametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new NameSlackBusSelector(slackBusId));
        lfParameters.addExtension(OpenLoadFlowParameters.class, lfParametersExt);
        return sensiParameters;
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId) {
        return createParameters(dc, slackBusId, false);
    }

    protected static <T extends Injection<T>> InjectionIncrease createInjectionIncrease(T injection) {
        return new InjectionIncrease(injection.getId(), injection.getId(), injection.getId());
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return injections.stream().flatMap(injection -> branches.stream().map(branch -> new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                createInjectionIncrease(injection)))).collect(Collectors.toList());
    }

    protected static BranchFlow createBranchFlow(Branch branch) {
        return new BranchFlow(branch.getId(), branch.getNameOrId(), branch.getId());
    }

    protected static BranchIntensity createBranchIntensity(Branch branch) {
        return new BranchIntensity(branch.getId(), branch.getNameOrId(), branch.getId());
    }

    protected static double getValue(SensitivityAnalysisResult result, String variableId, String functionId) {
        return getValue(result.getSensitivityValues(), variableId, functionId);
    }

    protected static double getValue(Collection<SensitivityValue> result, String variableId, String functionId) {
        return result.stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
            .findFirst()
            .map(SensitivityValue::getValue)
            .orElseThrow();
    }

    protected static double getContingencyValue(SensitivityAnalysisResult result, String contingencyId, String variableId, String functionId) {
        return result.getSensitivityValuesContingencies().get(contingencyId).stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElseThrow();
    }

    protected static double getContingencyValue(List<SensitivityValue> result, String variableId, String functionId) {
        return result.stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElseThrow();
    }

    protected static double getFunctionReference(SensitivityAnalysisResult result, String functionId) {
        return getFunctionReference(result.getSensitivityValues(), functionId);
    }

    protected static double getContingencyFunctionReference(SensitivityAnalysisResult result, String functionId, String contingencyId) {
        return getFunctionReference(result.getSensitivityValuesContingencies().get(contingencyId), functionId);
    }

    protected static double getFunctionReference(Collection<SensitivityValue> result, String functionId) {
        return result.stream().filter(value -> value.getFactor().getFunction().getId().equals(functionId))
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
            return Collections.singletonList(new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                    new InjectionIncrease("a", "a", "a")));
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
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
                return Collections.singletonList(new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                    new InjectionIncrease("a", "a", "a")));
            }
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
            factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
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
                return Collections.singletonList(new BranchFlowPerInjectionIncrease(createBranchFlow(branch),
                    new InjectionIncrease("a", "a", "a")));
            }
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
            factorsProvider, Collections.singletonList(new Contingency("a", new BranchContingency("NHV1_NHV2_2"))), sensiParameters, LocalComputationManager.getDefault());
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
            return Collections.singletonList(new BranchFlowPerLinearGlsk(
                createBranchFlow(branch),
                new LinearGlsk("glsk", "glsk", Collections.singletonMap("a", 10f))
            ));
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testBranchNotFound(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("GEN");
            return Collections.singletonList(new BranchFlowPerInjectionIncrease(new BranchFlow("b", "b", "b"),
                    createInjectionIncrease(gen)));
        };
        CompletableFuture<SensitivityAnalysisResult> sensiResult = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID,
                factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Branch 'b' not found", e.getCause().getMessage());
    }

    protected void testEmptyFactors(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.emptyList();
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getSensitivityValues().isEmpty());
    }

    protected void testBranchFunctionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("g1");
            return Collections.singletonList(new BranchFlowPerInjectionIncrease(new BranchFlow("l56", "l56", "l56"),
                createInjectionIncrease(gen)));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());
        assertEquals(0d, result.getSensitivityValues().iterator().next().getValue());
    }

    protected void testInjectionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Generator gen = n.getGenerator("g3");
            return Collections.singletonList(new BranchFlowPerInjectionIncrease(new BranchFlow("l12", "l12", "l12"),
                createInjectionIncrease(gen)));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getSensitivityValues().isEmpty());
    }

    protected void testPhaseShifterOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            return Collections.singletonList(new BranchFlowPerPSTAngle(new BranchFlow("l12", "l12", "l12"),
                new PhaseTapChangerAngle("l45", "l45", "l45")));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getSensitivityValues().isEmpty());
    }

    protected void testGlskOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("g6", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("l12", "l12", "l12"),
                new LinearGlsk("glsk", "glsk", glskMap)));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getSensitivityValues().isEmpty());
    }

    protected void testGlskPartiallyOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");
        SensitivityFactorsProvider factorsProvider = n -> {
            Map<String, Float> glskMap = new HashMap<>();
            glskMap.put("ld2", 1f);
            glskMap.put("g3", 2f);
            return Collections.singletonList(new BranchFlowPerLinearGlsk(new BranchFlow("l12", "l12", "l12"),
                new LinearGlsk("glsk", "glsk", glskMap)));
        };
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(1, result.getSensitivityValues().size());

        SensitivityFactorsProvider factorsProviderInjection = n -> {
            return Collections.singletonList(new BranchFlowPerInjectionIncrease(new BranchFlow("l12", "l12", "l12"),
                new InjectionIncrease("ld2", "ld2", "ld2")));
        };
        SensitivityAnalysisResult resultInjection = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProviderInjection, Collections.emptyList(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertEquals(resultInjection.getSensitivityValues().iterator().next().getValue(), result.getSensitivityValues().iterator().next().getValue(), LoadFlowAssert.DELTA_POWER);
    }
}
