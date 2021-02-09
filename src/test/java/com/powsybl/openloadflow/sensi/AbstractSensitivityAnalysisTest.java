/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.EmptyContingencyListProvider;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.NameSlackBusSelector;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.sensitivity.factors.BranchFlowPerLinearGlsk;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.variables.InjectionIncrease;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    protected static double getValue(SensitivityAnalysisResult result, String variableId, String functionId) {
        return result.getSensitivityValues().stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                .findFirst()
                .map(SensitivityValue::getValue)
                .orElse(Double.NaN);
    }

    protected static double getContingencyValue(SensitivityAnalysisResult result, String contingencyId, String variableId, String functionId) {
        return result.getSensitivityValuesContingencies().get(contingencyId).stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElse(Double.NaN);
    }

    protected static double getContingencyValue(List<SensitivityValue> result, String variableId, String functionId) {
        return result.stream().filter(value -> value.getFactor().getVariable().getId().equals(variableId) && value.getFactor().getFunction().getId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getValue)
                     .orElse(Double.NaN);
    }

    protected static double getFunctionReference(SensitivityAnalysisResult result, String functionId) {
        return result.getSensitivityValues().stream().filter(value -> value.getFactor().getFunction().getId().equals(functionId))
                .findFirst()
                .map(SensitivityValue::getFunctionReference)
                .orElse(Double.NaN);
    }

    protected static double getContingencyFunctionReference(SensitivityAnalysisResult result, String functionId, String contingencyId) {
        return result.getSensitivityValuesContingencies().get(contingencyId).stream().filter(value -> value.getFactor().getFunction().getId().equals(functionId))
                     .findFirst()
                     .map(SensitivityValue::getFunctionReference)
                     .orElse(Double.NaN);
    }

    protected void runAcLf(Network network) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, new LoadFlowParameters())
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("AC LF diverged");
        }
    }

    protected void runDcLf(Network network) {
        LoadFlowParameters parameters =  new LoadFlowParameters().setDc(true);
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, parameters)
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("DC LF failed");
        }
    }

    protected void runLf(Network network, LoadFlowParameters loadFlowParameters) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, loadFlowParameters)
                .join();
        if (!result.isOk()) {
            throw new PowsyblException("DC LF failed");
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault());
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault());
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
                factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault());
        CompletionException e = assertThrows(CompletionException.class, () -> sensiResult.join());
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Branch 'b' not found", e.getCause().getMessage());
    }

    protected void testEmptyFactors(boolean dc) {
        Network network = EurostagTutorialExample1Factory.create();
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");
        SensitivityFactorsProvider factorsProvider = n -> Collections.emptyList();
        SensitivityAnalysisResult result = sensiProvider.run(network, VariantManagerConstants.INITIAL_VARIANT_ID, factorsProvider, new EmptyContingencyListProvider(), sensiParameters, LocalComputationManager.getDefault()).join();
        assertTrue(result.getSensitivityValues().isEmpty());
    }
}
