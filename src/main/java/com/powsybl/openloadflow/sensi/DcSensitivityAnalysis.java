/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.*;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.dc.equations.DcEquationType;
import com.powsybl.openloadflow.dc.equations.DcVariableType;
import com.powsybl.openloadflow.equations.Equation;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.util.Derivable;
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.dc.DcLoadFlowEngine.solveMultipleTargets;
import static com.powsybl.openloadflow.network.util.ParticipatingElement.normalizeParticipationFactors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Gaël Macherel {@literal <gael.macherel@artelys.com>}
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis<DcVariableType, DcEquationType> {
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

        return new DcLoadFlowParameters()
                .setNetworkParameters(networkParameters)
                .setEquationSystemCreationParameters(equationSystemCreationParameters)
                .setMatrixFactory(matrixFactory)
                .setDistributedSlack(lfParameters.isDistributedSlack())
                .setBalanceType(lfParameters.getBalanceType())
                .setSetVToNan(true)
                .setMaxOuterLoopIterations(parametersExt.getMaxOuterLoopIterations());
    }

    private void createBranchPreContingencySensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, SensitivityResultWriter resultWriter) {
        Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, new DisabledNetwork(), null);
        Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
        Optional<Double> functionPredefinedResults = predefinedResults.getRight();
        double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
        double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);
        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
            resultWriter.writeSensitivityValue(factor.getIndex(), -1, unscaledSensi, unscaleFunction(factor, functionValue));
        }
    }

    private void createBranchPostContingenciesSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup,
                                                               List<PropagatedContingency> contingencies, List<DenseMatrix> injectionResult, List<DenseMatrix> flowResult,
                                                               HashMap<PropagatedContingency, DisabledNetwork> disabledNetworksByPropagatedContingencies,
                                                               SensitivityResultWriter resultWriter) {
        Derivable<DcVariableType> p1 = factor.getFunctionEquationTerm();
        for (PropagatedContingency contingency : contingencies) {
            DenseMatrix postContingencyInjectionStates = injectionResult.get(contingency.getIndex());
            DenseMatrix postContingencyFlowStates = flowResult.get(contingency.getIndex());
            DisabledNetwork disabledNetwork = disabledNetworksByPropagatedContingencies.get(contingency);

            Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, disabledNetwork, contingency);
            Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
            Optional<Double> functionPredefinedResults = predefinedResults.getRight();

            double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
            double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);

            if (sensitivityValuePredefinedResult.isEmpty()) {
                sensitivityValue = p1.calculateSensi(postContingencyInjectionStates, factorGroup.getIndex());
            }

            if (functionPredefinedResults.isEmpty()) {
                functionValue = p1.calculateSensi(postContingencyFlowStates, 0);
            }

            functionValue = fixZeroFunctionReference(contingency, functionValue);
            double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
            if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                int contingencyIndex = contingency != null ? contingency.getIndex() : -1;
                resultWriter.writeSensitivityValue(factor.getIndex(), contingencyIndex, unscaledSensi, unscaleFunction(factor, functionValue));
            }
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
     * Calculate the sensitivity value for pre-contingency state only.
     */
    private void setBaseCaseSensitivityValues(SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, DenseMatrix factorsState) {
        for (SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup : factorGroups.getList()) {
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorGroup.getFactors()) {
                factor.setBaseCaseSensitivityValue(factor.getFunctionEquationTerm().calculateSensi(factorsState, factorGroup.getIndex()));
            }
        }
    }

    /**
     * Set the function reference of factors with calculated pre-contingency or post-contingency states.
     */
    private void setFunctionReference(List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, DenseMatrix states) {
        double[] statesArray = new double[states.getRowCount()];
        for (int i = 0; i < states.getRowCount(); i++) {
            statesArray[i] = states.get(i, 0);
        }
        StateVector sv = new StateVector(statesArray);
        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval(sv)); // pass explicitly the previously calculated state vector
        }
    }

    /**
     * Calculate sensitivity values for post-contingency state.
     */
    private void calculateSensitivityValues(List<DenseMatrix> injectionResult, List<DenseMatrix> flowResult, HashMap<PropagatedContingency, DisabledNetwork> disabledNetworksByPropagatedContingencies,
                                            List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, List<PropagatedContingency> contingencies,
                                            SensitivityResultWriter resultWriter) {
        if (lfFactors.isEmpty()) {
            return;
        }

        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    createBranchPreContingencySensitivityValue(factor, resultWriter);
                    createBranchPostContingenciesSensitivityValue(factor, null, contingencies, injectionResult, flowResult, disabledNetworksByPropagatedContingencies, resultWriter);
                });

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchPreContingencySensitivityValue(factor, resultWriter);
                createBranchPostContingenciesSensitivityValue(factor, factorGroup, contingencies, injectionResult, flowResult, disabledNetworksByPropagatedContingencies, resultWriter);
            }
        }
    }

    /**
     * Compute flow rhs taking into account slack distribution.
     */
    private static DenseMatrix getPreContingencyFlowRhs(DcLoadFlowContext loadFlowContext,
                                                        List<ParticipatingElement> participatingElements,
                                                        DisabledNetwork disabledNetwork) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] preContingencyFlowRhs = getDcLoadFlowTargetVector(loadFlowContext, disabledNetwork);

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return new DenseMatrix(preContingencyFlowRhs.length, 1, preContingencyFlowRhs);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling, that only
     * returns the target vector of the equation system.
     */
    private static double[] getDcLoadFlowTargetVector(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork) {

        Collection<LfBus> remainingBuses;
        if (disabledNetwork.getBuses().isEmpty()) {
            remainingBuses = loadFlowContext.getNetwork().getBuses();
        } else {
            remainingBuses = new LinkedHashSet<>(loadFlowContext.getNetwork().getBuses());
            remainingBuses.removeAll(disabledNetwork.getBuses());
        }

        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            DcLoadFlowEngine.distributeSlack(remainingBuses, parameters.getBalanceType(), parameters.getNetworkParameters().isUseActiveLimits());
        }

        // we need to copy the target array because:
        //  - in case of disabled buses or branches some elements could be overwritten to zero
        //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
        //    to fill with the solution
        // so we need to copy to later the target as it is and reusable for next run
        var targetVectorArray = loadFlowContext.getTargetVector().getArray().clone();

        if (!disabledNetwork.getBuses().isEmpty()) {
            // set buses injections and transformers to 0
            disabledNetwork.getBuses().stream()
                    .flatMap(lfBus -> loadFlowContext.getEquationSystem().getEquation(lfBus.getNum(), DcEquationType.BUS_TARGET_P).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        if (!disabledNetwork.getBranches().isEmpty()) {
            // set transformer phase shift to 0
            disabledNetwork.getBranches().stream()
                    .flatMap(lfBranch -> loadFlowContext.getEquationSystem().getEquation(lfBranch.getNum(), DcEquationType.BRANCH_TARGET_ALPHA1).stream())
                    .map(Equation::getColumn)
                    .forEach(column -> targetVectorArray[column] = 0);
        }

        return targetVectorArray;
    }

    /**
     * Compute all the injection rhs taking into account slack distribution.
     */
    static DenseMatrix getPreContingencyInjectionRhs(DcLoadFlowContext loadFlowContext,
                                                     SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                     List<ParticipatingElement> participatingElements) {
        Map<LfBus, Double> slackParticipationByBus;
        if (participatingElements.isEmpty()) {
            slackParticipationByBus = new HashMap<>();
        } else {
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                    ParticipatingElement::getLfBus,
                    element -> -element.getFactor(),
                    Double::sum));
        }

        return initFactorsRhs(loadFlowContext.getEquationSystem(), factorGroups, slackParticipationByBus);
    }

    private void processHvdcLinesWithDisconnection(DcLoadFlowContext loadFlowContext, Set<LfBus> disabledBuses, ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult) {
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }
    }

    private List<ParticipatingElement> getNewNormalizedParticipationFactors(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt,
                                                                            LfContingency lfContingency, List<ParticipatingElement> participatingElements) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        List<ParticipatingElement> newParticipatingElements;
        if (isDistributedSlackOnGenerators(loadFlowContext.getParameters())) {
            // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
            Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
            newParticipatingElements = participatingElements.stream()
                    .filter(participatingElement -> !(participatingElement.getElement() instanceof LfGenerator lfGenerator
                            && participatingGeneratorsToRemove.contains(lfGenerator)))
                    .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                    .toList();
            normalizeParticipationFactors(newParticipatingElements);
        } else { // slack distribution on loads
            newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
        }
        return newParticipatingElements;
    }

    protected void cleanContingencies(LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        for (PropagatedContingency contingency : contingencies) {
            // Elements have already been checked and found in PropagatedContingency, so there is no need to
            // check them again
            Set<String> branchesToRemove = new HashSet<>(); // branches connected to one side, or switches
            for (String branchId : contingency.getBranchIdsToOpen().keySet()) {
                LfBranch lfBranch = lfNetwork.getBranchById(branchId);
                if (lfBranch == null) {
                    branchesToRemove.add(branchId); // disconnected branch
                    continue;
                }
                if (!lfBranch.isConnectedAtBothSides()) {
                    branchesToRemove.add(branchId); // branch connected only on one side
                }
            }
            branchesToRemove.forEach(branchToRemove -> contingency.getBranchIdsToOpen().remove(branchToRemove));

            // update branches to open connected with buses in contingency. This is an approximation:
            // these branches are indeed just open at one side.
            String slackBusId = null;
            for (String busId : contingency.getBusIdsToLose()) {
                LfBus bus = lfNetwork.getBusById(busId);
                if (bus != null) {
                    if (bus.isSlack()) {
                        // slack bus disabling is not supported in DC because the relocation is done from propagated contingency
                        // to LfContingency
                        // we keep the slack bus enabled and the connected branches
                        LOGGER.error("Contingency '{}' leads to the loss of a slack bus: slack bus kept", contingency.getContingency().getId());
                        slackBusId = busId;
                    } else {
                        bus.getBranches().forEach(branch -> contingency.getBranchIdsToOpen().put(branch.getId(), DisabledBranchStatus.BOTH_SIDES));
                    }
                }
            }
            if (slackBusId != null) {
                contingency.getBusIdsToLose().remove(slackBusId);
            }

            if (contingency.hasNoImpact()) {
                LOGGER.warn("Contingency '{}' has no impact", contingency.getContingency().getId());
            }
        }
    }

    @Override
    public void analyse(Network network, List<PropagatedContingency> contingencies, List<SensitivityVariableSet> variableSets,
                        SensitivityFactorReader factorReader, SensitivityResultWriter resultWriter, ReportNode reportNode,
                        LfTopoConfig topoConfig) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(variableSets);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(resultWriter);

        LoadFlowParameters lfParameters = parameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowParameters.get(lfParameters);

        Stopwatch stopwatch = Stopwatch.createStarted();

        boolean breakers = topoConfig.isBreaker();

        // create the network (we only manage main connected component)
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(),
                lfParametersExt.getSlackBusesIds(),
                lfParametersExt.getPlausibleActivePowerLimit(),
                lfParametersExt.getMostMeshedSlackBusSelectorMaxNominalVoltagePercentile(),
                lfParametersExt.getSlackBusCountryFilter());
        if (lfParameters.isReadSlackBus()) {
            slackBusSelector = new NetworkSlackBusSelector(network, lfParametersExt.getSlackBusCountryFilter(), slackBusSelector);
        }
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(false)
                .setMinImpedance(true)
                .setTwtSplitShuntAdmittance(lfParameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(lfParametersExt.getPlausibleActivePowerLimit())
                .setComputeMainConnectedComponentOnly(true)
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
        try (LfNetworkList lfNetworks = Networks.load(network, lfNetworkParameters, topoConfig, reportNode)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow(() -> new PowsyblException("Empty network"));

            checkContingencies(contingencies);
            cleanContingencies(lfNetwork, contingencies);
            checkLoadFlowParameters(lfParameters);

            Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
            SensitivityFactorHolder<DcVariableType, DcEquationType> allFactorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork, breakers);
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> allLfFactors = allFactorHolder.getAllFactors();

            allLfFactors.stream()
                    .filter(lfFactor -> lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER_1
                            && lfFactor.getFunctionType() != SensitivityFunctionType.BRANCH_ACTIVE_POWER_2
                            || lfFactor.getVariableType() != SensitivityVariableType.INJECTION_ACTIVE_POWER
                            && lfFactor.getVariableType() != SensitivityVariableType.TRANSFORMER_PHASE
                            && lfFactor.getVariableType() != SensitivityVariableType.HVDC_LINE_ACTIVE_POWER)
                    .findFirst()
                    .ifPresent(ignored -> {
                        throw new PowsyblException("Only variables of type TRANSFORMER_PHASE, INJECTION_ACTIVE_POWER and HVDC_LINE_ACTIVE_POWER, and functions of type BRANCH_ACTIVE_POWER_1 and BRANCH_ACTIVE_POWER_2 are yet supported in DC");
                    });

            LOGGER.info("Running DC sensitivity analysis with {} factors and {} contingencies", allLfFactors.size(), contingencies.size());

            var dcLoadFlowParameters = createDcLoadFlowParameters(lfNetworkParameters, matrixFactory, lfParameters, lfParametersExt);

            // next we only work with valid factors
            var validFactorHolder = writeInvalidFactors(allFactorHolder, resultWriter, contingencies);
            var validLfFactors = validFactorHolder.getAllFactors();
            LOGGER.info("{}/{} factors are valid", validLfFactors.size(), allLfFactors.size());

            try (DcLoadFlowContext loadFlowContext = new DcLoadFlowContext(lfNetwork, dcLoadFlowParameters, false)) {

                // create jacobian matrix either using calculated voltages from pre-contingency network or nominal voltages
                VoltageInitializer voltageInitializer = lfParameters.getVoltageInitMode() == LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES
                        ? new PreviousValueVoltageInitializer()
                        : new UniformValueVoltageInitializer();

                DcLoadFlowEngine.initStateVector(lfNetwork, loadFlowContext.getEquationSystem(), voltageInitializer);

                // index factors by variable group to compute the minimal number of states
                SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups = createFactorGroups(validLfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).toList());

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution)
                List<ParticipatingElement> participatingElements = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt)
                        : Collections.emptyList();

                // engine to compute pre- and post-contingency states for sensitivities calculations
                WoodburyEngine engine = new WoodburyEngine();

                // pre-contingency injection/flow rhs
                DenseMatrix flowsRhs = getPreContingencyFlowRhs(loadFlowContext, participatingElements, new DisabledNetwork());
                solveMultipleTargets(flowsRhs, loadFlowContext.getJacobianMatrix(), reportNode);

                DenseMatrix injectionRhs = getPreContingencyInjectionRhs(loadFlowContext, factorGroups, participatingElements);
                solveMultipleTargets(injectionRhs, loadFlowContext.getJacobianMatrix(), reportNode);

                // compute states with +1 -1 to model the contingencies and run connectivity analysis
                ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityData = ConnectivityBreakAnalysis.run(loadFlowContext, contingencies);

                // storage of disabled network by propagated contingencies, for sensitivity calculation
                HashMap<PropagatedContingency, DisabledNetwork> disabledNetworkByPropagatedContingency = new HashMap<>();

                WoodburyEngineRhsReader injectionReader = handler -> {
                    for (PropagatedContingency contingency : contingencies) {
                        DenseMatrix preContingencyStatesOverride = null;
                        Set<String> elementsToReconnect = Collections.emptySet();
                        Set<LfBus> disabledBuses = Collections.emptySet();
                        Set<LfBranch> partialDisabledBranches = Collections.emptySet();
                        List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;

                        // determine if the contingency breaks the connectivity, and if so recompute right member
                        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityData.connectivityAnalysisResults()) {
                            if (connectivityAnalysisResult.getContingencies().contains(contingency)) {
                                disabledBuses = connectivityAnalysisResult.getDisabledBuses();

                                // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
                                // if one bus of the line is lost.
                                processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

                                // null and unused if slack bus is not distributed
                                boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
                                if (lfParameters.isDistributedSlack()) {
                                    Set<LfBus> finalDisabledBuses = disabledBuses;
                                    rhsChanged = participatingElementsForThisConnectivity.stream().anyMatch(element -> finalDisabledBuses.contains(element.getLfBus()));
                                }
                                if (factorGroups.hasMultiVariables()) {
                                    // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
                                    rhsChanged |= rescaleGlsk(factorGroups, disabledBuses);
                                }
                                // we need to recompute the injection rhs because the connectivity changed
                                if (rhsChanged) {
                                    participatingElementsForThisConnectivity = new ArrayList<>(lfParameters.isDistributedSlack()
                                            ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                                            : Collections.emptyList());
                                    preContingencyStatesOverride = getPreContingencyInjectionRhs(loadFlowContext, factorGroups, participatingElementsForThisConnectivity);
                                }
                                elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
                                partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();
                            }
                        }

                        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                        disabledBranches.addAll(partialDisabledBranches);
                        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
                        disabledNetworkByPropagatedContingency.put(contingency, disabledNetwork);

                        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
                            resultWriter.writeContingencyStatus(contingency.getIndex(), contingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS);
                        } else {
                            // if we have a contingency including the loss of a DC line or a generator or a load
                            // save base state for later restoration after each contingency
                            LfNetwork lfNetwork2 = loadFlowContext.getNetwork();
                            NetworkState networkState = NetworkState.save(lfNetwork2);
                            LfContingency lfContingency = contingency.toLfContingency(lfNetwork2).orElse(null);
                            if (lfContingency != null) {
                                DcLoadFlowParameters lfParameters2 = loadFlowContext.getParameters();
                                lfContingency.apply(lfParameters2.getBalanceType());
                                List<ParticipatingElement> modifiedParticipatingElements = participatingElements;
                                boolean rhsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                                        || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
                                if (rhsChanged) {
                                    modifiedParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, modifiedParticipatingElements);
                                }
                                if (factorGroups.hasMultiVariables()) {
                                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                                    rhsChanged |= rescaleGlsk(factorGroups, impactedBuses);
                                }
                                if (rhsChanged) {
                                    preContingencyStatesOverride = getPreContingencyInjectionRhs(loadFlowContext, factorGroups, modifiedParticipatingElements);
                                }
                                // write contingency status
                                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
                            } else {
                                // write contingency status
                                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
                            }
                            networkState.restore();
                        }

                        if (!Objects.isNull(preContingencyStatesOverride)) {
                            solveMultipleTargets(preContingencyStatesOverride, loadFlowContext.getJacobianMatrix(), reportNode);
                        } else {
                            preContingencyStatesOverride = injectionRhs;
                        }

                        Set<String> finalElementsToReconnect1 = elementsToReconnect;
                        Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                                .filter(element -> !finalElementsToReconnect1.contains(element))
                                .map(connectivityData.contingencyElementByBranch()::get)
                                .toList();

                        handler.onContingency(contingency, contingencyElements, preContingencyStatesOverride);
                    }
                };

                WoodburyEngineRhsReader flowReader = handler -> {

                    for (PropagatedContingency contingency : contingencies) {
                        DenseMatrix preContingencyStatesOverride = null;
                        Set<String> elementsToReconnect = Collections.emptySet();
                        Set<LfBus> disabledBuses = Collections.emptySet();
                        Set<LfBranch> partialDisabledBranches = Collections.emptySet();
                        List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;

                        // determine if the contingency breaks the connectivity, and if so recompute right member
                        for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityData.connectivityAnalysisResults()) {
                            if (connectivityAnalysisResult.getContingencies().contains(contingency)) {
                                disabledBuses = connectivityAnalysisResult.getDisabledBuses();

                                // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
                                // if one bus of the line is lost.
                                processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

                                // null and unused if slack bus is not distributed
                                boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
                                if (lfParameters.isDistributedSlack()) {
                                    Set<LfBus> finalDisabledBuses = disabledBuses;
                                    rhsChanged = participatingElementsForThisConnectivity.stream().anyMatch(element -> finalDisabledBuses.contains(element.getLfBus()));
                                }
                                // we need to recompute the injection rhs because the connectivity changed
                                if (rhsChanged) {
                                    participatingElementsForThisConnectivity = new ArrayList<>(lfParameters.isDistributedSlack()
                                            ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                                            : Collections.emptyList());
                                }

                                DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, Collections.emptySet());
                                preContingencyStatesOverride = getPreContingencyFlowRhs(loadFlowContext, participatingElementsForThisConnectivity, disabledNetwork);
                                elementsToReconnect = connectivityAnalysisResult.getElementsToReconnect();
                                partialDisabledBranches = connectivityAnalysisResult.getPartialDisabledBranches();
                            }
                        }

                        // in case a phase tap changer is lost, flow rhs must be updated
                        Set<String> finalElementsToReconnect = elementsToReconnect;
                        Set<LfBranch> disabledPhaseTapChangers = contingency.getBranchIdsToOpen().keySet().stream()
                                .filter(element -> !finalElementsToReconnect.contains(element))
                                .map(connectivityData.contingencyElementByBranch()::get)
                                .map(ComputedContingencyElement::getLfBranch)
                                .filter(LfBranch::hasPhaseControllerCapability)
                                .collect(Collectors.toSet());
                        if (!disabledPhaseTapChangers.isEmpty()) {
                            preContingencyStatesOverride = getPreContingencyFlowRhs(loadFlowContext, participatingElementsForThisConnectivity,
                                    new DisabledNetwork(disabledBuses, disabledPhaseTapChangers));
                        }

                        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                        disabledBranches.addAll(partialDisabledBranches);
                        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);
//                        disabledNetworkByPropagatedContingency.put(contingency, disabledNetwork);
                        if (!(contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty())) {
                            // if we have a contingency including the loss of a DC line or a generator or a load
                            // save base state for later restoration after each contingency
                            LfNetwork lfNetwork2 = loadFlowContext.getNetwork();
                            NetworkState networkState = NetworkState.save(lfNetwork2);
                            LfContingency lfContingency = contingency.toLfContingency(lfNetwork2).orElse(null);
                            List<ParticipatingElement> newParticipatingElements = participatingElementsForThisConnectivity;
                            if (lfContingency != null) {
                                List<ParticipatingElement> modifiedParticipatingElements = participatingElementsForThisConnectivity;
                                lfContingency.apply(lfParameters.getBalanceType());
                                boolean rhsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                                        || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
                                if (rhsChanged) {
                                    modifiedParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, modifiedParticipatingElements);
                                }
                                newParticipatingElements = modifiedParticipatingElements;
                            }

                            preContingencyStatesOverride = getPreContingencyFlowRhs(loadFlowContext, newParticipatingElements, disabledNetwork);
                            networkState.restore();
                        }

                        if (!Objects.isNull(preContingencyStatesOverride)) {
                            solveMultipleTargets(preContingencyStatesOverride, loadFlowContext.getJacobianMatrix(), reportNode);
                        } else {
                            preContingencyStatesOverride = flowsRhs;
                        }

                        Set<String> finalElementsToReconnect1 = elementsToReconnect;
                        Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                                .filter(element -> !finalElementsToReconnect1.contains(element))
                                .map(connectivityData.contingencyElementByBranch()::get)
                                .toList();

                        handler.onContingency(contingency, contingencyElements, preContingencyStatesOverride);
                    }
                };

                // compute pre- and post-contingency flow states
                List<DenseMatrix> flowResult = engine.run(loadFlowContext, flowReader, connectivityData.contingenciesStates());
                // set function reference values of the factors
                setFunctionReference(validLfFactors, flowsRhs);

                // compute pre- and post-contingency injection states
                List<DenseMatrix> injectionResult = engine.run(loadFlowContext, injectionReader, connectivityData.contingenciesStates());
                // set base case values of the factors
                setBaseCaseSensitivityValues(factorGroups, injectionRhs);

                // compute the sensibilities with Woodbury computed states (pre- and post- contingency), and computed disabledNetworks
                calculateSensitivityValues(injectionResult, flowResult, disabledNetworkByPropagatedContingency, validFactorHolder.getAllFactors(), contingencies, resultWriter);
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
