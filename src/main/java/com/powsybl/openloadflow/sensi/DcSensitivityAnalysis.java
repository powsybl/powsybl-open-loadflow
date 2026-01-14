/*
 * Copyright (c) 2020-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.google.common.base.Stopwatch;
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
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.dc.fastdc.ComputedContingencyElement;
import com.powsybl.openloadflow.dc.fastdc.ComputedElement;
import com.powsybl.openloadflow.dc.fastdc.ConnectivityBreakAnalysis;
import com.powsybl.openloadflow.dc.fastdc.WoodburyEngine;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.action.*;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Derivable;
import com.powsybl.openloadflow.util.Indexed;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.openloadflow.network.impl.PropagatedContingency.cleanContingencies;
import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis<DcVariableType, DcEquationType> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DcSensitivityAnalysis.class);

    private static final double FUNCTION_REFERENCE_ZER0_THRESHOLD = 1e-13;

    public DcSensitivityAnalysis(MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory, SensitivityAnalysisParameters parameters) {
        super(matrixFactory, connectivityFactory, parameters);
    }

    private static DcLoadFlowParameters createDcLoadFlowParameters(LfNetworkParameters networkParameters, MatrixFactory matrixFactory,
                                                                   LoadFlowParameters lfParameters, OpenLoadFlowParameters parametersExt) {
        var equationSystemCreationParameters = new DcEquationSystemCreationParameters()
                .setUpdateFlows(true)
                .setForcePhaseControlOffAndAddAngle1Var(true)
                .setUseTransformerRatio(lfParameters.isDcUseTransformerRatio())
                .setDcApproximationType(parametersExt.getDcApproximationType());

        if (parametersExt.getSlackDistributionFailureBehavior() != OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS) {
            LOGGER.warn("Slack distribution failure mode {} ignored. Using LEAVE_ON_SLACK_BUS for DC sensitivity analysis", parametersExt.getSlackDistributionFailureBehavior());
        }

        return new DcLoadFlowParameters()
                .setNetworkParameters(networkParameters)
                .setEquationSystemCreationParameters(equationSystemCreationParameters)
                .setMatrixFactory(matrixFactory)
                .setDistributedSlack(lfParameters.isDistributedSlack())
                // Currently the DC sensitivity analysis does not check slack distribution success or failure and runs always in
                // mode LEAVE_ON_SLACK_BUS
                .setSlackDistributionFailureBehavior(OpenLoadFlowParameters.SlackDistributionFailureBehavior.LEAVE_ON_SLACK_BUS)
                .setBalanceType(lfParameters.getBalanceType())
                .setSetVToNan(true)
                .setMaxOuterLoopIterations(parametersExt.getMaxOuterLoopIterations());
    }

    /**
     * Calculate the active power flows for pre-contingency or a post-contingency state.
     * The interesting disabled branches are only phase shifters.
     */
    private DenseMatrix calculateFlowStates(DcLoadFlowContext loadFlowContext, List<ParticipatingElement> participatingElements,
                                            DisabledNetwork disabledNetwork, List<LfAction> actions, ReportNode reportNode) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] dx = WoodburyEngine.runDcLoadFlowWithModifiedTargetVector(loadFlowContext, disabledNetwork, actions, reportNode);

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return new DenseMatrix(dx.length, 1, dx);
    }

    /**
     * Calculate flow and sensitivity values from pre-contingency states or post-contingency states.
     * Write the flow and sensitivity values for a LfSensitivityFactor in the SensitivityResultWriter.
     */
    private void createBranchSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup,
                                              DenseMatrix flowStates, DenseMatrix factorStates, PropagatedContingency contingency, LfOperatorStrategy operatorStrategy,
                                              SensitivityResultWriter resultWriter, DisabledNetwork disabledNetwork) {
        Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, disabledNetwork, contingency);
        Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
        Optional<Double> functionPredefinedResults = predefinedResults.getRight();
        double sensitivityValue = sensitivityValuePredefinedResult.orElse(0d);
        double functionValue = functionPredefinedResults.orElse(0d);
        Derivable<DcVariableType> p1 = factor.getFunctionEquationTerm();

        if (functionPredefinedResults.isEmpty()) {
            functionValue = p1.calculateSensi(flowStates, 0);
        }

        if (sensitivityValuePredefinedResult.isEmpty()) {
            sensitivityValue = p1.calculateSensi(factorStates, factorGroup.getIndex());
        }

        functionValue = fixZeroFunctionReference(contingency, functionValue);

        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
            resultWriter.writeSensitivityValue(factor.getIndex(), contingency != null ? contingency.getIndex() : -1, operatorStrategy != null ? operatorStrategy.getIndex() : -1,
                    unscaledSensi, unscaleFunction(factor, functionValue));
        }
    }

    /**
     * Post contingency reference flow, that should be strictly zero, for numeric reason and because it is computed
     * from shifting pre-contingency non-zero flow, cannot end up to a strict zero: very small values are converted to zero.
     */
    private static double fixZeroFunctionReference(PropagatedContingency contingency, double functionValue) {
        if (contingency != null) {
            return Math.abs(functionValue) < FUNCTION_REFERENCE_ZER0_THRESHOLD ? 0 : functionValue;
        }
        return functionValue;
    }

    /**
     * Compute state for sensitivity factors taking into account slack distribution.
     */
    private DenseMatrix calculateFactorStates(DcLoadFlowContext loadFlowContext,
                                              SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                              List<ParticipatingElement> participatingElements) {
        Map<LfBus, Double> slackParticipationByBus;
        if (participatingElements.isEmpty()) {
            slackParticipationByBus = Map.of(loadFlowContext.getNetwork().getSlackBus(), -1d);
        } else {
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                ParticipatingElement::getLfBus,
                element -> -element.getFactor(),
                Double::sum));
        }

        DenseMatrix factorStates = initFactorsRhs(loadFlowContext.getEquationSystem(), factorGroups, slackParticipationByBus);
        loadFlowContext.getJacobianMatrix().solveTransposed(factorStates); // states for the sensitivity factors
        return factorStates;
    }

    /**
     * Create branch flow and sensitivity values from a pre-contingency state or a post-contingency state.
     */
    private void calculateSensitivityValues(List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, DenseMatrix factorStates, DenseMatrix flowStates,
                                            PropagatedContingency contingency, LfOperatorStrategy operatorStrategy, SensitivityResultWriter resultWriter, DisabledNetwork disabledNetwork) {
        if (lfFactors.isEmpty()) {
            return;
        }

        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> createBranchSensitivityValue(factor, null, flowStates, factorStates, contingency, operatorStrategy, resultWriter, disabledNetwork));

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchSensitivityValue(factor, factorGroup, flowStates, factorStates, contingency, operatorStrategy, resultWriter, disabledNetwork);
            }
        }
    }

    /**
     * Calculate sensitivity values for a contingency.
     * In case of connectivity break, a pre-computation has been done in {@link #processContingencyAndOperatorStrategy}
     * to get a first version of the new participating elements, that can be overridden in this method, and to indicate
     * if the factorsStates should be overridden or not in this method.
     * If connectivity, a generator, a load or a phase tap changer is lost due to the contingency,
     * the flowStates are overridden.
     * The matrices factorStates and flowStates are modified by this method.
     */
    private void calculateSensitivityValuesForContingencyAndOperatorStrategy(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                                             SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, DenseMatrix factorStates, DenseMatrix contingenciesStates, DenseMatrix actionsStates,
                                                                             DenseMatrix flowStates, PropagatedContingency contingency, LfOperatorStrategy operatorStrategy, Map<String, ComputedContingencyElement> contingencyElementByBranch, Map<LfAction, ComputedElement> actionElementByLfAction,
                                                                             Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements, Set<String> elementsToReconnect,
                                                                             SensitivityResultWriter resultWriter, ReportNode reportNode, Set<LfBranch> partialDisabledBranches, boolean rhsChangedAfterConnectivityBreak) {
        List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors = validFactorHolder.getFactorsForContingency(contingency.getContingency().getId());
        List<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .collect(Collectors.toList());
        List<ComputedElement> actionElements = operatorStrategy != null ? operatorStrategy.getActions().stream()
                .map(actionElementByLfAction::get)
                .filter(actionElement -> !elementsToReconnect.contains(actionElement.getLfBranch().getId()))
                .toList() : Collections.emptyList();

        List<LfAction> actions = operatorStrategy != null ? operatorStrategy.getActions() : Collections.emptyList();

        var lfNetwork = loadFlowContext.getNetwork();
        Set<LfBranch> disabledBranches = findDisabledBranchIds(contingency, actions).stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
        disabledBranches.addAll(partialDisabledBranches);
        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
        DenseMatrix newFactorStates = factorStates;

        WoodburyEngine engine = new WoodburyEngine(loadFlowContext.getParameters().getEquationSystemCreationParameters(),
                                                   contingencyElements, contingenciesStates, actionElements, actionsStates);
        int operatorStrategyIndex = operatorStrategy != null ? operatorStrategy.getIndex() : -1;
        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLose().isEmpty()) {
            DenseMatrix newFlowStates = flowStates;
            // we need to recompute the factor states because the connectivity changed
            if (rhsChangedAfterConnectivityBreak) {
                newFactorStates = calculateFactorStates(loadFlowContext, factorGroups, participatingElements);
            }

            // get the lost phase tap changers for this contingency
            Set<LfBranch> lostPhaseControllers = contingency.getBranchIdsToOpen().keySet().stream()
                    .filter(element -> !elementsToReconnect.contains(element))
                    .map(contingencyElementByBranch::get)
                    .map(ComputedContingencyElement::getLfBranch)
                    .filter(LfBranch::hasPhaseControllerCapability)
                    .collect(Collectors.toSet());

            // if a phase tap changer is lost or if the connectivity have changed, we must recompute load flows
            if (!disabledBuses.isEmpty() || !lostPhaseControllers.isEmpty()) {
                newFlowStates = calculateFlowStates(loadFlowContext, participatingElements, disabledNetwork, actions, reportNode);
            }

            engine.toPostContingencyAndOperatorStrategyStates(newFlowStates);
            engine.toPostContingencyAndOperatorStrategyStates(newFactorStates);
            calculateSensitivityValues(factors, newFactorStates, newFlowStates, contingency, operatorStrategy, resultWriter, disabledNetwork);
            // write contingency status
            if (contingency.hasNoImpact()) {
                resultWriter.writeStateStatus(contingency.getIndex(), operatorStrategyIndex, SensitivityAnalysisResult.Status.NO_IMPACT);
            } else {
                resultWriter.writeStateStatus(contingency.getIndex(), operatorStrategyIndex, SensitivityAnalysisResult.Status.SUCCESS);
            }
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
            NetworkState networkState = NetworkState.save(lfNetwork);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            boolean participatingElementsChanged = false;
            boolean rhsChangedAfterGlskRescaling = false;
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                if (isDistributedSlackOnGenerators(lfParameters) && !contingency.getGeneratorIdsToLose().isEmpty()) {
                    // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
                    Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
                    newParticipatingElements = participatingElements.stream()
                            .filter(participatingElement -> !participatingGeneratorsToRemove.contains(participatingElement.getElement()))
                            .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                            .collect(Collectors.toList());
                    normalizeParticipationFactors(newParticipatingElements);
                    participatingElementsChanged = true;
                } else if (isDistributedSlackOnLoads(lfParameters) && !contingency.getLoadIdsToLose().isEmpty()) {
                    newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
                    participatingElementsChanged = true;
                }
                if (factorGroups.hasMultiVariables()) {
                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                    rhsChangedAfterGlskRescaling = rescaleGlsk(factorGroups, impactedBuses);
                }
                // write contingency status
                resultWriter.writeStateStatus(contingency.getIndex(), operatorStrategyIndex, SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                // write contingency status
                resultWriter.writeStateStatus(contingency.getIndex(), operatorStrategyIndex, SensitivityAnalysisResult.Status.NO_IMPACT);
            }

            // we need to recompute the factor states because the rhs or the participating elements have changed
            if (participatingElementsChanged || rhsChangedAfterGlskRescaling || rhsChangedAfterConnectivityBreak) {
                newFactorStates = calculateFactorStates(loadFlowContext, factorGroups, newParticipatingElements);
            }

            DenseMatrix newFlowStates = calculateFlowStates(loadFlowContext, newParticipatingElements, disabledNetwork, actions, reportNode);

            engine.toPostContingencyAndOperatorStrategyStates(newFlowStates);
            engine.toPostContingencyAndOperatorStrategyStates(newFactorStates);
            calculateSensitivityValues(factors, newFactorStates, newFlowStates, contingency, operatorStrategy, resultWriter, disabledNetwork);

            networkState.restore();
        }
    }

    private static Set<String> findDisabledBranchIds(PropagatedContingency contingency, List<LfAction> actions) {
        Set<String> disableBranchIds = new HashSet<>();
        disableBranchIds.addAll(contingency.getBranchIdsToOpen().keySet());
        for (LfAction action : actions) {
            if (action instanceof AbstractLfBranchAction<?> branchAction) {
                if (branchAction.getDisabledBranch() != null) {
                    disableBranchIds.add(branchAction.getDisabledBranch().getId());
                }
            } else {
                throw new PowsyblException("Unexpected action type: " + action.getClass().getSimpleName());
            }
        }
        for (LfAction action : actions) {
            if (action instanceof AbstractLfBranchAction<?> branchAction) {
                if (branchAction.getEnabledBranch() != null) {
                    disableBranchIds.remove(branchAction.getEnabledBranch().getId());
                }
            }
        }
        return disableBranchIds;
    }

    /**
     * Calculate sensitivity values for a contingency.
     * If the contingnecy is brealing connectivity, tt determines if the right hand side has been changed due to the
     * contingency, e.g. when the slack distribution is impacted by the disabled buses. If so, factorsStates will be
     * overridden in {@link #calculateSensitivityValuesForContingencyAndOperatorStrategy}.
     */
    private void processContingencyAndOperatorStrategy(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                       LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                       SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                       SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                       List<ParticipatingElement> participatingElements,
                                                       Map<String, ComputedContingencyElement> contingencyElementByBranch, Map<LfAction, ComputedElement> actionElementByLfAction,
                                                       DenseMatrix flowStates, DenseMatrix factorsStates, DenseMatrix contingenciesStates, DenseMatrix actionsStates,
                                                       SensitivityResultWriter resultWriter,
                                                       ReportNode reportNode) {
        if (connectivityAnalysisResult.getDisabledBuses().isEmpty()) {
            // there is no connectivity break
            calculateSensitivityValuesForContingencyAndOperatorStrategy(loadFlowContext, lfParametersExt, validFactorHolder, factorGroups,
                    factorsStates, contingenciesStates, actionsStates, flowStates, connectivityAnalysisResult.getPropagatedContingency(),
                    connectivityAnalysisResult.getOperatorStrategy(), contingencyElementByBranch, actionElementByLfAction, Collections.emptySet(),
                    participatingElements, Collections.emptySet(), resultWriter, reportNode, Collections.emptySet(), false);
        } else {
            // there is a connectivity break
            PropagatedContingency contingency = connectivityAnalysisResult.getPropagatedContingency();
            Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();
            Set<LfBranch> partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();

            // as we are processing a contingency with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
                if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                }
            }

            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
            boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
            if (lfParameters.isDistributedSlack()) {
                rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
            }
            if (factorGroups.hasMultiVariables()) {
                // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
                rhsChanged |= rescaleGlsk(factorGroups, disabledBuses);
            }

            // we need to recompute the participating elements because the connectivity changed
            if (rhsChanged) {
                participatingElementsForThisConnectivity = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                        : Collections.emptyList();
            }

            calculateSensitivityValuesForContingencyAndOperatorStrategy(loadFlowContext, lfParametersExt,
                    validFactorHolder, factorGroups, factorsStates, contingenciesStates, actionsStates, flowStates,
                    contingency, connectivityAnalysisResult.getOperatorStrategy(), contingencyElementByBranch, actionElementByLfAction, disabledBuses,
                    participatingElementsForThisConnectivity, connectivityAnalysisResult.getElementsToReconnect(), resultWriter, reportNode, partialDisabledBranches, rhsChanged);
        }
    }

    @Override
    public void analyse(Network network, String workingVariantId, List<Contingency> contingencies, List<OperatorStrategy> operatorStrategies,
                        List<Action> actions, PropagatedContingencyCreationParameters creationParameters,
                        List<SensitivityVariableSet> variableSets, SensitivityFactorReader factorReader,
                        SensitivityResultWriter resultWriter, ReportNode sensiReportNode,
                        OpenSensitivityAnalysisParameters sensitivityAnalysisParametersExt,
                        Executor executor) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(variableSets);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);

        network.getVariantManager().setWorkingVariant(workingVariantId);

        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);

        Stopwatch stopwatch = Stopwatch.createStarted();

        // create the network (we only manage main connected component)
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(),
                                                                      lfParametersExt.getSlackBusesIds(),
                                                                      lfParametersExt.getPlausibleActivePowerLimit(),
                                                                      lfParametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(),
                                                                      lfParametersExt.getSlackBusCountryFilter());
        if (lfParameters.isReadSlackBus()) {
            slackBusSelector = new NetworkSlackBusSelector(network, lfParametersExt.getSlackBusCountryFilter(), slackBusSelector);
        }

        LfTopoConfig topoConfig = new LfTopoConfig();
        List<PropagatedContingency> propagatedContingencies = PropagatedContingency.createList(network, contingencies, topoConfig, creationParameters);
        boolean breakers = topoConfig.isBreaker();

        // update topo config with supported actions
        Actions.addAllBranchesToClose(topoConfig, network, actions);

        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(false)
                .setMinImpedance(true)
                .setTwtSplitShuntAdmittance(lfParameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(lfParametersExt.getPlausibleActivePowerLimit())
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS)
                .setCountriesToBalance(lfParameters.getCountriesToBalance())
                .setDistributedOnConformLoad(lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(false)
                .setTransformerVoltageControl(false)
                .setVoltagePerReactivePowerControl(false)
                .setGeneratorReactivePowerRemoteControl(false)
                .setTransformerReactivePowerControl(false)
                .setLoadFlowModel(LoadFlowModel.DC)
                .setShuntVoltageControl(false)
                .setReactiveLimits(false)
                .setHvdcAcEmulation(false) // still not supported
                .setCacheEnabled(false) // force not caching as not supported in sensi analysis
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR); // not supported yet

        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, lfNetworkParameters, topoConfig, sensiReportNode)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow(() -> new PowsyblException("Empty network"));

            checkContingencies(contingencies);

            cleanContingencies(lfNetwork, propagatedContingencies);
            checkLoadFlowParameters(lfParameters);

            Map<String, Action> actionsById = Actions.indexById(actions);
            Map<String, List<Indexed<OperatorStrategy>>> operatorStrategiesByContingencyId =
                    OperatorStrategies.indexByContingencyId(propagatedContingencies, operatorStrategies, actionsById, true);
            Set<Action> neededActions = OperatorStrategies.getNeededActions(operatorStrategiesByContingencyId, actionsById);
            Map<String, LfAction> lfActionById = LfActionUtils.createLfActions(lfNetwork, neededActions, network, lfNetworkParameters); // only convert needed actions

            Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
            SensitivityFactorHolder<DcVariableType, DcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> allLfFactors = allFactorHolder.getAllFactors();

            allLfFactors.stream()
                    .filter(lfFactor -> lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                                && lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER_2
                                && lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER_3
                            || lfFactor.getVariableType() != SensitivityVariableType.INJECTION_ACTIVE_POWER
                                && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE
                                && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE_1
                                && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE_2
                                && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE_3
                                && lfFactor.getVariableType() != SensitivityVariableType.HVDC_LINE_ACTIVE_POWER)
                    .findFirst()
                    .ifPresent(ignored -> {
                        throw new PowsyblException("Only variables of type TRANSFORMER_PHASE, TRANSFORMER_PHASE_1, TRANSFORMER_PHASE_2, TRANSFORMER_PHASE_3, INJECTION_ACTIVE_POWER and HVDC_LINE_ACTIVE_POWER, and functions of type BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2 and BRANCH_ACTIVE_POWER_3 are yet supported in DC");
                    });

            LOGGER.info("Running DC sensitivity analysis with {} factors and {} contingencies", allLfFactors.size(), contingencies.size());

            var dcLoadFlowParameters = createDcLoadFlowParameters(lfNetworkParameters, matrixFactory, lfParameters, lfParametersExt);

            // next we only work with valid factors
            var validFactorHolder = writeInvalidFactors(allFactorHolder, resultWriter, propagatedContingencies);
            var validLfFactors = validFactorHolder.getAllFactors();
            LOGGER.info("{}/{} factors are valid", validLfFactors.size(), allLfFactors.size());

            try (DcLoadFlowContext loadFlowContext = new DcLoadFlowContext(lfNetwork, dcLoadFlowParameters, false)) {

                // create jacobian matrix either using calculated voltages from pre-contingency network or nominal voltages
                VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES
                        ? new PreviousValueVoltageInitializer()
                        : new UniformValueVoltageInitializer();

                DcLoadFlowEngine.initStateVector(lfNetwork, loadFlowContext.getEquationSystem(), voltageInitializer);

                // index factors by variable group to compute the minimal number of states
                SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups = createFactorGroups(validLfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution)
                List<ParticipatingElement> participatingElements = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt)
                        : Collections.emptyList();

                // run DC load on pre-contingency network
                DenseMatrix baseFlowStates = calculateFlowStates(loadFlowContext, participatingElements, new DisabledNetwork(), Collections.emptyList(), sensiReportNode);
                // create workingFlowStates matrix that will be a working copy of baseFlowStates
                DenseMatrix workingFlowStates = new DenseMatrix(baseFlowStates.getRowCount(), baseFlowStates.getColumnCount());

                // compute the pre-contingency factor states
                DenseMatrix baseFactorStates = calculateFactorStates(loadFlowContext, factorGroups, participatingElements);
                // create workingFactorStates matrix that will be a working copy of baseFactorStates
                DenseMatrix workingFactorStates = new DenseMatrix(baseFactorStates.getRowCount(), baseFactorStates.getColumnCount());

                // calculate sensitivity values for pre-contingency network
                calculateSensitivityValues(validFactorHolder.getFactorsForBaseNetwork(), baseFactorStates, baseFlowStates, null, null, resultWriter, new DisabledNetwork());

                // filter contingencies without factors
                List<PropagatedContingency> contingenciesWithFactors = new ArrayList<>();
                propagatedContingencies.forEach(contingency -> {
                    List<AbstractSensitivityAnalysis.LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors = validFactorHolder.getFactorsForContingencies(List.of(contingency.getContingency().getId()));
                    if (!lfFactors.isEmpty()) {
                        contingenciesWithFactors.add(contingency);
                    } else {
                        resultWriter.writeStateStatus(contingency.getIndex(), -1, SensitivityAnalysisResult.Status.SUCCESS);
                    }
                });

                // compute states with +1 -1 to model the contingencies and run connectivity analysis
                ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults = ConnectivityBreakAnalysis.run(loadFlowContext, contingenciesWithFactors);

                // the map is indexed by lf actions as different kind of actions can be given on the same branch
                Map<LfAction, ComputedElement> actionElementsIndexByLfAction = ComputedElement.createActionElementsIndexByLfAction(lfActionById, loadFlowContext.getEquationSystem());

                // compute states with +1 -1 to model the actions in Woodbury engine
                // note that the number of columns in the matrix depends on the number of distinct branches affected by the action elements
                DenseMatrix actionsStates = ComputedElement.calculateElementsStates(loadFlowContext, actionElementsIndexByLfAction.values());

                LOGGER.info("Processing contingencies with no connectivity break");

                // process contingencies with no connectivity break
                for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityBreakAnalysisResults.nonBreakingConnectivityAnalysisResults()) {
                    if (Thread.currentThread().isInterrupted()) {
                        stopwatch.stop();
                        throw new PowsyblException("Computation was interrupted");
                    }
                    workingFlowStates.copyValuesFrom(baseFlowStates);
                    workingFactorStates.copyValuesFrom(baseFactorStates);

                    processContingencyAndOperatorStrategy(connectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
                            validFactorHolder, factorGroups, participatingElements, connectivityBreakAnalysisResults.contingencyElementByBranch(), actionElementsIndexByLfAction,
                            workingFlowStates, workingFactorStates, connectivityBreakAnalysisResults.contingenciesStates(), actionsStates, resultWriter, sensiReportNode);
                }

                LOGGER.info("Processing contingencies with connectivity break");

                // process contingencies with connectivity break
                for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityBreakAnalysisResults.connectivityBreakingAnalysisResults()) {
                    if (Thread.currentThread().isInterrupted()) {
                        stopwatch.stop();
                        throw new PowsyblException("Computation was interrupted");
                    }
                    workingFlowStates.copyValuesFrom(baseFlowStates);
                    workingFactorStates.copyValuesFrom(baseFactorStates);

                    processContingencyAndOperatorStrategy(connectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
                            validFactorHolder, factorGroups, participatingElements, connectivityBreakAnalysisResults.contingencyElementByBranch(), actionElementsIndexByLfAction,
                            workingFlowStates, workingFactorStates, connectivityBreakAnalysisResults.contingenciesStates(), actionsStates, resultWriter, sensiReportNode);
                }

                LOGGER.info("Processing operator strategies");

                // process operator strategies
                for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : Stream.concat(connectivityBreakAnalysisResults.nonBreakingConnectivityAnalysisResults().stream(),
                                                                                                                     connectivityBreakAnalysisResults.connectivityBreakingAnalysisResults().stream()).toList()) {
                    for (Indexed<OperatorStrategy> operatorStrategy : operatorStrategiesByContingencyId.getOrDefault(connectivityAnalysisResult.getPropagatedContingency().getContingency().getId(), Collections.emptyList())) {
                        if (Thread.currentThread().isInterrupted()) {
                            stopwatch.stop();
                            throw new PowsyblException("Computation was interrupted");
                        }

                        List<String> operatorStrategyActionIds = operatorStrategy.value().getConditionalActions().stream().flatMap(conditionalActions -> conditionalActions.getActionIds().stream()).toList();
                        List<LfAction> operatorStrategyLfActions = operatorStrategyActionIds.stream().map(lfActionById::get).toList();
                        LfOperatorStrategy lfOperatorStrategy = new LfOperatorStrategy(operatorStrategy.value(), operatorStrategy.index(), operatorStrategyLfActions);
                        var postActionsConnectivityAnalysisResult = ConnectivityBreakAnalysis.processPostContingencyAndPostOperatorStrategyConnectivityAnalysisResult(loadFlowContext,
                                connectivityAnalysisResult,
                                connectivityBreakAnalysisResults.contingencyElementByBranch(),
                                connectivityBreakAnalysisResults.contingenciesStates(),
                                lfOperatorStrategy,
                                actionElementsIndexByLfAction,
                                actionsStates);

                        workingFlowStates.copyValuesFrom(baseFlowStates);
                        workingFactorStates.copyValuesFrom(baseFactorStates);

                        processContingencyAndOperatorStrategy(postActionsConnectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
                                validFactorHolder, factorGroups, participatingElements, connectivityBreakAnalysisResults.contingencyElementByBranch(), actionElementsIndexByLfAction,
                                workingFlowStates, workingFactorStates, connectivityBreakAnalysisResults.contingenciesStates(), actionsStates, resultWriter, sensiReportNode);
                    }
                }
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

}
