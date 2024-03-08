/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.test.AbstractSerDeTest;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.contingency.BranchContingency;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.HvdcNetworkFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.util.LoadFlowAssert;
import com.powsybl.sensitivity.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractSensitivityAnalysisTest extends AbstractSerDeTest {

    protected static final double SENSI_CHANGE = 10e-4;

    protected final DenseMatrixFactory matrixFactory = new DenseMatrixFactory();

    protected final OpenSensitivityAnalysisProvider sensiProvider = new OpenSensitivityAnalysisProvider(matrixFactory);

    protected final SensitivityAnalysis.Runner sensiRunner = new SensitivityAnalysis.Runner(sensiProvider);

    protected final OpenLoadFlowProvider loadFlowProvider = new OpenLoadFlowProvider(matrixFactory);

    protected final LoadFlow.Runner loadFlowRunner = new LoadFlow.Runner(loadFlowProvider);

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId, boolean distributedSlack) {
        return createParameters(dc, List.of(slackBusId), distributedSlack);
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, List<String> slackBusesIds, boolean distributedSlack) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc)
                .setDistributedSlack(distributedSlack);
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.NAME)
                .setSlackBusesIds(slackBusesIds);
        return sensiParameters;
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc, String slackBusId) {
        return createParameters(dc, slackBusId, false);
    }

    protected static SensitivityAnalysisParameters createParameters(boolean dc) {
        SensitivityAnalysisParameters sensiParameters = new SensitivityAnalysisParameters();
        LoadFlowParameters lfParameters = sensiParameters.getLoadFlowParameters();
        lfParameters.setDc(dc)
                .setDistributedSlack(true);
        OpenLoadFlowParameters.create(lfParameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        return sensiParameters;
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, String contingencyId, TwoSides side) {
        Objects.requireNonNull(injections);
        Objects.requireNonNull(branches);
        return injections.stream().flatMap(injection -> branches.stream().map(branch -> createBranchFlowPerInjectionIncrease(branch.getId(), injection.getId(), contingencyId, side))).collect(Collectors.toList());
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, TwoSides side) {
        return createFactorMatrix(injections, branches, null, side);
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches) {
        return createFactorMatrix(injections, branches, null, TwoSides.ONE);
    }

    protected static <T extends Injection<T>> List<SensitivityFactor> createFactorMatrix(List<T> injections, List<Branch> branches, String contingencyId) {
        return createFactorMatrix(injections, branches, contingencyId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId, String contingencyId) {
        return createBranchFlowPerInjectionIncrease(functionId, variableId, contingencyId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId, String contingencyId, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId, TwoSides side) {
        return createBranchFlowPerInjectionIncrease(functionId, variableId, null, side);
    }

    protected static SensitivityFactor createBranchFlowPerInjectionIncrease(String functionId, String variableId) {
        return createBranchFlowPerInjectionIncrease(functionId, variableId, null, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, String contingencyId, TwoSides side) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId), side);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, ContingencyContext contingencyContext) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, contingencyContext, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, String contingencyId) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, contingencyId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, ContingencyContext contingencyContext, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, true, contingencyContext);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId, TwoSides side) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, (String) null, side);
    }

    protected static SensitivityFactor createBranchFlowPerLinearGlsk(String functionId, String variableId) {
        return createBranchFlowPerLinearGlsk(functionId, variableId, (String) null, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId, String contingencyId) {
        return createBranchFlowPerPSTAngle(functionId, variableId, contingencyId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId, String contingencyId, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.TRANSFORMER_PHASE, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId, TwoSides side) {
        return createBranchFlowPerPSTAngle(functionId, variableId, null, side);
    }

    protected static SensitivityFactor createBranchFlowPerTransformerLegPSTAngle(String functionId, String variableId, ThreeSides side) {
        SensitivityVariableType fVariable = side.equals(ThreeSides.ONE) ? SensitivityVariableType.TRANSFORMER_PHASE_1
                : side.equals(ThreeSides.TWO) ? SensitivityVariableType.TRANSFORMER_PHASE_2
                : SensitivityVariableType.TRANSFORMER_PHASE_3;
        return new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionId, fVariable, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createTransformerLegFlowPerInjectionIncrease(String functionId, String variableId, ThreeSides side) {
        SensitivityFunctionType ftype = side.equals(ThreeSides.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                : side.equals(ThreeSides.TWO) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_2
                : SensitivityFunctionType.BRANCH_ACTIVE_POWER_3;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createBranchFlowPerPSTAngle(String functionId, String variableId) {
        return createBranchFlowPerPSTAngle(functionId, variableId, null, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchIntensityPerInjectionIncrease(String functionId, String variableId, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_CURRENT_1 : SensitivityFunctionType.BRANCH_CURRENT_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createTransformerLegIntensityPerInjectionIncrease(String functionId, String variableId, ThreeSides side) {
        SensitivityFunctionType ftype = side.equals(ThreeSides.ONE) ? SensitivityFunctionType.BRANCH_CURRENT_1
                : side.equals(ThreeSides.TWO) ? SensitivityFunctionType.BRANCH_CURRENT_2
                : SensitivityFunctionType.BRANCH_CURRENT_3;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.INJECTION_ACTIVE_POWER, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createBranchIntensityPerInjectionIncrease(String functionId, String variableId) {
        return createBranchIntensityPerInjectionIncrease(functionId, variableId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBranchIntensityPerPSTAngle(String functionId, String variableId, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_CURRENT_1 : SensitivityFunctionType.BRANCH_CURRENT_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.TRANSFORMER_PHASE, variableId, false, ContingencyContext.all());
    }

    protected static SensitivityFactor createBranchIntensityPerPSTAngle(String functionId, String variableId) {
        return createBranchIntensityPerPSTAngle(functionId, variableId, TwoSides.ONE);
    }

    protected static SensitivityFactor createBusVoltagePerTargetV(String functionId, String variableId, String contingencyId) {
        return new SensitivityFactor(SensitivityFunctionType.BUS_VOLTAGE, functionId, SensitivityVariableType.BUS_TARGET_VOLTAGE, variableId, false, Objects.isNull(contingencyId) ? ContingencyContext.all() : ContingencyContext.specificContingency(contingencyId));
    }

    protected static SensitivityFactor createBusVoltagePerTargetV(String functionId, String variableId) {
        return createBusVoltagePerTargetV(functionId, variableId, null);
    }

    protected static SensitivityFactor createHvdcInjection(String functionId, String variableId, TwoSides side) {
        SensitivityFunctionType ftype = side.equals(TwoSides.ONE) ? SensitivityFunctionType.BRANCH_ACTIVE_POWER_1 : SensitivityFunctionType.BRANCH_ACTIVE_POWER_2;
        return new SensitivityFactor(ftype, functionId, SensitivityVariableType.HVDC_LINE_ACTIVE_POWER, variableId, false, ContingencyContext.all());
    }

    protected void runAcLf(Network network) {
        runAcLf(network, ReportNode.NO_OP);
    }

    protected void runAcLf(Network network, ReportNode reportNode) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, new LoadFlowParameters(), reportNode)
                .join();
        if (!result.isFullyConverged()) {
            throw new PowsyblException("AC LF diverged");
        }
    }

    protected void runDcLf(Network network) {
        runDcLf(network, ReportNode.NO_OP);
    }

    protected void runDcLf(Network network, ReportNode reportNode) {
        LoadFlowParameters parameters = new LoadFlowParameters().setDc(true);
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, parameters, reportNode)
                .join();
        if (!result.isFullyConverged()) {
            throw new PowsyblException("DC LF failed");
        }
    }

    protected void runLf(Network network, LoadFlowParameters loadFlowParameters) {
        runLf(network, loadFlowParameters, ReportNode.NO_OP);
    }

    protected void runLf(Network network, LoadFlowParameters loadFlowParameters, ReportNode reportNode) {
        LoadFlowResult result = new OpenLoadFlowProvider(matrixFactory)
                .run(network, LocalComputationManager.getDefault(), VariantManagerConstants.INITIAL_VARIANT_ID, loadFlowParameters, reportNode)
                .join();
        if (!result.isFullyConverged()) {
            throw new PowsyblException("LF failed");
        }
    }

    protected void testInjectionNotFound(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = Collections.singletonList(createBranchFlowPerInjectionIncrease("NHV1_NHV2_1", "a"));

        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testInjectionNotFoundAdditionalFactor(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = Collections.singletonList(createBranchFlowPerInjectionIncrease("NHV1_NHV2_1", "a"));

        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testInjectionNotFoundAdditionalFactorContingency(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("NHV1_NHV2_1", "a"));

        List<Contingency> contingencies = List.of(new Contingency("a", new BranchContingency("NHV1_NHV2_2")));

        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testGlskInjectionNotFound(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = Collections.singletonList(createBranchFlowPerLinearGlsk("NHV1_NHV2_1", "glsk"));

        List<Contingency> contingencies = Collections.emptyList();

        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("a", 10f))));

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Injection 'a' not found", e.getCause().getMessage());
    }

    protected void testHvdcInjectionNotFound(boolean dc) {
        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "b1_vl_0", true);

        Network network = HvdcNetworkFactory.createTwoCcLinkedByAHvdcWithGenerators();

        List<SensitivityFactor> factors = List.of(createHvdcInjection("l12", "nop", TwoSides.ONE));

        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("HVDC line 'nop' cannot be found in the network.", e.getCause().getMessage());
    }

    protected void testBranchNotFound(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("b", "GEN"));

        List<Contingency> contingencies = Collections.emptyList();
        List<SensitivityVariableSet> variableSets = Collections.emptyList();

        CompletionException e = assertThrows(CompletionException.class, () -> sensiRunner.run(network, factors, contingencies, variableSets, sensiParameters));
        assertTrue(e.getCause() instanceof PowsyblException);
        assertEquals("Branch, tie line, dangling line or leg of 'b' not found", e.getCause().getMessage());
    }

    protected void testEmptyFactors(boolean dc) {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        runAcLf(network);

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "VLLOAD_0");

        List<SensitivityFactor> factors = Collections.emptyList();

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertTrue(result.getValues().isEmpty());
    }

    protected void testBranchFunctionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l56", "g1"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0d, result.getValues().iterator().next().getValue());
    }

    protected void testInjectionOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerInjectionIncrease("l12", "g3"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0f, result.getBranchFlow1SensitivityValue("g3", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
    }

    protected void testPhaseShifterOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerPSTAngle("l12", "l45"));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0d, result.getBranchFlow1SensitivityValue("l45", "l12", SensitivityVariableType.TRANSFORMER_PHASE), LoadFlowAssert.DELTA_POWER);
        if (dc) {
            assertEquals(100.00, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        } else {
            assertEquals(100.08, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        }
    }

    protected void testGlskOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l12", "glsk"));

        List<SensitivityVariableSet> variableSets = Collections.singletonList(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                                         new WeightedSensitivityVariable("g3", 2f))));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(0, result.getBranchFlow1SensitivityValue("glsk", "l12", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        if (dc) {
            assertEquals(100.000, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        } else {
            assertEquals(100.080, result.getBranchFlow1FunctionReferenceValue("l12"), LoadFlowAssert.DELTA_POWER);
        }
    }

    protected void testGlskAndLineOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l56", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("g6", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(1, result.getValues().size());
        assertEquals(Double.NaN, result.getBranchFlow1SensitivityValue("glsk", "l56", SensitivityVariableType.INJECTION_ACTIVE_POWER), LoadFlowAssert.DELTA_POWER);
        assertEquals(Double.NaN, result.getBranchFlow1FunctionReferenceValue("l56"), LoadFlowAssert.DELTA_POWER);
    }

    protected void testGlskPartiallyOutsideMainComponent(boolean dc) {
        Network network = HvdcNetworkFactory.createLccWithBiggerComponents();

        SensitivityAnalysisParameters sensiParameters = createParameters(dc, "vl1_0");

        List<SensitivityFactor> factors = List.of(createBranchFlowPerLinearGlsk("l12", "glsk"));

        List<SensitivityVariableSet> variableSets = List.of(new SensitivityVariableSet("glsk", List.of(new WeightedSensitivityVariable("ld2", 1f),
                                                                                                       new WeightedSensitivityVariable("g3", 2f))));

        SensitivityAnalysisResult result = sensiRunner.run(network, factors, Collections.emptyList(), variableSets, sensiParameters);

        assertEquals(1, result.getValues().size());

        List<SensitivityFactor> factorsInjection = List.of(createBranchFlowPerInjectionIncrease("l12", "ld2"));

        SensitivityAnalysisResult resultInjection = sensiRunner.run(network, factorsInjection, Collections.emptyList(), Collections.emptyList(), sensiParameters);

        assertEquals(resultInjection.getValues().iterator().next().getValue(), result.getValues().iterator().next().getValue(), LoadFlowAssert.DELTA_POWER);
    }
}
