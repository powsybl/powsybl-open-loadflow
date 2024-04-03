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
                                                               List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter, WoodburyEngineResult woodburyResults) {
        Derivable<DcVariableType> p1 = factor.getFunctionEquationTerm();
        for (PropagatedContingency contingency : contingencies) {

            WoodburyEngineResult.PostContingencyWoodburyResult woodburyResult = woodburyResults.getPostContingencyWoodburyResults().get(contingency);

            Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, woodburyResult.postContingencyDisabledNetwork(), contingency);
            Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
            Optional<Double> functionPredefinedResults = predefinedResults.getRight();

            double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
            double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);

            if (sensitivityValuePredefinedResult.isEmpty()) {
                sensitivityValue = p1.calculateSensi(woodburyResult.postContingencyStates(), factorGroup.getIndex());
            }

            if (functionPredefinedResults.isEmpty()) {
                functionValue = p1.calculateSensi(woodburyResult.postContingencyFlowStates(), 0);
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
    private void setFunctionReference(List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, double[] states) {
        StateVector sv = new StateVector(states);
        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval(sv)); // pass explicitly the previously calculated state vector
        }
    }

    /**
     * Calculate sensitivity values for post-contingency state.
     */
    private void calculateSensitivityValues(WoodburyEngineResult woodburyEngineResult, List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors,
                                            List<PropagatedContingency> contingencies, SensitivityResultWriter resultWriter) {
        if (lfFactors.isEmpty()) {
            return;
        }

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
     * Calculate the states for a fictitious pre-contingency situation.
     * The interesting disabled branches are only phase shifters.
     */
    private static double[] calculatePreContingencyStates(DcLoadFlowContext loadFlowContext,
                                                         List<ParticipatingElement> participatingElements,
                                                         DisabledNetwork disabledNetwork) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] dx = getDcLoadFlowTargetVector(loadFlowContext, disabledNetwork);

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return dx;
    }

    /**
     * TODO
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling and that do not
     * update the state vector and the network at the end (because we don't need it to just evaluate a few equations)
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

        return targetVectorArray; // now contains dx
    }

    /**
     * Compute all the injection vectors taking into account slack distribution.
     */
    static DenseMatrix getWoodburyInjectionVectors(DcLoadFlowContext loadFlowContext,
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

    private void processHvdcLinesWithDisconnection(DcLoadFlowContext loadFlowContext, Set<LfBus> disabledBuses, WoodburyEngine.ConnectivityAnalysisResult connectivityAnalysisResult) {
        for (LfHvdc hvdc : loadFlowContext.getNetwork().getHvdcs()) {
            if (Networks.isIsolatedBusForHvdc(hvdc.getBus1(), disabledBuses) ^ Networks.isIsolatedBusForHvdc(hvdc.getBus2(), disabledBuses)) {
                connectivityAnalysisResult.getContingencies().forEach(contingency -> {
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation1().getId());
                    contingency.getGeneratorIdsToLose().add(hvdc.getConverterStation2().getId());
                });
            }
        }
    }

    private List<PropagatedContingency> getContingenciesWithGeneratorOrLoadLostAndWrite(WoodburyEngine.ConnectivityAnalysisResult connectivityAnalysisResult, SensitivityResultWriter resultWriter) {
        List<PropagatedContingency> contingenciesWithGeneratorOrLoadLost = new ArrayList<>();
        for (PropagatedContingency propagatedContingency : connectivityAnalysisResult.getContingencies()) {
            if (propagatedContingency.getGeneratorIdsToLose().isEmpty() && propagatedContingency.getLoadIdsToLoose().isEmpty()) {
                resultWriter.writeContingencyStatus(propagatedContingency.getIndex(), propagatedContingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                contingenciesWithGeneratorOrLoadLost.add(propagatedContingency);
            }
        }

        return contingenciesWithGeneratorOrLoadLost;
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
                    .filter(participatingElement -> !participatingGeneratorsToRemove.contains(participatingElement.getLfGeneratorElement()))
                    .map(participatingElement -> new ParticipatingElement(participatingElement.getElement(), participatingElement.getFactor()))
                    .toList();
            normalizeParticipationFactors(newParticipatingElements);
        } else { // slack distribution on loads
            newParticipatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt);
        }
        return newParticipatingElements;
    }

    private List<ParticipatingElement> processInjectionRhsModificationForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                                                      LfContingency lfContingency, PropagatedContingency contingency, List<ParticipatingElement> participatingElements,
                                                                                      WoodburyEngineRhsModifications rhsModifications) {
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
        lfContingency.apply(lfParameters.getBalanceType());
        List<ParticipatingElement> newParticipatingElements = participatingElements;
        boolean rhsChanged = isDistributedSlackOnGenerators(loadFlowContext.getParameters()) && !contingency.getGeneratorIdsToLose().isEmpty()
                || isDistributedSlackOnLoads(loadFlowContext.getParameters()) && !contingency.getLoadIdsToLoose().isEmpty();
        if (rhsChanged) {
            newParticipatingElements = getNewNormalizedParticipationFactors(loadFlowContext, lfParametersExt, lfContingency, newParticipatingElements);
        }
        if (factorGroups.hasMultiVariables()) {
            Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
            rhsChanged |= rescaleGlsk(factorGroups, impactedBuses);
        }
        if (rhsChanged) {
            DenseMatrix modifiedInjectionRhs = getWoodburyInjectionVectors(loadFlowContext, factorGroups, newParticipatingElements);
            rhsModifications.getNewInjectionRhsByPropagatedContingency().put(contingency, modifiedInjectionRhs);
        }

        return newParticipatingElements;
    }

    private void buildRhsModificationsForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                      PropagatedContingency contingency, List<ParticipatingElement> participatingElements, WoodburyEngineRhsModifications rhsModifications,
                                                      DisabledNetwork disabledNetwork, SensitivityResultWriter resultWriter) {

        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
            resultWriter.writeContingencyStatus(contingency.getIndex(), contingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS);
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            LfNetwork lfNetwork = loadFlowContext.getNetwork();
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            if (lfContingency != null) {
                newParticipatingElements = processInjectionRhsModificationForAContingency(loadFlowContext, lfParametersExt, factorGroups, lfContingency, contingency,
                        newParticipatingElements, rhsModifications);
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            }

            double[] newFlowsRhs = calculatePreContingencyStates(loadFlowContext, newParticipatingElements, disabledNetwork);
            rhsModifications.getNewFlowRhsByPropagatedContingency().put(contingency, newFlowsRhs);

            networkState.restore();
        }
    }

    private void buildRhsModificationsForContingencies(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                       Collection<PropagatedContingency> contingencies, List<ParticipatingElement> participatingElements, WoodburyEngineRhsModifications rhsModifications,
                                                       Set<LfBus> disabledBuses, Set<LfBranch> partialDisabledBranches, Map<String, WoodburyEngine.ComputedContingencyElement> contingencyElementByBranch,
                                                       Set<String> elementsToReconnect, SensitivityResultWriter resultWriter) {
        LfNetwork lfNetwork = loadFlowContext.getNetwork();

        WoodburyEngine.PhaseTapChangerContingenciesIndexing phaseTapChangerContingenciesIndexing = new WoodburyEngine.PhaseTapChangerContingenciesIndexing(contingencies, contingencyElementByBranch, elementsToReconnect);

        // compute rhs modifications for contingencies without loss of phase tap changer
        // first we compute the ones without loss of phase tap changers (because no need to recompute new rhs for load flows)
        for (PropagatedContingency contingency : phaseTapChangerContingenciesIndexing.getContingenciesWithoutPhaseTapChangerLoss()) {
            Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
            disabledBranches.addAll(partialDisabledBranches);

            buildRhsModificationsForAContingency(loadFlowContext, lfParametersExt, factorGroups, contingency, participatingElements,
                    rhsModifications, new DisabledNetwork(disabledBuses, disabledBranches), resultWriter);
        }

        // then we compute the ones involving the loss of a phase tap changer (because we need to recompute new rhs for load flows)
        for (Map.Entry<Set<LfBranch>, Collection<PropagatedContingency>> e : phaseTapChangerContingenciesIndexing.getContingenciesIndexedByPhaseTapChangers().entrySet()) {
            Set<LfBranch> disabledPhaseTapChangers = e.getKey();
            Collection<PropagatedContingency> propagatedContingencies = e.getValue();
            double[] modifiedFlowRhs = calculatePreContingencyStates(loadFlowContext, participatingElements,
                    new DisabledNetwork(disabledBuses, disabledPhaseTapChangers));

            for (PropagatedContingency contingency : propagatedContingencies) {
                rhsModifications.getNewFlowRhsByPropagatedContingency().put(contingency, modifiedFlowRhs);
                Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
                disabledBranches.addAll(partialDisabledBranches);

                buildRhsModificationsForAContingency(loadFlowContext, lfParametersExt, factorGroups, contingency, participatingElements,
                        rhsModifications, new DisabledNetwork(disabledBuses, disabledBranches), resultWriter);
            }
        }
    }

    private void buildRhsModificationsForContingenciesBreakingConnectivity(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, WoodburyEngine.ConnectivityDataResult connectivityDataResult,
                                                                           SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, List<ParticipatingElement> participatingElements,
                                                                           WoodburyEngineRhsModifications rhsModifications, SensitivityResultWriter resultWriter) {
        DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();

        // Loop on the different connectivity schemas among the post-contingency states
        for (WoodburyEngine.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityDataResult.connectivityAnalysisResults()) {
            Set<LfBus> disabledBuses = connectivityAnalysisResult.getDisabledBuses();

            // as we are processing contingencies with connectivity break, we have to reset active power flow of a hvdc line
            // if one bus of the line is lost.
            processHvdcLinesWithDisconnection(loadFlowContext, disabledBuses, connectivityAnalysisResult);

            // null and unused if slack bus is not distributed
            List<ParticipatingElement> participatingElementsForThisConnectivity = participatingElements;
            boolean rhsChanged = false; // true if the disabled buses change the slack distribution, or the GLSK
            if (lfParameters.isDistributedSlack()) {
                rhsChanged = participatingElementsForThisConnectivity.stream().anyMatch(element -> disabledBuses.contains(element.getLfBus()));
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
                DenseMatrix newInjectionRhsForThisConnectivity = getWoodburyInjectionVectors(loadFlowContext, factorGroups, participatingElementsForThisConnectivity);
                rhsModifications.getNewInjectionRhsForAConnectivity().put(connectivityAnalysisResult, newInjectionRhsForThisConnectivity);
            }

            DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, Collections.emptySet());
            // recompute the flow rhs
            double[] newFlowsRhs = calculatePreContingencyStates(loadFlowContext, participatingElementsForThisConnectivity, disabledNetwork);
            rhsModifications.getNewFlowRhsForAConnectivity().put(connectivityAnalysisResult, newFlowsRhs);

            // Build rhs modifications for each contingency bringing this connectivity schema
            buildRhsModificationsForContingencies(loadFlowContext, lfParametersExt, factorGroups, connectivityAnalysisResult.getContingencies(), participatingElementsForThisConnectivity, rhsModifications,
                    disabledBuses, connectivityAnalysisResult.getPartialDisabledBranches(),
                    connectivityDataResult.contingencyElementByBranch(), connectivityAnalysisResult.getElementsToReconnect(), resultWriter);
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

                // create Jacobin matrix either using calculated voltages from pre-contingency network or nominal voltages
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
                DenseMatrix injectionRhs = getWoodburyInjectionVectors(loadFlowContext, factorGroups, participatingElements);
                double[] flowsRhs = calculatePreContingencyStates(loadFlowContext, participatingElements, new DisabledNetwork());

                // compute states with +1 -1 to model the contingencies and run connectivity analysis
                WoodburyEngine.ConnectivityDataResult connectivityData = engine.runConnectivityData(loadFlowContext, contingencies);

                // storage of modifications that must be applied on rhs members, due to GLSK and/or slack bus participation
                WoodburyEngineRhsModifications woodburyEngineRhsModification = new WoodburyEngineRhsModifications();

                // compute rhs modifications for contingencies breaking connectivity
                buildRhsModificationsForContingenciesBreakingConnectivity(loadFlowContext, lfParametersExt, connectivityData, factorGroups, participatingElements, woodburyEngineRhsModification, resultWriter);

//                List<PropagatedContingency> nonBreakingConnectivityContingenciesWithGeneratorOrLoadLost = new ArrayList<>();
//                for (PropagatedContingency propagatedContingency : connectivityData.nonBreakingConnectivityContingencies()) {
//                    if (propagatedContingency.getGeneratorIdsToLose().isEmpty() && propagatedContingency.getLoadIdsToLoose().isEmpty()) {
//                        resultWriter.writeContingencyStatus(propagatedContingency.getIndex(), propagatedContingency.hasNoImpact() ? SensitivityAnalysisResult.Status.NO_IMPACT : SensitivityAnalysisResult.Status.SUCCESS);
//                    } else {
//                    nonBreakingConnectivityContingenciesWithGeneratorOrLoadLost.add(propagatedContingency);
//                    }
//                }
                // compute rhs modifications for contingencies with no connectivity break
                buildRhsModificationsForContingencies(loadFlowContext, lfParametersExt, factorGroups, connectivityData.nonBreakingConnectivityContingencies(), participatingElements, woodburyEngineRhsModification,
                        Collections.emptySet(), Collections.emptySet(), connectivityData.contingencyElementByBranch(), Collections.emptySet(), resultWriter);

                // compute the pre- and post-contingency states using Woodbury equality
                WoodburyEngineResult results = engine.run(loadFlowContext, flowsRhs, injectionRhs, woodburyEngineRhsModification, connectivityData, reportNode);

                // set base case/function reference values of the factors
                setFunctionReference(validLfFactors, results.getPreContingenciesFlowStates());
                setBaseCaseSensitivityValues(factorGroups, results.getPreContingenciesStates()); // use this state to compute the base sensitivity (without +1-1)

                // compute the sensibilities with Woodbury computed states (pre- and post- contingency)
                calculateSensitivityValues(results, validFactorHolder.getAllFactors(), contingencies, resultWriter);
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
