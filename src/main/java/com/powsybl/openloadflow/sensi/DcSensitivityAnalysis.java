/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.EquationTerm;
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
import com.powsybl.sensitivity.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        double functionValue = fixZeroFunctionReference(null, functionPredefinedResults.orElseGet(factor::getFunctionReference));
        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
            resultWriter.writeSensitivityValue(factor.getIndex(), -1, unscaledSensi, unscaleFunction(factor, functionValue));
        }
    }

    private void createBranchPostContingenciesSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup,
                                                               List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter, WoodburyEngineResult results) {

        // TODO : refactor
        EquationTerm<DcVariableType, DcEquationType> p1 = factor.getFunctionEquationTerm();
        for (var contingency : contingencies) {

            WoodburyEngineResult.PostContingencyWoodburyResult result = results.getPostContingencyWoodburyResults().get(contingency);

            Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, result.getPostContingencyDisabledNetwork(), contingency);
            Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
            Optional<Double> functionPredefinedResults = predefinedResults.getRight();

            double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
            double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);

            if (sensitivityValuePredefinedResult.isEmpty()) {
                sensitivityValue = p1.calculateSensi(result.getPostContingencyStates(), factorGroup.getIndex());
            }

            if (functionPredefinedResults.isEmpty()) {
                functionValue = p1.calculateSensi(result.getPostContingencyFlowStates(), 0);
            }

            functionValue = fixZeroFunctionReference(contingency, functionValue);
            double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
            if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                resultWriter.writeSensitivityValue(factor.getIndex(), contingency.getIndex(), unscaledSensi, unscaleFunction(factor, functionValue));
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
    private void setFunctionReference(List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, double[] states) {
        StateVector sv = new StateVector(states);
        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval(sv)); // pass explicitly the previously calculated state vector
        }
    }

    /**
     * Calculate sensitivity values for post-contingency state.
     */
    private void calculateSensitivityValues(WoodburyEngineResult woodburyEngineResult, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors,
                                            List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter) {
        if (lfFactors.isEmpty()) {
            return;
        }

        // TODO : refactor
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    createBranchPreContingencySensitivityValue(factor, resultWriter);
                    createBranchPostContingenciesSensitivityValue(factor, null, contingencies, resultWriter, woodburyEngineResult);
                });

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchPreContingencySensitivityValue(factor, resultWriter);
                createBranchPostContingenciesSensitivityValue(factor, factorGroup, contingencies, resultWriter, woodburyEngineResult);
            }
        }
    }

    /**
     * Compute all the injection vectors of the Woodbury engine.
     */
    public static DenseMatrix getInjectionVectors(DcLoadFlowContext loadFlowContext,
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

        return initFactorsRhs(loadFlowContext.getEquationSystem(), factorGroups, slackParticipationByBus);
    }

    /**
     * Write contingency statuses after woodbury engine calculation.
     */
    private void setContingencyStatus(SensitivityResultWriter resultWriter, WoodburyEngineResult woodburyEngineResult) {
        for (var contingencyStatus : woodburyEngineResult.getContingencyStatuses().entrySet()) {
            SensitivityAnalysisResult.Status status = contingencyStatus.getValue() ? SensitivityAnalysisResult.Status.SUCCESS : SensitivityAnalysisResult.Status.NO_IMPACT;
            resultWriter.writeContingencyStatus(contingencyStatus.getKey().getIndex(), status);
        }
    }

    public static boolean isDistributedSlackOnGenerators(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P);
    }

    public static boolean isDistributedSlackOnLoads(DcLoadFlowParameters lfParameters) {
        return lfParameters.isDistributedSlack()
                && (lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_LOAD
                || lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD);
    }

    private void rhsModificationForContingenciesBreakingConnectivity(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, List<WoodburyEngine.ConnectivityAnalysisResult> connectivityAnalysisResults,
                                                                     SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, List<ParticipatingElement> participatingElements,
                                                                     WoodburyEngineInjectionInput input, SensitivityResultWriter resultWriter) {
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();

        for (WoodburyEngine.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityAnalysisResults) {
            var disabledBuses = connectivityAnalysisResult.getDisabledBuses();

            // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
                if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                    connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                        contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                        contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                    });
                }
            }

            // null and unused if slack bus is not distributed
            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
            boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
            if (lfParameters.isDistributedSlack()) {
                rhsChanged = participatingElements.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
            }
            if (factorGroups.hasMultiVariables()) {
                // some elements of the GLSK may not be in the connected component anymore, we recompute the injections
                rhsChanged |= rescaleGlsk(factorGroups, disabledBuses);
            }
            // we need to recompute the factor states because the connectivity changed
            if (rhsChanged) {
                participatingElementsForThisConnectivity = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                        : Collections.emptyList();
                // TODO : add saving of new injection vector
                input.getNewParticipantElementsForAConnectivity().put(connectivityAnalysisResult, participatingElementsForThisConnectivity);
                input.getNewInjectionVectorsForAConnectivity().put(connectivityAnalysisResult, DcSensitivityAnalysis.getInjectionVectors(loadFlowContext, factorGroups, participatingElementsForThisConnectivity));
            }

            // TODO : rename following lists
            List<PropagatedContingency> contingenciesWithGeneratorOrLoadLost = connectivityAnalysisResult.getContingencies().stream()
                    .filter(propagatedContingency -> !propagatedContingency.getGeneratorIdsToLose().isEmpty() || !propagatedContingency.getLoadIdsToLoose().isEmpty())
                    .collect(Collectors.toList());
            connectivityAnalysisResult.getContingencies().stream()
                    .filter(propagatedContingency -> propagatedContingency.getGeneratorIdsToLose().isEmpty() && propagatedContingency.getLoadIdsToLoose().isEmpty())
                    .forEach(propagatedContingency -> resultWriter.writeContingencyStatus(propagatedContingency.getIndex(), propagatedContingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS));
            functionToBeNamed(loadFlowContext, lfParametersExt, factorGroups, contingenciesWithGeneratorOrLoadLost, participatingElementsForThisConnectivity, input, resultWriter);
        }
    }

    private void functionToBeNamed(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                   List<PropagatedContingency> contingencies, List<ParticipatingElement> participatingElements, WoodburyEngineInjectionInput input,
                                   SensitivityResultWriter resultWriter) {

        LfNetwork lfNetwork = loadFlowContext.getNetwork();
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        for (PropagatedContingency contingency : contingencies) {
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            boolean rhsChanged = false;
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                boolean participatingElementsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
                if (factorGroups.hasMultiVariables()) {
                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                    rhsChanged = rescaleGlsk(factorGroups, impactedBuses);
                }
                if (participatingElementsChanged) {
                    if (isDistributedSlackOnGenerators(loadFlowContext.getParameters())) {
                        // deep copy of participatingElements, removing the participating LfGeneratorImpl whose targetP has been set to 0
                        Set<LfGenerator> participatingGeneratorsToRemove = lfContingency.getLostGenerators();
                        newParticipatingElements = participatingElements.stream()
                                .filter(participatingElement -> !participatingGeneratorsToRemove.contains(participatingElement.getElement()))
                                .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                                .collect(Collectors.toList());
                        normalizeParticipationFactors(newParticipatingElements);
                    } else { // slack distribution on loads
                        newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
                    }
                }
                if (participatingElementsChanged || rhsChanged) {
                    input.getNewInjectionVectorsByPropagatedContingency().put(contingency, DcSensitivityAnalysis.getInjectionVectors(loadFlowContext, factorGroups, newParticipatingElements));
                }

                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            }

            input.getNewParticipatingElementsByPropagatedContingency().put(contingency, newParticipatingElements);
            networkState.restore();
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
                .setHvdcAcEmulation(false)
                .setCacheEnabled(false) // force not caching as not supported in sensi analysis
                .setReferenceBusSelector(ReferenceBusSelector.DEFAULT_SELECTOR); // not supported yet
        // create networks including all necessary switches
        try (LfNetworkList lfNetworks = Networks.load(network, lfNetworkParameters, topoConfig, reportNode)) {
            LfNetwork lfNetwork = lfNetworks.getLargest().orElseThrow(() -> new PowsyblException("Empty network"));

            checkContingencies(lfNetwork, contingencies);
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
                SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups = createFactorGroups(validLfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution)
                List<ParticipatingElement> participatingElements = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt)
                        : Collections.emptyList();

                // we need to copy the target array because:
                //  - in case of disabled buses or branches some elements could be overwritten to zero
                //  - JacobianMatrix.solveTransposed take as an input the second member and reuse the array
                //    to fill with the solution
                // so we need to copy to later the target as it is and reusable for next run
                DenseMatrix injectionVectors = getInjectionVectors(loadFlowContext, factorGroups, participatingElements); // for now is only rhs

                WoodburyEngine engine = new WoodburyEngine();

                // run connectivity analysis and compute post-contingency states in woodbury engine
                WoodburyEngine.ConnectivityDataResult connectivityData = engine.runConnectivityData(loadFlowContext, contingencies);

                WoodburyEngineInjectionInput input = new WoodburyEngineInjectionInput(injectionVectors, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());

                rhsModificationForContingenciesBreakingConnectivity(loadFlowContext, lfParametersExt, connectivityData.getConnectivityAnalysisResults(), factorGroups, participatingElements, input, resultWriter);

                // TODO : rename following lists
                List<PropagatedContingency> nonBreakingConnectivityContingenciesWithGeneratorOrLoadLost = connectivityData.getNonBreakingConnectivityContingencies().stream()
                        .filter(propagatedContingency -> !propagatedContingency.getGeneratorIdsToLose().isEmpty() || !propagatedContingency.getLoadIdsToLoose().isEmpty())
                        .collect(Collectors.toList());
                connectivityData.getNonBreakingConnectivityContingencies().stream()
                                .filter(propagatedContingency -> propagatedContingency.getGeneratorIdsToLose().isEmpty() && propagatedContingency.getLoadIdsToLoose().isEmpty())
                                .forEach(propagatedContingency -> resultWriter.writeContingencyStatus(propagatedContingency.getIndex(), propagatedContingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS));
                functionToBeNamed(loadFlowContext, lfParametersExt, factorGroups, nonBreakingConnectivityContingenciesWithGeneratorOrLoadLost, participatingElements, input, resultWriter);

                // compute the pre- and post-contingency states using Woodbury equality
                WoodburyEngineResult results = engine.run(loadFlowContext, lfParameters, lfParametersExt, injectionVectors,
                        contingencies, participatingElements, reportNode, factorGroups, connectivityData, input);

                // set base case/function reference values of the factors
                setFunctionReference(validLfFactors, results.getPreContingenciesFlowStates());
                setBaseCaseSensitivityValues(factorGroups, results.getPreContingenciesStates()); // use this state to compute the base sensitivity (without +1-1)

                // compute the sensibilities with Woodbury computed states (pre and post contingency)
                calculateSensitivityValues(results, factorGroups, validFactorHolder.getAllFactors(), contingencies, resultWriter);
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
