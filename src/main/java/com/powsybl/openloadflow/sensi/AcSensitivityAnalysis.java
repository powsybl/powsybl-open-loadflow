/*
 * Copyright (c) 2020-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.action.Action;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.strategy.OperatorStrategy;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.equations.*;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.mt.BufferedFactorReader;
import com.powsybl.openloadflow.sensi.mt.SequentialSensitivityResultWriter;
import com.powsybl.openloadflow.util.Lists2;
import com.powsybl.openloadflow.util.Reports;
import com.powsybl.openloadflow.util.mt.ContingencyMultiThreadHelper;
import com.powsybl.sensitivity.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gael Macherel {@literal <gael.macherel at artelys.com>}
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis<AcVariableType, AcEquationType> {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory, SensitivityAnalysisParameters parameters) {
        super(matrixFactory, connectivityFactory, parameters);
    }

    private void calculateSensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups, DenseMatrix factorsState,
                                            int contingencyIndex, SensitivityResultWriter resultWriter) {
        Set<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactorsSet = new HashSet<>(lfFactors);

        // VALID_ONLY_FOR_FUNCTION status is for factors where variable element is not in the main connected component but reference element is.
        // Therefore, the sensitivity is known to value 0 and the reference value can be computed.
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    if (!filterSensitivityValue(0, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                        resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, -1, 0, unscaleFunction(factor, factor.getFunctionReference()));
                    }
                });

        for (SensitivityFactorGroup<AcVariableType, AcEquationType> factorGroup : factorGroups.getList()) {
            for (LfSensitivityFactor<AcVariableType, AcEquationType> factor : factorGroup.getFactors()) {
                if (!lfFactorsSet.contains(factor)) {
                    continue;
                }
                double sensi;
                double ref;
                if (factor.getSensitivityValuePredefinedResult() != null) {
                    sensi = factor.getSensitivityValuePredefinedResult();
                } else {
                    if (!factor.getFunctionEquationTerm().isActive()) {
                        throw new PowsyblException("Found an inactive equation for a factor that has no predefined result");
                    }
                    sensi = factor.getFunctionEquationTerm().calculateSensi(factorsState, factorGroup.getIndex());
                    // Add the direct term (explicit dependence of the function on the variable), if any
                    sensi += computeParameterDirectPartial(factor);
                }
                if (factor.getFunctionPredefinedResult() != null) {
                    ref = factor.getFunctionPredefinedResult();
                } else {
                    ref = factor.getFunctionReference();
                }
                double unscaledSensi = unscaleSensitivity(factor, sensi);
                if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                    resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, -1, unscaledSensi, unscaleFunction(factor, ref));
                }
            }
        }
    }

    /**
     * Direct term of a sensitivity factor: the explicit dependence of the monitored function on the variable,
     * to be added to the indirect term flowing through the Jacobian. A direct term exists only when the function
     * depends explicitly (not only through the state) on the variable, i.e. for self-sensitivity where the
     * variable element and the function element are the same. Today only branch parameters (R / X / Y) contribute;
     * every other variable type returns 0.
     */
    private static double computeParameterDirectPartial(LfSensitivityFactor<AcVariableType, AcEquationType> factor) {
        if (!(factor instanceof SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType> singleFactor)) {
            return 0;
        }
        SensitivityVariableType varType = factor.getVariableType();
        if (varType != SensitivityVariableType.BRANCH_RESISTANCE && varType != SensitivityVariableType.BRANCH_REACTANCE
                && varType != SensitivityVariableType.BRANCH_ADMITTANCE) {
            return 0; // only branch parameters have a direct term; other variables act only through the state
        }
        LfBranch branch = (LfBranch) singleFactor.getVariableElement();
        LfElement functionElement = factor.getFunctionElement();
        SensitivityFunctionType functionType = factor.getFunctionType();
        if (branch == functionElement) {
            // self-sensitivity: the monitored flow/current is on the very branch being perturbed
            return computeBranchFunctionDirectPartial(branch, functionType, varType);
        }
        if (functionType == SensitivityFunctionType.BUS_REACTIVE_POWER && functionElement instanceof LfBus bus) {
            // Bus reactive injection has a direct term only when the perturbed branch is incident to that bus.
            // The injection is the opposite of the reactive power leaving the bus into the branch, hence the minus.
            double[] partials = SingleVariableFactorGroup.computeBranchFlowParameterPartials(branch, varType);
            if (partials.length == 0) {
                return 0;
            }
            if (branch.getBus1() == bus) {
                return -partials[1]; // -dq1/du
            } else if (branch.getBus2() == bus) {
                return -partials[3]; // -dq2/du
            }
        }
        return 0;
    }

    /**
     * Direct partial ∂F/∂u of a branch function F (active/reactive flow or current magnitude) w.r.t. one of the
     * branch's own parameters u (R / X / Y), at the converged operating point. Built from the four branch flow
     * partials [∂p1/∂u, ∂q1/∂u, ∂p2/∂u, ∂q2/∂u] (shared with the indirect RHS term).
     * <p>
     * The side the quantity is taken on is selected consistently with
     * {@code AbstractLfSensitivityFactor#getFunctionEquationTerm}: function types {@code *_1} and {@code *_3} read
     * the branch's side-1 quantities, {@code *_2} reads its side-2 quantities. Branch parameter (R / X / Y)
     * self-sensitivity is only defined on two-winding branches and lines (a three-winding leg is not addressable as
     * an R / X / Y variable), so {@code branch} is never a leg here and side 3 collapses to side 1 just like side 1.
     */
    private static double computeBranchFunctionDirectPartial(LfBranch branch, SensitivityFunctionType functionType, SensitivityVariableType varType) {
        double[] partials = SingleVariableFactorGroup.computeBranchFlowParameterPartials(branch, varType);
        if (partials.length == 0) {
            return 0;
        }
        boolean side2 = functionType.getSide().orElse(0) == 2;
        double dpdu = side2 ? partials[2] : partials[0]; // ∂p/∂u on the monitored side
        double dqdu = side2 ? partials[3] : partials[1]; // ∂q/∂u on the monitored side
        switch (functionType) {
            case BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2, BRANCH_ACTIVE_POWER_3:
                return dpdu;
            case BRANCH_REACTIVE_POWER_1, BRANCH_REACTIVE_POWER_2, BRANCH_REACTIVE_POWER_3:
                return dqdu;
            case BRANCH_CURRENT_1, BRANCH_CURRENT_2, BRANCH_CURRENT_3: {
                // Current magnitude is recovered from the apparent power: |S| = v·|I|, hence |I| = |S| / v with
                // |S| = sqrt(p² + q²). For the direct partial the state (V, θ) is held fixed, so v is constant:
                //   ∂|I|/∂u = (1/v)·∂|S|/∂u = (1/v)·(p·∂p/∂u + q·∂q/∂u) / |S| = (p·∂p/∂u + q·∂q/∂u) / (v·|S|).
                double v = (side2 ? branch.getBus2() : branch.getBus1()).getV();
                double p = (side2 ? branch.getP2() : branch.getP1()).eval();
                double q = (side2 ? branch.getQ2() : branch.getQ1()).eval();
                double s = Math.sqrt(p * p + q * q);
                // Below the zero-current threshold |I| = |S| / v is a 0/0 limit and (p·∂p/∂u + q·∂q/∂u) / (v·|S|)
                // is numerically unstable; the direct term vanishes there (same convention as the equation terms).
                return s / v < AbstractClosedBranchAcFlowEquationTerm.ZERO_CURRENT_THRESHOLD ? 0 : (p * dpdu + q * dqdu) / (v * s);
            }
            default:
                // Unreachable: this method is only reached for a self-sensitivity whose function element is the
                // branch carrying the R/X/Y variable (see computeParameterDirectPartial), so functionType is always
                // one of the branch flow/current types above. Bus function types never get here.
                throw new IllegalStateException("Unexpected branch function type: " + functionType);
        }
    }

    private void setFunctionReferences(List<LfSensitivityFactor<AcVariableType, AcEquationType>> factors) {
        for (LfSensitivityFactor<AcVariableType, AcEquationType> factor : factors) {
            if (factor.getFunctionPredefinedResult() != null) {
                factor.setFunctionReference(factor.getFunctionPredefinedResult());
            } else {
                factor.setFunctionReference(factor.getFunctionEquationTerm().eval());
            }
        }
    }

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, LfContingency lfContingency,
                                                           LfNetwork lfNetwork, AcLoadFlowContext context, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           int contingencyIndex, SensitivityResultWriter resultWriter,
                                                           boolean hasTransformerBusTargetVoltage) {
        if (lfParameters.isDistributedSlack() && Math.abs(lfContingency.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(), lfParametersExt.isLoadPowerFactorConstant(), lfParametersExt.isUseActiveLimits());
            activePowerDistribution.run(lfNetwork.getSynchronousNetworks().getFirst(), lfContingency.getActivePowerLoss());
        }

        if (!runLoadFlow(context, false)) {
            // write contingency status
            resultWriter.writeStateStatus(contingencyIndex, -1, SensitivityAnalysisResult.Status.FAILURE);
            return;
        }

        // write contingency status
        resultWriter.writeStateStatus(contingencyIndex, -1, SensitivityAnalysisResult.Status.SUCCESS);

        // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
        // system obtained just before the transformer steps rounding.
        if (hasTransformerBusTargetVoltage) {
            for (LfBranch branch : lfNetwork.getBranches()) {
                branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
            }
            lfNetwork.fixTransformerVoltageControls();
        }

        if (factorGroups.hasMultiVariables() && (!lfContingency.getLostLoads().isEmpty() || !lfContingency.getLostGenerators().isEmpty())) {
            // FIXME. It does not work with a contingency that breaks connectivity and lose an isolate injection.
            Set<LfBus> affectedBuses = lfContingency.getLoadAndGeneratorBuses();
            rescaleGlsk(factorGroups, affectedBuses);
        }

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

        // solve system
        DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
        context.getJacobianMatrix().solveTransposed(factorsStates);
        setFunctionReferences(lfFactors);

        // calculate sensitivity values
        calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyIndex, resultWriter);
    }

    private static boolean runLoadFlow(AcLoadFlowContext context, boolean isRunningBaseSituation) {
        AcLoadFlowResult result = new AcloadFlowEngine(context)
                .run();
        if (result.isSuccess() || result.getSolverStatus() == AcSolverStatus.NO_CALCULATION) {
            return true;
        } else {
            if (isRunningBaseSituation) {
                if (result.getOuterLoopResult().status() != OuterLoopStatus.STABLE) {
                    throw new PowsyblException("Initial load flow of base situation ended with outer loop status " + result.getOuterLoopResult().statusText());
                } else {
                    throw new PowsyblException("Initial load flow of base situation ended with solver status " + result.getSolverStatus());
                }
            } else {
                LOGGER.warn("Load flow failed with result={}", result);
                return false;
            }
        }
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    @Override
    public void analyse(Network network, String workingVariantId, List<Contingency> contingencies, List<OperatorStrategy> operatorStrategies,
                        List<Action> actions, PropagatedContingencyCreationParameters creationParameters,
                        List<SensitivityVariableSet> variableSets, SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter, ReportNode sensiReportNode,
                        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt,
                        Executor executor) throws ExecutionException {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);
        Objects.requireNonNull(sensiReportNode);

        if (!operatorStrategies.isEmpty()) {
            throw new PowsyblException("AC sensitivity analysis does not support operator strategies");
        }

        network.getVariantManager().setWorkingVariant(workingVariantId);

        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);
        VariablesTargetVoltageInfo variablesTargetVoltageInfo = getVariableTargetVoltageInfo(factorReader, network);

        // create LF network (we only manage main connected component)
        if (variablesTargetVoltageInfo.hasTransformerTargetVoltage()) {
            // if we have at least one bus target voltage linked to a ratio tap changer, we activate the transformer
            // voltage control for the AC load flow engine.
            lfParameters.setTransformerVoltageControlOn(true);
        }
        SlackBusSelector slackBusSelector = makeSlackBusSelector(network, lfParameters, lfParametersExt);

        checkVariableSet(variableSets);
        checkContingencies(contingencies);
        checkLoadFlowParameters(lfParameters);

        if (sensitivityAnalysisParametersExt.getThreadCount() == 1) {
            LfTopoConfig topoConfig = new LfTopoConfig();
            List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);
            AcLoadFlowParameters acParameters = makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
            try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig, acParameters.getNetworkParameters(), sensiReportNode)) {

                analyzeContingencySet(network, lfNetworks, propagatedContingencies, acParameters, lfParameters, lfParametersExt, variableSets, factorReader,
                        topoConfig.isBreaker(), resultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
            }
        } else {
            try (SequentialSensitivityResultWriter sequentialSensitivityResultWriter = new SequentialSensitivityResultWriter(resultWriter)) {
                BufferedFactorReader bufferedFactorReader = new BufferedFactorReader(factorReader);
                var contingenciesPartitions = Lists2.partition(contingencies, sensitivityAnalysisParametersExt.getThreadCount());
                ContingencyMultiThreadHelper.ParameterProvider<AcLoadFlowParameters> parameterProvider = topoConfig -> makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
                ContingencyMultiThreadHelper.ContingencyRunner<AcLoadFlowParameters> contingencyRunner = (partitionNum, lfNetworks, propagatedContingencies, acParameters) -> {
                    analyzeContingencySet(network, lfNetworks, propagatedContingencies, acParameters, lfParameters, lfParametersExt, variableSets, bufferedFactorReader,
                        acParameters.getNetworkParameters().isBreakers(), sequentialSensitivityResultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
                    sequentialSensitivityResultWriter.flush(); // flush the batch of data kept in this thread
                };
                ContingencyMultiThreadHelper.ReportMerger reportMerger = ContingencyMultiThreadHelper::mergeReportThreadResults;

                ContingencyMultiThreadHelper.createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions, creationParameters, new LfTopoConfig(),
                        parameterProvider, contingencyRunner, sensiReportNode, reportMerger, executor);
            }
        }
    }

    private LfNetworkParameters makeNetworkParameters(SlackBusSelector slackBusSelector, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        return new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(lfParametersExt.isVoltageRemoteControl())
                .setMinImpedance(true)
                .setTwtSplitShuntAdmittance(lfParameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(lfParametersExt.getPlausibleActivePowerLimit())
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS)
                .setCountriesToBalance(lfParameters.getCountriesToBalance())
                .setDistributedOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(lfParameters.isPhaseShifterRegulationOn())
                .setTransformerVoltageControl(lfParameters.isTransformerVoltageControlOn())
                .setVoltagePerReactivePowerControl(lfParametersExt.isVoltagePerReactivePowerControl())
                .setGeneratorReactivePowerRemoteControl(lfParametersExt.isGeneratorReactivePowerRemoteControl())
                .setTransformerReactivePowerControl(lfParametersExt.isTransformerReactivePowerControl())
                .setLoadFlowModel(lfParameters.isDc() ? LoadFlowModel.DC : LoadFlowModel.AC)
                .setShuntVoltageControl(lfParameters.isShuntCompensatorVoltageControlOn())
                .setReactiveLimits(lfParameters.isUseReactiveLimits())
                .setHvdcAcEmulation(lfParameters.isHvdcAcEmulation())
                .setMinPlausibleTargetVoltage(lfParametersExt.getMinPlausibleTargetVoltage())
                .setMaxPlausibleTargetVoltage(lfParametersExt.getMaxPlausibleTargetVoltage())
                .setMinNominalVoltageTargetVoltageCheck(lfParametersExt.getMinNominalVoltageTargetVoltageCheck())
                .setCacheEnabled(false) // force not caching as not supported in sensi analysis
                .setSimulateAutomationSystems(false)
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR) // not supported yet
                .setAreaInterchangeControlAreaType(lfParametersExt.getAreaInterchangeControlAreaType())
                .setForceTargetQInReactiveLimits(lfParametersExt.isForceTargetQInReactiveLimits())
                .setDisableInconsistentVoltageControls(lfParametersExt.isDisableInconsistentVoltageControls())
                .setExtrapolateReactiveLimits(lfParametersExt.isExtrapolateReactiveLimits())
                .setGeneratorsWithZeroMwTargetAreNotStarted(lfParametersExt.isGeneratorsWithZeroMwTargetAreNotStarted())
                .setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NETWORK_LOADING))
                .setAllowNonLinearShuntZeroSection(lfParametersExt.isAllowNonLinearShuntZeroSection());
    }

    private SlackBusSelector makeSlackBusSelector(Network network, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(),
                lfParametersExt.getSlackBusesIds(),
                lfParametersExt.getPlausibleActivePowerLimit(),
                lfParametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(),
                lfParametersExt.getSlackBusCountryFilter());
        if (lfParameters.isReadSlackBus()) {
            slackBusSelector = new NetworkSlackBusSelector(network, lfParametersExt.getSlackBusCountryFilter(), slackBusSelector);
        }
        return slackBusSelector;
    }

    private AcLoadFlowParameters makeAcLoadFlowParameters(Network network, SlackBusSelector slackBusSelector, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean breakers) {
        // create AC engine
        AcLoadFlowParameters acParameters = OpenLoadFlowParameters.createAcParameters(network, lfParameters, lfParametersExt, matrixFactory, connectivityFactory, breakers, true);
        acParameters.setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NEWTON_RAPHSON_SENSITIVITY_ANALYSIS));
        acParameters.setNetworkParameters(makeNetworkParameters(slackBusSelector, lfParameters, lfParametersExt, breakers));
        return acParameters;
    }

    private void analyzeContingencySet(Network network, LfNetworkList lfNetworks, List<PropagatedContingency> contingencies, AcLoadFlowParameters acParameters,
                                       LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, List<SensitivityVariableSet> variableSets,
                                       SensitivityFactorReader factorReader, boolean breakers, SensitivityResultWriter resultWriter,
                                       VariablesTargetVoltageInfo variablesTargetVoltageInfo, OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt) {

        if (breakers && variablesTargetVoltageInfo.hasBusTargetVoltage()) {
            // FIXME
            // a bus voltage function works only on a bus/branch topology and a switch contingency only works on a
            // bus/breaker topology. It is not compatible and must be fixed in the API.
            throw new PowsyblException("Switch contingency is not yet supported with sensitivity function of type BUS_VOLTAGE");
        }

        // create networks including all necessary switches
        LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow(() -> new PowsyblException("Empty network"));
        // As sensitivity analysis does not support AC-DC networks, the lfNetwork contains only one synchronous network.

        ReportNode networkReportNode = lfNetwork.getReportNode();

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors = allFactorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors and {} contingencies", allLfFactors.size(), contingencies.size());

        // next we only work with valid and valid only for function factors
        var validFactorHolder = writeInvalidFactors(allFactorHolder, resultWriter, contingencies, new HashMap<>(), parameters);
        var validLfFactors = validFactorHolder.getAllFactors();

        try (AcLoadFlowContext context = new AcLoadFlowContext(lfNetwork, acParameters)) {

            runLoadFlow(context, true);

            acParameters.setVoltageInitReport(false);

            // index factors by variable group to compute a minimal number of states
            SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups = createFactorGroups(validLfFactors.stream()
                    .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution

            Map<LfBus, Double> slackParticipationByBus;
            if (lfParameters.isDistributedSlack()) {
                List<ParticipatingElement> participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
                slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                        ParticipatingElement::getLfBus,
                        element -> -element.getFactor(),
                        Double::sum
                ));
            } else {
                slackParticipationByBus = Collections.singletonMap(lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);

            }

            // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
            // system obtained just before the transformer steps rounding.
            if (variablesTargetVoltageInfo.hasTransformerTargetVoltage()) {
                // switch on regulating transformers
                for (LfBranch branch : lfNetwork.getBranches()) {
                    branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
                }
                lfNetwork.fixTransformerVoltageControls();
            }

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

            // initialize right hand side from valid factors
            DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, slackParticipationByBus); // this is the rhs for the moment

            // solve system
            context.getJacobianMatrix().solveTransposed(factorsStates);

            // calculate sensitivity values
            setFunctionReferences(validLfFactors);
            calculateSensitivityValues(validFactorHolder.getFactorsForBaseNetwork(), factorGroups, factorsStates, -1, resultWriter);

            NetworkState networkState = NetworkState.save(lfNetwork);

            // we always restart from base case voltages for contingency simulation
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());

            OpenLoadFlowParameters contingencylfParametersExt = applyGenericContingencyParameters(context, lfParameters, lfParametersExt,
                    sensitivityAnalysisParametersExt.isStartWithFrozenACEmulation());

            contingencies.forEach(contingency -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PowsyblException("Computation was interrupted");
                }
                LOGGER.info("Simulate contingency '{}'", contingency.getContingency().getId());
                contingency.toLfContingency(lfNetwork)
                        .ifPresentOrElse(lfContingency -> {

                            ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfContingency.getId());
                            lfNetwork.setReportNode(postContSimReportNode);

                            List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = validFactorHolder.getFactorsForContingency(lfContingency.getId());
                            contingencyFactors.forEach(lfFactor -> {
                                lfFactor.setSensitivityValuePredefinedResult(null);
                                lfFactor.setFunctionPredefinedResult(null);
                            });

                            lfContingency.apply(lfParameters.getBalanceType());

                            setPredefinedResults(contingencyFactors, lfContingency.getDisabledNetwork(), contingency);

                            Map<LfBus, Double> postContingencySlackParticipationByBus;
                            Set<LfBus> slackConnectedComponent;
                            boolean hasChanged = false;
                            if (lfContingency.getDisabledNetwork().getBuses().isEmpty()) {
                                // contingency not breaking connectivity
                                LOGGER.debug("Contingency '{}' without loss of connectivity", lfContingency.getId());
                                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
                            } else {
                                // contingency breaking connectivity
                                LOGGER.debug("Contingency '{}' with loss of connectivity", lfContingency.getId());
                                // we check if factors are still in the main component
                                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses()).stream().filter(Predicate.not(lfContingency.getDisabledNetwork().getBuses()::contains)).collect(Collectors.toSet());
                                // we recompute GLSK weights if needed
                                hasChanged = rescaleGlsk(factorGroups, lfContingency.getDisabledNetwork().getBuses());
                            }

                            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                            // buses that contain elements participating to slack distribution)
                            if (lfParameters.isDistributedSlack()) {
                                postContingencySlackParticipationByBus = getParticipatingElements(slackConnectedComponent, lfParameters.getBalanceType(), contingencylfParametersExt).stream().collect(Collectors.toMap(
                                        ParticipatingElement::getLfBus, element -> -element.getFactor(), Double::sum));
                            } else {
                                postContingencySlackParticipationByBus = Collections.singletonMap(lfNetwork.getSynchronousNetworks().getFirst().getSlackBuses().getFirst(), -1d);
                            }
                            calculatePostContingencySensitivityValues(contingencyFactors, lfContingency, lfNetwork, context, factorGroups, postContingencySlackParticipationByBus,
                                    lfParameters, contingencylfParametersExt, lfContingency.getIndex(), resultWriter, variablesTargetVoltageInfo.hasTransformerTargetVoltage());

                            if (hasChanged) {
                                rescaleGlsk(factorGroups, Collections.emptySet());
                            }
                            networkState.restore();
                        }, () -> {
                            // it means that the contingency has no impact.
                            // we need to force the state vector to be re-initialized from base case network state
                            AcSolverUtil.initStateVector(lfNetwork, context.getEquationSystem(), context.getParameters().getVoltageInitializer());

                            calculateSensitivityValues(validFactorHolder.getFactorsForContingency(contingency.getContingency().getId()), factorGroups, factorsStates, contingency.getIndex(), resultWriter);
                            // write contingency status
                            resultWriter.writeStateStatus(contingency.getIndex(), -1, SensitivityAnalysisResult.Status.NO_IMPACT);
                        });
            });
        }

    }

    private OpenLoadFlowParameters applyGenericContingencyParameters(AcLoadFlowContext context, LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, boolean startWithFrozenACEmulation) {
        OpenLoadFlowParameters contingencylfParametersExt = lfParametersExt;
        if (startWithFrozenACEmulation) {
            contingencylfParametersExt = OpenLoadFlowParameters.clone(lfParametersExt);
            contingencylfParametersExt.setStartWithFrozenACEmulation(true);
            context.getParameters().setOuterLoops(OpenLoadFlowParameters.createAcOuterLoops(lfParameters, contingencylfParametersExt));
        }
        context.getParameters().setFixVoltageTargets(false); // Checking voltage targets in contingency cases is unnecessary in most cases
        return contingencylfParametersExt;
    }
}
