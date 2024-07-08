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
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
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
import java.util.function.ObjDoubleConsumer;
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

    /**
     * Calculate the active power flows for pre-contingency or a post-contingency state and set the factor function reference.
     * The interesting disabled branches are only phase shifters.
     */
    private DenseMatrix calculateActivePowerFlows(DcLoadFlowContext loadFlowContext,
                                                  List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors, List<ParticipatingElement> participatingElements,
                                                  DisabledNetwork disabledNetwork,
                                                  ReportNode reportNode) {
        List<BusState> busStates = Collections.emptyList();
        DcLoadFlowParameters parameters = loadFlowContext.getParameters();
        if (parameters.isDistributedSlack()) {
            busStates = ElementState.save(participatingElements.stream()
                    .map(ParticipatingElement::getLfBus)
                    .collect(Collectors.toSet()), BusState::save);
        }

        double[] dx = runDcLoadFlow(loadFlowContext, disabledNetwork, reportNode);

        StateVector sv = new StateVector(dx);
        for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval(sv)); // pass explicitly the previously calculated state vector
        }

        if (parameters.isDistributedSlack()) {
            ElementState.restore(busStates);
        }

        return new DenseMatrix(dx.length, 1, dx);
    }

    /**
     * A simplified version of DcLoadFlowEngine that supports on the fly bus and branch disabling and that do not
     * update the state vector and the network at the end (because we don't need it to just evaluate a few equations)
     */
    public double[] runDcLoadFlow(DcLoadFlowContext loadFlowContext, DisabledNetwork disabledNetwork,
                                  ReportNode reportNode) {
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

        boolean succeeded = DcLoadFlowEngine.solve(targetVectorArray, loadFlowContext.getJacobianMatrix(), reportNode);
        if (!succeeded) {
            throw new PowsyblException("DC solver failed");
        }

        return targetVectorArray; // now contains dx
    }

    /**
     * Get the sensitivity value for pre-contingency state and calculate the sensitivity value for a post-contingency state if asked.
     * The sensitivity value is written in the SensitivityResultWriter.
     */
    private void createBranchSensitivityValue(LfSensitivityFactor<DcVariableType, DcEquationType> factor, DenseMatrix contingenciesStates,
                                              Collection<ComputedContingencyElement> contingencyElements,
                                              PropagatedContingency contingency, SensitivityResultWriter resultWriter,
                                              DisabledNetwork disabledNetwork) {
        Pair<Optional<Double>, Optional<Double>> predefinedResults = getPredefinedResults(factor, disabledNetwork, contingency);
        Optional<Double> sensitivityValuePredefinedResult = predefinedResults.getLeft();
        Optional<Double> functionPredefinedResults = predefinedResults.getRight();
        double sensitivityValue = sensitivityValuePredefinedResult.orElseGet(factor::getBaseSensitivityValue);
        double functionValue = functionPredefinedResults.orElseGet(factor::getFunctionReference);
        Derivable<DcVariableType> p1 = factor.getFunctionEquationTerm();

        if (!(functionPredefinedResults.isPresent() && sensitivityValuePredefinedResult.isPresent())) {
            for (ComputedContingencyElement contingencyElement : contingencyElements) {
                double contingencySensitivity = p1.calculateSensi(contingenciesStates, contingencyElement.getContingencyIndex());
                if (functionPredefinedResults.isEmpty()) {
                    functionValue += contingencyElement.getAlphaForFunctionReference() * contingencySensitivity;
                }
                if (sensitivityValuePredefinedResult.isEmpty()) {
                    sensitivityValue += contingencyElement.getAlphaForSensitivityValue() * contingencySensitivity;
                }
            }
        }

        functionValue = fixZeroFunctionReference(contingency, functionValue);

        double unscaledSensi = unscaleSensitivity(factor, sensitivityValue);
        if (!filterSensitivityValue(unscaledSensi, factor.getVariableType(), factor.getFunctionType(), parameters)) {
            resultWriter.writeSensitivityValue(factor.getIndex(), contingency != null ? contingency.getIndex() : -1, unscaledSensi, unscaleFunction(factor, functionValue));
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
        setBaseCaseSensitivityValues(factorGroups, factorStates); // use this state to compute the base sensitivity (without +1-1)
        return factorStates;
    }

    /**
     * Calculate sensitivity values for pre-contingency state or a post-contingency state using the pre-contingency sensitivity
     * value and some flow transfer factors (alphas).
     */
    private void calculateSensitivityValues(DcLoadFlowContext loadFlowContext, List<LfSensitivityFactor<DcVariableType, DcEquationType>> lfFactors, DenseMatrix factorStates,
                                            DenseMatrix contingenciesStates, DenseMatrix flowStates, Collection<ComputedContingencyElement> contingencyElements,
                                            PropagatedContingency contingency, SensitivityResultWriter resultWriter, DisabledNetwork disabledNetwork) {
        if (lfFactors.isEmpty()) {
            return;
        }

        setAlphas(loadFlowContext, contingencyElements, flowStates, contingenciesStates, 0, ComputedContingencyElement::setAlphaForFunctionReference);

        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION)
                .forEach(factor -> createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork));

        Map<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> factorsByGroup = lfFactors.stream()
                .filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID)
                .collect(Collectors.groupingBy(LfSensitivityFactor::getGroup, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<SensitivityFactorGroup<DcVariableType, DcEquationType>, List<LfSensitivityFactor<DcVariableType, DcEquationType>>> e : factorsByGroup.entrySet()) {
            SensitivityFactorGroup<DcVariableType, DcEquationType> factorGroup = e.getKey();
            List<LfSensitivityFactor<DcVariableType, DcEquationType>> factorsForThisGroup = e.getValue();
            setAlphas(loadFlowContext, contingencyElements, factorStates, contingenciesStates, factorGroup.getIndex(), ComputedContingencyElement::setAlphaForSensitivityValue);
            for (LfSensitivityFactor<DcVariableType, DcEquationType> factor : factorsForThisGroup) {
                createBranchSensitivityValue(factor, contingenciesStates, contingencyElements, contingency, resultWriter, disabledNetwork);
            }
        }
    }

    /**
     * Compute the flow transfer factors needed to calculate the post-contingency sensitivity values.
     */
    private static void setAlphas(DcLoadFlowContext loadFlowContext, Collection<ComputedContingencyElement> contingencyElements, DenseMatrix states,
                                  DenseMatrix contingenciesStates, int columnState, ObjDoubleConsumer<ComputedContingencyElement> setValue) {
        if (contingencyElements.size() == 1) {
            ComputedContingencyElement element = contingencyElements.iterator().next();
            LfBranch lfBranch = element.getLfBranch();
            ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
            // we solve a*alpha = b
            double a = 1d / calculatePower(loadFlowContext, lfBranch) - (contingenciesStates.get(p1.getPh1Var().getRow(), element.getContingencyIndex())
                    - contingenciesStates.get(p1.getPh2Var().getRow(), element.getContingencyIndex()));
            double b = states.get(p1.getPh1Var().getRow(), columnState) - states.get(p1.getPh2Var().getRow(), columnState);
            setValue.accept(element, b / a);
        } else {
            // FIXME: direct resolution if contingencyElements.size() == 2
            ComputedContingencyElement.setLocalIndexes(contingencyElements);
            DenseMatrix rhs = new DenseMatrix(contingencyElements.size(), 1);
            DenseMatrix matrix = new DenseMatrix(contingencyElements.size(), contingencyElements.size());
            for (ComputedContingencyElement element : contingencyElements) {
                LfBranch lfBranch = element.getLfBranch();
                ClosedBranchSide1DcFlowEquationTerm p1 = element.getLfBranchEquation();
                rhs.set(element.getLocalIndex(), 0, states.get(p1.getPh1Var().getRow(), columnState)
                        - states.get(p1.getPh2Var().getRow(), columnState)
                );
                for (ComputedContingencyElement element2 : contingencyElements) {
                    double value = 0d;
                    if (element.equals(element2)) {
                        value = 1d / calculatePower(loadFlowContext, lfBranch);
                    }
                    value = value - (contingenciesStates.get(p1.getPh1Var().getRow(), element2.getContingencyIndex())
                            - contingenciesStates.get(p1.getPh2Var().getRow(), element2.getContingencyIndex()));
                    matrix.set(element.getLocalIndex(), element2.getLocalIndex(), value);
                }
            }
            try (LUDecomposition lu = matrix.decomposeLU()) {
                lu.solve(rhs); // rhs now contains state matrix
            }
            contingencyElements.forEach(element -> setValue.accept(element, rhs.get(element.getLocalIndex(), 0)));
        }
    }

    private static double calculatePower(DcLoadFlowContext loadFlowContext, LfBranch lfBranch) {
        PiModel piModel = lfBranch.getPiModel();
        DcEquationSystemCreationParameters creationParameters = loadFlowContext.getParameters().getEquationSystemCreationParameters();
        return AbstractClosedBranchDcFlowEquationTerm.calculatePower(creationParameters.isUseTransformerRatio(), creationParameters.getDcApproximationType(), piModel);
    }

    /**
     * Calculate sensitivity values for a post-contingency state.
     * When a contingency involves the loss of a load or a generator, the slack distribution could be changed
     * or the sensitivity factors in case of GLSK.
     */
    private void calculateSensitivityValuesForAContingency(DcLoadFlowContext loadFlowContext, OpenLoadFlowParameters lfParametersExt, SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                              SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups, DenseMatrix factorState, DenseMatrix contingenciesStates, DenseMatrix flowStates,
                                                              PropagatedContingency contingency, Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                              Set<LfBus> disabledBuses, List<ParticipatingElement> participatingElements, Set<String> elementsToReconnect,
                                                              SensitivityResultWriter resultWriter, ReportNode reportNode, Set<LfBranch> partialDisabledBranches, boolean hasRhsChangedDueToConnectivityBreak) {
        List<LfSensitivityFactor<DcVariableType, DcEquationType>> factors = validFactorHolder.getFactorsForContingency(contingency.getContingency().getId());
        Collection<ComputedContingencyElement> contingencyElements = contingency.getBranchIdsToOpen().keySet().stream()
                .filter(element -> !elementsToReconnect.contains(element))
                .map(contingencyElementByBranch::get)
                .collect(Collectors.toList());

        var lfNetwork = loadFlowContext.getNetwork();
        Set<LfBranch> disabledBranches = contingency.getBranchIdsToOpen().keySet().stream().map(lfNetwork::getBranchById).collect(Collectors.toSet());
        disabledBranches.addAll(partialDisabledBranches);
        DisabledNetwork disabledNetwork = new DisabledNetwork(disabledBuses, disabledBranches);

        if (contingency.getGeneratorIdsToLose().isEmpty() && contingency.getLoadIdsToLoose().isEmpty()) {
            DenseMatrix newFlowStates = flowStates;
            DenseMatrix newFactorStates = factorState;

            if (!factors.isEmpty()) {
                Set<LfBranch> lostTransformers = contingency.getBranchIdsToOpen().keySet().stream()
                        .filter(element -> !elementsToReconnect.contains(element))
                        .map(contingencyElementByBranch::get)
                        .map(ComputedContingencyElement::getLfBranch)
                        .filter(LfBranch::hasPhaseControllerCapability)
                        .collect(Collectors.toSet());

                // if a phase tap changer is lost or if connectivity changed, recompute load flows
                if (!disabledBuses.isEmpty() || !lostTransformers.isEmpty()) {
                    DisabledNetwork disabledNetworkWithPstLost = new DisabledNetwork(disabledBuses, lostTransformers);
                    newFlowStates = calculateActivePowerFlows(loadFlowContext, factors, participatingElements, disabledNetworkWithPstLost, reportNode);
                }
            }

            // we need to recompute the factor states because the connectivity changed
            if (hasRhsChangedDueToConnectivityBreak) {
                newFactorStates = calculateFactorStates(loadFlowContext, factorGroups, participatingElements);
            }

            calculateSensitivityValues(loadFlowContext, factors, newFactorStates, contingenciesStates, newFlowStates, contingencyElements,
                    contingency, resultWriter, disabledNetwork);
            // write contingency status
            if (contingency.hasNoImpact()) {
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            } else {
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            }
        } else {
            // if we have a contingency including the loss of a DC line or a generator or a load
            // save base state for later restoration after each contingency
            DcLoadFlowParameters lfParameters = loadFlowContext.getParameters();
            NetworkState networkState = NetworkState.save(lfNetwork);
            LfContingency lfContingency = contingency.toLfContingency(lfNetwork).orElse(null);
            DenseMatrix newFactorStates = factorState;
            List<ParticipatingElement> newParticipatingElements = participatingElements;
            boolean participatingElementsChanged = false;
            boolean rhsChanged = false;
            if (lfContingency != null) {
                lfContingency.apply(lfParameters.getBalanceType());
                participatingElementsChanged = isDistributedSlackOnGenerators(lfParameters) && !contingency.getGeneratorIdsToLose().isEmpty()
                        || isDistributedSlackOnLoads(lfParameters) && !contingency.getLoadIdsToLoose().isEmpty();
                if (factorGroups.hasMultiVariables()) {
                    Set<LfBus> impactedBuses = lfContingency.getLoadAndGeneratorBuses();
                    rhsChanged = rescaleGlsk(factorGroups, impactedBuses);
                }
                if (participatingElementsChanged) {
                    if (isDistributedSlackOnGenerators(lfParameters)) {
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
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.SUCCESS);
            } else {
                // write contingency status
                resultWriter.writeContingencyStatus(contingency.getIndex(), SensitivityAnalysisResult.Status.NO_IMPACT);
            }

            // we need to recompute the factor states because the rhs has changed or
            if (participatingElementsChanged || rhsChanged || hasRhsChangedDueToConnectivityBreak) {
                newFactorStates = calculateFactorStates(loadFlowContext, factorGroups, newParticipatingElements);
            }

            DenseMatrix newFlowStates = calculateActivePowerFlows(loadFlowContext, factors,
                    newParticipatingElements, disabledNetwork, reportNode);

            calculateSensitivityValues(loadFlowContext, factors, newFactorStates, contingenciesStates, newFlowStates, contingencyElements,
                    contingency, resultWriter, disabledNetwork);

            networkState.restore();
            if (participatingElementsChanged || rhsChanged) {
                setBaseCaseSensitivityValues(factorGroups, factorState);
            }
        }
    }

    private void processContingenciesBreakingConnectivity(ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult, DcLoadFlowContext loadFlowContext,
                                                          LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                          SensitivityFactorHolder<DcVariableType, DcEquationType> validFactorHolder,
                                                          SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups,
                                                          List<ParticipatingElement> participatingElements,
                                                          Map<String, ComputedContingencyElement> contingencyElementByBranch,
                                                          DenseMatrix flowStates, DenseMatrix factorsStates, DenseMatrix contingenciesStates,
                                                          SensitivityResultWriter resultWriter,
                                                          ReportNode reportNode) {

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

        // we need to recompute the participating elements because the connectivity changed
        if (rhsChanged) {
            participatingElementsForThisConnectivity = lfParameters.isDistributedSlack()
                    ? getParticipatingElements(connectivityAnalysisResult.getSlackConnectedComponent(), lfParameters.getBalanceType(), lfParametersExt) // will also be used to recompute the loadflow
                    : Collections.emptyList();
        }

        calculateSensitivityValuesForAContingency(loadFlowContext, lfParametersExt,
                validFactorHolder, factorGroups, factorsStates, contingenciesStates, flowStates,
                contingency, contingencyElementByBranch, disabledBuses, participatingElementsForThisConnectivity,
                connectivityAnalysisResult.getElementsToReconnect(), resultWriter, reportNode, partialDisabledBranches, rhsChanged);

        if (rhsChanged) {
            setBaseCaseSensitivityValues(factorGroups, factorsStates); // we modified the rhs, we need to restore previous state
        }
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
                SensitivityFactorGroupList<DcVariableType, DcEquationType> factorGroups = createFactorGroups(validLfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution)
                List<ParticipatingElement> participatingElements = lfParameters.isDistributedSlack()
                        ? getParticipatingElements(lfNetwork.getBuses(), lfParameters.getBalanceType(), lfParametersExt)
                        : Collections.emptyList();

                // run DC load on pre-contingency network
                DenseMatrix flowStates = calculateActivePowerFlows(loadFlowContext, validLfFactors, participatingElements, new DisabledNetwork(), reportNode);

                // compute the pre-contingency sensitivity values
                DenseMatrix factorsStates = calculateFactorStates(loadFlowContext, factorGroups, participatingElements);

                // calculate sensitivity values for pre-contingency network
                calculateSensitivityValues(loadFlowContext, validFactorHolder.getFactorsForBaseNetwork(), factorsStates, null, flowStates,
                        Collections.emptySet(), null, resultWriter, new DisabledNetwork());

                // compute states with +1 -1 to model the contingencies and run connectivity analysis
                ConnectivityBreakAnalysis.ConnectivityBreakAnalysisResults connectivityBreakAnalysisResults = ConnectivityBreakAnalysis.run(loadFlowContext, validFactorHolder, contingencies, resultWriter);

                LOGGER.info("Processing contingencies with no connectivity break");

                // process contingencies with no connectivity break
                for (PropagatedContingency contingency : connectivityBreakAnalysisResults.nonBreakingConnectivityContingencies()) {
                    calculateSensitivityValuesForAContingency(loadFlowContext, lfParametersExt, validFactorHolder, factorGroups,
                            factorsStates, connectivityBreakAnalysisResults.contingenciesStates(), flowStates, contingency,
                            connectivityBreakAnalysisResults.contingencyElementByBranch(), Collections.emptySet(), participatingElements, Collections.emptySet(), resultWriter, reportNode, Collections.emptySet(), false);
                }

                LOGGER.info("Processing contingencies with connectivity break");

                // process contingencies with connectivity break
                for (ConnectivityBreakAnalysis.ConnectivityAnalysisResult connectivityAnalysisResult : connectivityBreakAnalysisResults.connectivityAnalysisResults()) {
                    processContingenciesBreakingConnectivity(connectivityAnalysisResult, loadFlowContext, lfParameters, lfParametersExt,
                            validFactorHolder, factorGroups, participatingElements, connectivityBreakAnalysisResults.contingencyElementByBranch(),
                            flowStates, factorsStates, connectivityBreakAnalysisResults.contingenciesStates(), resultWriter, reportNode);
                }
            }

            stopwatch.stop();
            LOGGER.info("DC sensitivity analysis done in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
