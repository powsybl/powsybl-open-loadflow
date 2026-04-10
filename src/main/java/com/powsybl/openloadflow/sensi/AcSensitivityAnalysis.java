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
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
import com.powsybl.openloadflow.ac.solver.AcSolverUtil;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.sensi.mt.BufferedFactorReader;
import com.powsybl.openloadflow.sensi.mt.SequentialSensitivityResultWriter;
import com.powsybl.openloadflow.util.Indexed;
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
                                            int contingencyIndex, int operatorStrategyIndex, SensitivityResultWriter resultWriter) {
        Set<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactorsSet = new HashSet<>(lfFactors);

        // VALID_ONLY_FOR_FUNCTION status is for factors where variable element is not in the main connected component but reference element is.
        // Therefore, the sensitivity is known to value 0 and the reference value can be computed.
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    if (!filterSensitivityValue(0, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                        resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, operatorStrategyIndex, 0, unscaleFunction(factor, factor.getFunctionReference()));
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
                }
                if (factor.getFunctionPredefinedResult() != null) {
                    ref = factor.getFunctionPredefinedResult();
                } else {
                    ref = factor.getFunctionReference();
                }
                double unscaledSensi = unscaleSensitivity(factor, sensi);
                if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                    resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, operatorStrategyIndex, unscaledSensi, unscaleFunction(factor, ref));
                }
            }
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

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, LfNetworkChange lfNetworkChange,
                                                           LfNetwork lfNetwork, AcLoadFlowContext context, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           SensitivityResultWriter resultWriter,
                                                           boolean hasTransformerBusTargetVoltage) {
        if (lfParameters.isDistributedSlack() && Math.abs(lfNetworkChange.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(), lfParametersExt.isLoadPowerFactorConstant(), lfParametersExt.isUseActiveLimits());
            activePowerDistribution.run(lfNetwork, lfNetworkChange.getActivePowerLoss());
        }

        int contingencyIndex = lfNetworkChange.getContingencyIndex();
        int operatorStrategyIndex = lfNetworkChange.getOperatorStrategyIndex();

        if (!runLoadFlow(context, false)) {
            // write contingency status
            resultWriter.writeStateStatus(contingencyIndex, operatorStrategyIndex, SensitivityAnalysisResult.Status.FAILURE);
            return;
        }

        // write contingency status
        resultWriter.writeStateStatus(contingencyIndex, operatorStrategyIndex, SensitivityAnalysisResult.Status.SUCCESS);

        // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
        // system obtained just before the transformer steps rounding.
        if (hasTransformerBusTargetVoltage) {
            for (LfBranch branch : lfNetwork.getBranches()) {
                branch.getVoltageControl().ifPresent(vc -> branch.setVoltageControlEnabled(true));
            }
            lfNetwork.fixTransformerVoltageControls();
        }

        if (factorGroups.hasMultiVariables() && (!lfNetworkChange.getLostLoads().isEmpty() || !lfNetworkChange.getLostGenerators().isEmpty())) {
            // FIXME. It does not work with a contingency that breaks connectivity and lose an isolate injection.
            Set<LfBus> affectedBuses = lfNetworkChange.getLoadAndGeneratorBuses();
            rescaleGlsk(factorGroups, affectedBuses);
        }

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one

        // solve system
        DenseMatrix factorsStates = initFactorsRhs(context.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
        context.getJacobianMatrix().solveTransposed(factorsStates);
        setFunctionReferences(lfFactors);

        // calculate sensitivity values
        calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyIndex, operatorStrategyIndex, resultWriter);
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
    public void analyse(Network network, String workingVariantId, List<Contingency> contingencies, List<OperatorStrategy> configuredOperatorStrategies,
                        List<Action> configuredActions, PropagatedContingencyCreationParameters creationParameters,
                        List<SensitivityVariableSet> variableSets, SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter, ReportNode sensiReportNode,
                        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt,
                        Executor executor) throws ExecutionException {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);
        Objects.requireNonNull(sensiReportNode);
        Objects.requireNonNull(sensitivityAnalysisParametersExt);
        Objects.requireNonNull(executor);

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

        List<OperatorStrategy> operatorStrategies;
        List<Action> actions;
        if (parameters.getOperatorStrategiesCalculationMode() == SensitivityOperatorStrategiesCalculationMode.NONE) {
            operatorStrategies = Collections.emptyList();
            actions = Collections.emptyList();
        } else {
            operatorStrategies = configuredOperatorStrategies;
            actions = configuredActions;
        }

        LfTopoConfig topoConfig = new LfTopoConfig();

        // update topo config with supported actions
        Actions.addAllSwitchesToOperate(topoConfig, network, actions);
        Actions.addAllBranchesToClose(topoConfig, network, actions);
        Actions.addAllPtcToOperate(topoConfig, actions);

        if (sensitivityAnalysisParametersExt.getThreadCount() == 1) {
            List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);
            AcLoadFlowParameters acParameters = makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, topoConfig.isBreaker());
            try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, topoConfig, acParameters.getNetworkParameters(), sensiReportNode)) {

                analyzeContingencySet(network, lfNetworks, propagatedContingencies, operatorStrategies, actions, acParameters, lfParameters, lfParametersExt, variableSets, factorReader,
                        topoConfig.isBreaker(), resultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
            }
        } else {
            try (SequentialSensitivityResultWriter sequentialSensitivityResultWriter = new SequentialSensitivityResultWriter(resultWriter)) {
                BufferedFactorReader bufferedFactorReader = new BufferedFactorReader(factorReader);
                var contingenciesPartitions = Lists2.partition(contingencies, sensitivityAnalysisParametersExt.getThreadCount());
                ContingencyMultiThreadHelper.ParameterProvider<AcLoadFlowParameters> parameterProvider = tc -> makeAcLoadFlowParameters(network, slackBusSelector, lfParameters, lfParametersExt, tc.isBreaker());
                ContingencyMultiThreadHelper.ContingencyRunner<AcLoadFlowParameters> contingencyRunner = (partitionNum, lfNetworks, propagatedContingencies, acParameters) -> {
                    analyzeContingencySet(network, lfNetworks, propagatedContingencies, operatorStrategies, actions, acParameters, lfParameters, lfParametersExt, variableSets, bufferedFactorReader,
                        acParameters.getNetworkParameters().isBreakers(), sequentialSensitivityResultWriter, variablesTargetVoltageInfo, sensitivityAnalysisParametersExt);
                    sequentialSensitivityResultWriter.flush(); // flush the batch of data kept in this thread
                };
                ContingencyMultiThreadHelper.ReportMerger reportMerger = ContingencyMultiThreadHelper::mergeReportThreadResults;

                ContingencyMultiThreadHelper.createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions, creationParameters, topoConfig,
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
                .setDetailedReport(lfParametersExt.getReportedFeatures().contains(OpenLoadFlowParameters.ReportedFeatures.NETWORK_LOADING));
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

    private void analyzeContingencySet(Network network, LfNetworkList lfNetworks, List<PropagatedContingency> propagatedContingencies,
                                       List<OperatorStrategy> operatorStrategies, List<Action> actions, AcLoadFlowParameters acParameters,
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

        ReportNode networkReportNode = lfNetwork.getReportNode();

        Map<String, Action> actionsById = Actions.indexById(actions);
        Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId =
                OperatorStrategies.indexByContingencyId(propagatedContingencies, operatorStrategies, actionsById, true);
        Set<Action> neededActions = OperatorStrategies.getNeededActions(operatorStrategiesByContingencyId, actionsById);
        Map<String, LfAction> lfActionById = LfActionUtils.createLfActions(lfNetwork, neededActions, network); // only convert needed actions

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<AcVariableType, AcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> allLfFactors = allFactorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors, {} contingencies and {} operator strategies",
                allLfFactors.size(), propagatedContingencies.size(), operatorStrategies.size());

        // next we only work with valid and valid only for function factors
        var validFactorHolder = writeInvalidFactors(allFactorHolder, resultWriter, propagatedContingencies, new HashMap<>(), parameters);
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
                slackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus(), -1d);

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
            calculateSensitivityValues(validFactorHolder.getFactorsForBaseNetwork(), factorGroups, factorsStates, -1, -1, resultWriter);

            NetworkState networkState = NetworkState.save(lfNetwork);

            // we always restart from base case voltages for contingency simulation
            context.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());

            OpenLoadFlowParameters contingencylfParametersExt = applyGenericContingencyParameters(context, lfParameters, lfParametersExt,
                    sensitivityAnalysisParametersExt.isStartWithFrozenACEmulation());

            propagatedContingencies.forEach(propagatedContingency -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new PowsyblException("Computation was interrupted");
                }
                LOGGER.info("Simulate contingency '{}'", propagatedContingency.getContingency().getId());
                LfContingency lfContingency = propagatedContingency.toLfContingency(lfNetwork).orElse(null);
                LfNetworkChange lfNetworkChange = new LfNetworkChange(lfNetwork, propagatedContingency, lfContingency, null);
                processNetworkChange(lfParameters, resultWriter, variablesTargetVoltageInfo, propagatedContingency, lfNetworkChange, networkReportNode,
                        lfNetwork, validFactorHolder, factorGroups, contingencylfParametersExt, context, networkState, factorsStates);

                if (parameters.getOperatorStrategiesCalculationMode() != SensitivityOperatorStrategiesCalculationMode.NONE) {
                    List<Indexed<OperatorStrategy>> operatorStrategiesForThisContingency = operatorStrategiesByContingencyId
                            .getOrDefault(propagatedContingency.getContingency().getId(), Collections.emptyList());

                    for (Indexed<OperatorStrategy> operatorStrategy : operatorStrategiesForThisContingency) {
                        LfOperatorStrategy lfOperatorStrategy = LfOperatorStrategy.create(operatorStrategy, lfActionById);
                        lfNetworkChange = new LfNetworkChange(lfNetwork, propagatedContingency, lfContingency, lfOperatorStrategy);
                        processNetworkChange(lfParameters, resultWriter, variablesTargetVoltageInfo, propagatedContingency, lfNetworkChange, networkReportNode,
                                lfNetwork, validFactorHolder, factorGroups, contingencylfParametersExt, context, networkState, factorsStates);
                    }
                }
            });
        }
    }

    private void processNetworkChange(LoadFlowParameters lfParameters, SensitivityResultWriter resultWriter, VariablesTargetVoltageInfo variablesTargetVoltageInfo,
                                      PropagatedContingency propagatedContingency, LfNetworkChange lfNetworkChange, ReportNode networkReportNode, LfNetwork lfNetwork,
                                      SensitivityFactorHolder<AcVariableType, AcEquationType> validFactorHolder, SensitivityFactorGroupList<AcVariableType, AcEquationType> factorGroups,
                                      OpenLoadFlowParameters contingencylfParametersExt, AcLoadFlowContext context, NetworkState networkState, DenseMatrix factorsStates) {
        if (lfNetworkChange.hasImpact()) {
            ReportNode postContSimReportNode = Reports.createPostContingencySimulation(networkReportNode, lfNetworkChange.getContingencyId());
            lfNetwork.setReportNode(postContSimReportNode);

            List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = validFactorHolder.getFactorsForContingency(lfNetworkChange.getContingencyId());
            contingencyFactors.forEach(lfFactor -> {
                lfFactor.setSensitivityValuePredefinedResult(null);
                lfFactor.setFunctionPredefinedResult(null);
            });

            lfNetworkChange.apply(lfParameters.getBalanceType());

            setPredefinedResults(contingencyFactors, lfNetworkChange.getDisabledNetwork(), propagatedContingency);

            Map<LfBus, Double> postContingencySlackParticipationByBus;
            Set<LfBus> slackConnectedComponent;
            boolean hasChanged = false;
            if (lfNetworkChange.getDisabledNetwork().getBuses().isEmpty()) {
                // contingency not breaking connectivity
                LOGGER.debug("Contingency '{}' without loss of connectivity", lfNetworkChange.getContingencyId());
                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
            } else {
                // contingency breaking connectivity
                LOGGER.debug("Contingency '{}' with loss of connectivity", lfNetworkChange.getContingencyId());
                // we check if factors are still in the main component
                slackConnectedComponent = new HashSet<>(lfNetwork.getBuses()).stream().filter(Predicate.not(lfNetworkChange.getDisabledNetwork().getBuses()::contains)).collect(Collectors.toSet());
                // we recompute GLSK weights if needed
                hasChanged = rescaleGlsk(factorGroups, lfNetworkChange.getDisabledNetwork().getBuses());
            }

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution)
            if (lfParameters.isDistributedSlack()) {
                postContingencySlackParticipationByBus = getParticipatingElements(slackConnectedComponent, lfParameters.getBalanceType(), contingencylfParametersExt).stream().collect(Collectors.toMap(
                        ParticipatingElement::getLfBus, element -> -element.getFactor(), Double::sum));
            } else {
                postContingencySlackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus(), -1d);
            }
            calculatePostContingencySensitivityValues(contingencyFactors, lfNetworkChange, lfNetwork, context, factorGroups, postContingencySlackParticipationByBus,
                    lfParameters, contingencylfParametersExt, resultWriter, variablesTargetVoltageInfo.hasTransformerTargetVoltage());

            if (hasChanged) {
                rescaleGlsk(factorGroups, Collections.emptySet());
            }
            networkState.restore();
        } else {
            // it means that the contingency has no impact.
            // we need to force the state vector to be re-initialized from base case network state
            AcSolverUtil.initStateVector(lfNetwork, context.getEquationSystem(), context.getParameters().getVoltageInitializer());

            calculateSensitivityValues(validFactorHolder.getFactorsForContingency(lfNetworkChange.getContingencyId()),
                    factorGroups, factorsStates, lfNetworkChange.getContingencyIndex(), lfNetworkChange.getOperatorStrategyIndex(), resultWriter);
            // write contingency status
            resultWriter.writeStateStatus(lfNetworkChange.getContingencyIndex(), lfNetworkChange.getOperatorStrategyIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
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
