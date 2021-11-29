/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.ac.nr.NewtonRaphson;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.DiscreteVoltageControl.Mode;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.util.PropagatedContingency;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis<AcVariableType, AcEquationType> {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(matrixFactory, connectivityProvider);
    }

    private void calculateSensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, List<SensitivityFactorGroup<AcVariableType, AcEquationType>> factorGroups, DenseMatrix factorsState,
                                            String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter) {
        Set<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactorsSet = new HashSet<>(lfFactors);
        // ZERO status is for factors where variable element is in the main connected component and reference element is not.
        // Therefore, the sensitivity is known to value 0, but the reference cannot be known and is set to NaN.
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.ZERO).forEach(factor -> valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, 0, Double.NaN));
        // VALID_ONLY_FOR_FUNCTION status is for factors where variable element is not in the main connected component but reference element is.
        // Therefore, the sensitivity is known to value 0 and the reference value can be computed.
        lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION).forEach(factor -> valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, 0, unscaleFunction(factor, factor.getFunctionReference())));

        for (SensitivityFactorGroup<AcVariableType, AcEquationType> factorGroup : factorGroups) {
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
                    if (factor.getFunctionElement() instanceof LfBranch &&
                            factor instanceof SingleVariableLfSensitivityFactor &&
                            ((SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType>) factor).getVariableElement() instanceof LfBranch &&
                            ((SingleVariableLfSensitivityFactor<AcVariableType, AcEquationType>) factor).getVariableElement().equals(factor.getFunctionElement())) {
                        // add nabla_p eta, fr specific cases
                        // the only case currently: if we are computing the sensitivity of a phasetap change on itself
                        Variable<AcVariableType> phi1Var = factor.getFunctionEquationTerm().getVariables()
                                .stream()
                                .filter(var -> var.getNum() == factor.getFunctionElement().getNum() && var.getType().equals(AcVariableType.BRANCH_ALPHA1))
                                .findAny()
                                .orElseThrow(() -> new PowsyblException("No alpha_1 variable on the function branch"));
                        sensi += Math.toRadians(factor.getFunctionEquationTerm().der(phi1Var));
                    }
                }
                if (factor.getFunctionPredefinedResult() != null) {
                    ref = factor.getFunctionPredefinedResult();
                } else {
                    ref = factor.getFunctionReference();
                }

                valueWriter.write(factor.getContext(), contingencyId, contingencyIndex,
                                  unscaleSensitivity(factor, sensi), unscaleFunction(factor, ref));
            }
        }
    }

    protected void setFunctionReferences(List<LfSensitivityFactor<AcVariableType, AcEquationType>> factors) {
        for (LfSensitivityFactor<AcVariableType, AcEquationType> factor : factors) {
            factor.setFunctionReference(factor.getFunctionEquationTerm().eval());
        }
    }

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors, LfContingency lfContingency,
                                                           LfNetwork lfNetwork, AcloadFlowEngine engine, List<SensitivityFactorGroup<AcVariableType, AcEquationType>> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter,
                                                           Reporter reporter, boolean hasTransformerBusTargetVoltage) {
        for (LfBus bus : lfContingency.getBuses()) {
            bus.setDisabled(true);
        }

        if (lfParameters.isDistributedSlack() && Math.abs(lfContingency.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(), lfParametersExt.isLoadPowerFactorConstant());
            activePowerDistribution.run(lfNetwork, lfContingency.getActivePowerLoss());
        }

        List<Equation<AcVariableType, AcEquationType>> deactivatedEquations = new ArrayList<>();
        List<EquationTerm<AcVariableType, AcEquationType>> deactivatedEquationTerms = new ArrayList<>();

        EquationUtil.deactivateEquations(lfContingency.getBranches(), lfContingency.getBuses(), engine.getEquationSystem(), deactivatedEquations, deactivatedEquationTerms);

        engine.getParameters().getNewtonRaphsonParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        engine.run(reporter);

        // if we have at least one bus target voltage linked to a ratio tap changer, we have to rebuild the AC equation
        // system obtained just before the transformer steps rounding.
        if (hasTransformerBusTargetVoltage) {
            for (LfBus bus : lfNetwork.getBuses()) {
                // switch on regulating transformers
                bus.getDiscreteVoltageControl().filter(dvc -> dvc.getMode() == Mode.OFF).ifPresent(dvc -> dvc.setMode(Mode.VOLTAGE));
            }
        }

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
        try (JacobianMatrix<AcVariableType, AcEquationType> j = createJacobianMatrix(lfNetwork, engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
            // solve system
            DenseMatrix factorsStates = initFactorsRhs(engine.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
            j.solveTransposed(factorsStates);
            setFunctionReferences(lfFactors);

            // calculate sensitivity values
            calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyId, contingencyIndex, valueWriter);
        }

        EquationUtil.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
    }

    @Override
    public void checkContingencies(LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        super.checkContingencies(lfNetwork, contingencies);

        for (PropagatedContingency contingency : contingencies) {
            if (!contingency.getHvdcIdsToOpen().isEmpty()) {
                throw new NotImplementedException("Contingencies on a DC line are not yet supported in AC mode.");
            }
        }
    }

    private JacobianMatrix<AcVariableType, AcEquationType> createJacobianMatrix(LfNetwork network, EquationSystem<AcVariableType, AcEquationType> equationSystem, VoltageInitializer voltageInitializer) {
        double[] x = NewtonRaphson.createStateVector(network, equationSystem, voltageInitializer);
        equationSystem.updateEquations(x);
        return new JacobianMatrix<>(equationSystem, matrixFactory);
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    public void analyse(Network network, List<PropagatedContingency> contingencies, List<SensitivityVariableSet> variableSets,
                        LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, SensitivityFactorReader factorReader,
                        SensitivityValueWriter valueWriter, Reporter reporter) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(contingencies);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(factorReader);
        Objects.requireNonNull(valueWriter);

        // create LF network (we only manage main connected component)
        boolean hasTransformerBusTargetVoltage = hasTransformerBusTargetVoltage(factorReader, network);
        if (hasTransformerBusTargetVoltage) {
            // if we have at least one bus target voltage linked to a ratio tap changer, we activate the transformer
            // voltage control for the AC load flow engine.
            lfParameters.setTransformerVoltageControlOn(true);
        }
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(), lfParametersExt.getSlackBusesIds());
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters(slackBusSelector, lfParametersExt.hasVoltageRemoteControl(),
                true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(),
                false, true, lfParameters.getCountriesToBalance(),
                lfParameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                lfParameters.isPhaseShifterRegulationOn(), lfParameters.isTransformerVoltageControlOn(),
                lfParametersExt.isVoltagePerReactivePowerControl(), lfParametersExt.hasReactivePowerRemoteControl());
        List<LfNetwork> lfNetworks = Networks.load(network, lfNetworkParameters, reporter);
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkLoadFlowParameters(lfParameters);
        Map<String, Collection<String>> propagatedContingencyMap = contingencies.stream().collect(
            Collectors.toMap(contingency -> contingency.getContingency().getId(), contingency -> new HashSet<>(contingency.getBranchIdsToOpen()))
        );

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder<AcVariableType, AcEquationType> factorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork);
        List<LfSensitivityFactor<AcVariableType, AcEquationType>> lfFactors = factorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors and {} contingencies",  lfFactors.size(), contingencies.size());

        Set<String> branchesWithMeasuredCurrent = lfFactors.stream()
                .filter(lfFactor -> lfFactor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT)
                .map(lfFactor -> lfFactor.getFunctionElement().getId())
                .collect(Collectors.toSet());

        // create AC engine
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters,
            lfParametersExt, false, true,
            branchesWithMeasuredCurrent);
        try (AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters)) {

            engine.run(reporter);

            writeSkippedFactors(lfFactors, valueWriter);

            // next we only work with valid and skip only variable factors
            lfFactors = lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID || factor.getStatus() == LfSensitivityFactor.Status.VALID_ONLY_FOR_FUNCTION).collect(Collectors.toList());

            // index factors by variable group to compute a minimal number of states
            List<SensitivityFactorGroup<AcVariableType, AcEquationType>> factorGroups = createFactorGroups(lfFactors.stream().filter(factor -> factor.getStatus() == LfSensitivityFactor.Status.VALID).collect(Collectors.toList()));

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution

            Map<LfBus, Double> slackParticipationByBus;
            if (lfParameters.isDistributedSlack()) {
                List<ParticipatingElement> participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters, lfParametersExt);
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
            if (hasTransformerBusTargetVoltage) {
                for (LfBus bus : lfNetwork.getBuses()) {
                    // switch on regulating transformers
                    bus.getDiscreteVoltageControl().filter(dvc -> dvc.getMode() == Mode.OFF).ifPresent(dvc -> dvc.setMode(Mode.VOLTAGE));
                }
            }

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
            try (JacobianMatrix<AcVariableType, AcEquationType> j = createJacobianMatrix(lfNetwork, engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
                // otherwise, defining the rhs matrix will result in integer overflow
                assert Integer.MAX_VALUE / (engine.getEquationSystem().getSortedEquationsToSolve().size() * Double.BYTES) > factorGroups.size();
                // initialize right hand side from valid factors
                DenseMatrix factorsStates = initFactorsRhs(engine.getEquationSystem(), factorGroups, slackParticipationByBus); // this is the rhs for the moment

                // solve system
                j.solveTransposed(factorsStates);

                // calculate sensitivity values
                setFunctionReferences(lfFactors);
                calculateSensitivityValues(factorHolder.getFactorsForBaseNetwork(), factorGroups, factorsStates, null, -1, valueWriter);
            }

            GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity(connectivityProvider);

            List<LfContingency> lfContingencies = contingencies.stream()
                    .flatMap(contingency -> LfContingency.create(contingency, lfNetwork, connectivity, false).stream())
                    .collect(Collectors.toList());

            List<BusState> busStates = BusState.save(lfNetwork.getBuses());

            // Contingency not breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = factorHolder.getFactorsForContingency(lfContingency.getId());
                contingencyFactors.forEach(lfFactor -> {
                    lfFactor.setSensitivityValuePredefinedResult(null);
                    lfFactor.setFunctionPredefinedResult(null);
                });
                contingencyFactors.stream()
                        .filter(lfFactor -> lfFactor.getFunctionElement() instanceof LfBranch)
                        .filter(lfFactor ->  lfContingency.getBranches().contains(lfFactor.getFunctionElement()))
                        .forEach(lfFactor ->  {
                            lfFactor.setSensitivityValuePredefinedResult(0d);
                            lfFactor.setFunctionPredefinedResult(0d);
                        });
                calculatePostContingencySensitivityValues(contingencyFactors, lfContingency, lfNetwork, engine, factorGroups, slackParticipationByBus, lfParameters,
                        lfParametersExt, lfContingency.getId(), lfContingency.getIndex(), valueWriter, reporter, hasTransformerBusTargetVoltage);
                BusState.restore(busStates);
            }

            // Contingency breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> !lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<LfSensitivityFactor<AcVariableType, AcEquationType>> contingencyFactors = factorHolder.getFactorsForContingency(lfContingency.getId());
                contingencyFactors.forEach(lfFactor -> {
                    lfFactor.setSensitivityValuePredefinedResult(null);
                    lfFactor.setFunctionPredefinedResult(null);
                });

                cutConnectivity(lfNetwork, connectivity, propagatedContingencyMap.get(lfContingency.getId()));
                Set<LfBus> nonConnectedBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
                Set<LfBus> slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
                slackConnectedComponent.removeAll(nonConnectedBuses);
                setPredefinedResults(contingencyFactors, slackConnectedComponent, propagatedContingencyMap.get(lfContingency.getId())); // check if factors are still in the main component

                rescaleGlsk(factorGroups, nonConnectedBuses);

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution
                Map<LfBus, Double> slackParticipationByBusForThisConnectivity;

                if (lfParameters.isDistributedSlack()) {
                    List<ParticipatingElement> participatingElementsForThisConnectivity = getParticipatingElements(
                        slackConnectedComponent, lfParameters, lfParametersExt); // will also be used to recompute the loadflow
                    slackParticipationByBusForThisConnectivity = participatingElementsForThisConnectivity.stream().collect(Collectors.toMap(
                        ParticipatingElement::getLfBus,
                        element -> -element.getFactor(),
                        Double::sum
                    ));
                } else {
                    slackParticipationByBusForThisConnectivity = Collections.singletonMap(lfNetwork.getSlackBus(), -1d);
                }

                calculatePostContingencySensitivityValues(contingencyFactors, lfContingency, lfNetwork, engine, factorGroups, slackParticipationByBusForThisConnectivity,
                    lfParameters, lfParametersExt, lfContingency.getId(), lfContingency.getIndex(), valueWriter, reporter, hasTransformerBusTargetVoltage);
                BusState.restore(busStates);

                connectivity.reset();
            }
        }
    }
}
