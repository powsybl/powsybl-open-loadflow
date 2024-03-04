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
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.equations.*;
import com.powsybl.openloadflow.equations.EquationTerm;
import com.powsybl.openloadflow.equations.StateVector;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.lf.LoadFlowContext;
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
                                                               List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter, WoodburyEngine.WoodburyEngineOutput output) {

        EquationTerm<DcVariableType, DcEquationType> p1 = factor.getFunctionEquationTerm();
        for (var contingency : contingencies) {

            Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, output.getPostContingenciesDisabledNetworks().get(contingency.getIndex()), contingency);
            // TODO : add a valid DisabledNetwork. My impression is that we use it to have "default values" in the case where the sensitivity is not the best
            Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
            Optional<Double> functionPredefinedResults = predefinedResults.getRight();


            double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
            double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);
            if (sensitivityValuePredefinedResult.isEmpty()) {
                sensitivityValue = p1.calculateSensi(output.getPostContingenciesStates().get(contingency.getIndex()), factorGroup.getIndex());
            }

            if (functionPredefinedResults.isEmpty()) {
                functionValue = p1.calculateSensi(output.getPostContingenciesFlowStates().get(contingency.getIndex()), 0);
            }

            functionValue = fixZeroFunctionReference(contingency, functionValue);
            double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
            if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
                resultWriter.writeSensitivityValue(factor.getIndex(), contingency.getIndex(), unscaledSensi, unscaleFunction(factor, functionValue));
            }
        }
    }

//    // TODO : add step by step the content of the old function
//    private void createBranchSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup,
//                                              List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter, List<DenseMatrix> postContingenciesStates) {
//
//        Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, new DisabledNetwork(), null); // TODO : add a valid DisabledNetwork
//        Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
//        Optional<Double> functionPredefinedResults = predefinedResults.getRight();
//        double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
//        double functionValue = fixZeroFunctionReference(null, functionPredefinedResults.orElseGet(factor::getFunctionReference));
//        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
//        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
//            resultWriter.writeSensitivityValue(factor.getIndex(), -1, unscaledSensi, unscaleFunction(factor, functionValue));
//        }
//
//        EquationTerm<DcVariableType, DcEquationType> p1 = factor.getFunctionEquationTerm();
//        for (var contingency : contingencies) {
//            double contingencySensitivity = p1.calculateSensi(postContingenciesStates.get(contingency.getIndex()), factorGroup.getIndex());
//            sensitivityValue = contingencySensitivity;
//            functionValue = contingencySensitivity;
//
//            functionValue = fixZeroFunctionReference(contingency, functionValue);
//            unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
//            if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
//                resultWriter.writeSensitivityValue(factor.getIndex(), contingency.getIndex(), unscaledSensi, unscaleFunction(factor, functionValue));
//            }
//        }

//        if (!(functionPredefinedResults.isPresent() && sensitivityValuePredefinedResult.isPresent())) {
//            for (ComputedContingencyElement contingencyElement : contingencyElements) {
//                double contingencySensitivity = p1.calculateSensi(contingenciesStates, contingencyElement.getContingencyIndex());
//                if (functionPredefinedResults.isEmpty()) {
//                    functionValue += contingencyElement.getAlphaForFunctionReference() * contingencySensitivity;
//                }
//                if (sensitivityValuePredefinedResult.isEmpty()) {
//                    sensitivityValue += contingencyElement.getAlphaForSensitivityValue() * contingencySensitivity;
//                }
//            }
//        }

//        functionValue = fixZeroFunctionReference(contingency, functionValue);
//        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
//        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
//            resultWriter.writeSensitivityValue(factor.getIndex(), contingency != null ? contingency.getIndex() : -1, unscaledSensi, unscaleFunction(factor, functionValue));
//        }
//    }

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

//    /**
//     * Calculate sensitivity values for pre-contingency state or a post-contingency state using the pre-contingency sensitivity
//     * value and some flow transfer factors (alphas).
//     */
//    private void calculateSensitivityValues(DcLoadFlowContext loadFlowContext, List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, DenseMatrix factorStates,
//                                            DenseMatrix contingenciesStates, DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements,
//                                            PropagatedContingency contingency, SensitivityResultWriter resultWriter, DisabledNetwork disabledNetwork) {

//        if (lfFactors.isEmpty()) {
//            return;
//        }
//        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
//        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
//                .forEach(factor -> createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork));
//
//        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
//                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
//                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
//        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
//            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
//            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
//            setAlphas(loadFlowContext, contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForSensitivityValue);
//            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
//                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork);
//            }
//        }
//    }

    /**
     * Calculate sensitivity values for post-contingency state.
     */
    private void calculateSensitivityValues(WoodburyEngine.WoodburyEngineOutput output, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors,
                                            List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter) {
        if (lfFactors.isEmpty()) {
            return;
        }

        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> {
                    createBranchPreContingencySensitivityValue(factor, resultWriter);
                    createBranchPostContingenciesSensitivityValue(factor, null, contingencies, resultWriter, output);
                });

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchPreContingencySensitivityValue(factor, resultWriter);
                createBranchPostContingenciesSensitivityValue(factor, factorGroup, contingencies, resultWriter, output);
            }
        }

//        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);
//        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
//                .forEach(factor -> createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork));
//
//        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
//                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
//                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
//        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
//            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
//            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
//            setAlphas(loadFlowContext, contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForSensitivityValue);
//            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
//                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork);
//            }
//        }
    }

    /**
     * Compute all the injection vectors of the Woodbury engine.
     */
    private DenseMatrix getInjectionVectors(DcLoadFlowContext loadFlowContext,
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
                WoodburyEngine.WoodburyEngineOutput output = engine.run(loadFlowContext, lfParameters, lfParametersExt, injectionVectors, contingencies, participatingElements, reporter, validFactorHolder, resultWriter);

                // Set base case/reference values of the sensitivities
                setFunctionReference(validLfFactors, output.getPreContingenciesFlowStates());
                setBaseCaseSensitivityValues(factorGroups, output.getPreContingenciesFactorStates()); // use this state to compute the base sensitivity (without +1-1)

                // Compute sensibilities for given factors
                calculateSensitivityValues(output, factorGroups,
                        validFactorHolder.getAllFactors(), contingencies, resultWriter);

                // compute the pre-contingency sensitivity values
//                DenseMatrix factorsStates = calculateFactorStates(loadFlowContext, factorGroups, participatingElements);
                // calculate sensitivity values for pre-contingency network
//                calculateSensitivityValues(loadFlowContext, validFactorHolder.getFactorsForBaseNetwork(), injectionVectors, null, flowStates,
//                        Collections.emptySet(), null, resultWriter, new DisabledNetwork());
                // compute states with +1 -1 to model the contingencies
//
//                // process contingencies with no connectivity break
//                calculateSensitivityValuesForContingencyList(loadFlowContext, lfParametersExt, validFactorHolder, factorGroups,
//                        factorsStates, contingenciesStates, flowStates, nonBreakingConnectivityContingencies, contingencyElementByBranch,
//                        Collections.emptySet(), participatingElements, Collections.emptySet(), resultWriter, reporter, Collections.emptySet());
//
//                LOGGER.info("Processing contingencies with connectivity break");
//
//                // process contingencies with connectivity break
//                for (ConnectivityAnalysisResult connectivityAnalysisResult : connectivityAnalysisResults) {
//                    processContingenciesBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
//                            validFactorHolder, factorGroups, participatingElements,
//                            contingencyElementByBranch, flowStates, factorsStates, contingenciesStates, resultWriter, reporter);
//                }
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
