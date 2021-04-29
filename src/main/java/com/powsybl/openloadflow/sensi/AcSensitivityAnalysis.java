/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.TransformerVoltageControlOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.LfContingency;
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
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory, Supplier<GraphDecrementalConnectivity<LfBus>> connectivityProvider) {
        super(matrixFactory, connectivityProvider);
    }

    private void calculateSensitivityValues(List<LfSensitivityFactor> lfFactors, List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState,
                                            String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter) {
        Set<LfSensitivityFactor> lfFactorsSet = new HashSet<>(lfFactors);
        lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.ZERO)).forEach(factor -> valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, 0, Double.NaN));
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor factor : factorGroup.getFactors()) {
                if (!lfFactorsSet.contains(factor)) {
                    continue;
                }
                if (factor.getPredefinedResult() != null) {
                    valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, factor.getPredefinedResult(), factor.getPredefinedResult());
                    continue;
                }

                if (!factor.getEquationTerm().isActive()) {
                    throw new PowsyblException("Found an inactive equation for a factor that has no predefined result");
                }
                double sensi = factor.getEquationTerm().calculateSensi(factorsState, factorGroup.getIndex());
                if (factor.getFunctionElement() instanceof LfBranch &&
                    factor instanceof SingleVariableLfSensitivityFactor &&
                    ((SingleVariableLfSensitivityFactor) factor).getVariableElement() instanceof LfBranch &&
                    ((SingleVariableLfSensitivityFactor) factor).getVariableElement().equals(factor.getFunctionElement())) {
                    // add nabla_p eta, fr specific cases
                    // the only case currently: if we are computing the sensitivity of a phasetap change on itself
                    Variable phi1Var = factor.getEquationTerm().getVariables()
                        .stream()
                        .filter(var -> var.getNum() == factor.getFunctionElement().getNum() && var.getType().equals(VariableType.BRANCH_ALPHA1))
                        .findAny()
                        .orElseThrow(() -> new PowsyblException("No alpha_1 variable on the function branch"));
                    sensi += Math.toRadians(factor.getEquationTerm().der(phi1Var));
                }

                if (SensitivityFunctionType.BUS_VOLTAGE.equals(factor.getFunctionType())) {
                    sensi *= ((LfBus) factor.getFunctionElement()).getNominalV();
                }
                valueWriter.write(factor.getContext(), contingencyId, contingencyIndex, sensi * PerUnit.SB, factor.getFunctionReference() * PerUnit.SB);
            }
        }
    }

    protected void setFunctionReferences(List<LfSensitivityFactor> factors) {
        for (LfSensitivityFactor factor : factors) {
            double functionRef = factor.getEquationTerm().eval();
            if (factor.getFunctionType() == SensitivityFunctionType.BUS_VOLTAGE) {
                factor.setFunctionReference(functionRef / PerUnit.SB);
            } else {
                factor.setFunctionReference(functionRef);
            }
        }
    }

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor> lfFactors, LfContingency lfContingency,
                                                           LfNetwork lfNetwork, AcloadFlowEngine engine, List<SensitivityFactorGroup> factorGroups,
                                                           Map<LfBus, Double> participationByBus,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           String contingencyId, int contingencyIndex, SensitivityValueWriter valueWriter,
                                                           Reporter reporter) {
        for (LfBus bus : lfContingency.getBuses()) {
            bus.setDisabled(true);
        }

        if (lfParameters.isDistributedSlack() && Math.abs(lfContingency.getActivePowerLoss()) > 0) {
            ActivePowerDistribution activePowerDistribution = ActivePowerDistribution.create(lfParameters.getBalanceType(), lfParametersExt.isLoadPowerFactorConstant());
            activePowerDistribution.run(lfNetwork, lfContingency.getActivePowerLoss());
        }

        List<Equation> deactivatedEquations = new ArrayList<>();
        List<EquationTerm> deactivatedEquationTerms = new ArrayList<>();

        LfContingency.deactivateEquations(lfContingency, engine.getEquationSystem(), deactivatedEquations, deactivatedEquationTerms);

        engine.getParameters().setVoltageInitializer(new PreviousValueVoltageInitializer());
        engine.run(reporter);

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
        try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
            // solve system
            DenseMatrix factorsStates = initFactorsRhs(engine.getEquationSystem(), factorGroups, participationByBus); // this is the rhs for the moment
            j.solveTransposed(factorsStates);
            setFunctionReferences(lfFactors);

            // calculate sensitivity values
            calculateSensitivityValues(lfFactors, factorGroups, factorsStates, contingencyId, contingencyIndex, valueWriter);
        }

        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
    }

    @Override
    public void checkContingencies(Network network, LfNetwork lfNetwork, List<PropagatedContingency> contingencies) {
        super.checkContingencies(network, lfNetwork, contingencies);

        for (PropagatedContingency contingency : contingencies) {
            if (!contingency.getHvdcIdsToOpen().isEmpty()) {
                throw new NotImplementedException("Contingencies on a DC line are not yet supported in AC mode.");
            }
        }
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
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(lfParametersExt.getSlackBusSelectionMode(), lfParametersExt.getSlackBusId());
        LfNetworkParameters lfNetworkParameters = new LfNetworkParameters(slackBusSelector, lfParametersExt.hasVoltageRemoteControl(),
                true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(), false);
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfNetworkParameters, reporter);
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(network, lfNetwork, contingencies);
        checkLoadFlowParameters(lfParameters);
        Map<Contingency, Collection<String>> propagatedContingencyMap = contingencies.stream().collect(
            Collectors.toMap(PropagatedContingency::getContingency, contingency -> new HashSet<>(contingency.getBranchIdsToOpen()))
        );

        Map<String, SensitivityVariableSet> variableSetsById = variableSets.stream().collect(Collectors.toMap(SensitivityVariableSet::getId, Function.identity()));
        SensitivityFactorHolder factorHolder = readAndCheckFactors(network, variableSetsById, factorReader, lfNetwork);
        List<LfSensitivityFactor> lfFactors = factorHolder.getAllFactors();
        LOGGER.info("Running AC sensitivity analysis with {} factors and {} contingencies",  lfFactors.size(), contingencies.size());

        Set<String> branchesWithMeasuredCurrent = lfFactors.stream()
                .filter(lfFactor -> lfFactor.getFunctionType() == SensitivityFunctionType.BRANCH_CURRENT)
                .map(lfFactor -> lfFactor.getFunctionElement().getId())
                .collect(Collectors.toSet());

        // create AC engine
        boolean hasTransformerBusTargetVoltage = hasTransformerBusTargetVoltage(factorHolder, network);
        if (hasTransformerBusTargetVoltage) {
            // if we have at least one bus target voltage linked to a ratio tap changer, we activate the transformer
            // voltage control for the AC load flow engine.
            lfParameters.setTransformerVoltageControlOn(true);
        }
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters,
            lfParametersExt, false, true,
            branchesWithMeasuredCurrent);
        try (AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters)) {

            engine.run(reporter);

            warnSkippedFactors(lfFactors);
            lfFactors = lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.VALID)).collect(Collectors.toList());

            // index factors by variable group to compute a minimal number of states
            List<SensitivityFactorGroup> factorGroups = createFactorGroups(lfFactors);

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
                for (OuterLoop outerLoop : engine.getParameters().getOuterLoops()) {
                    if (outerLoop instanceof TransformerVoltageControlOuterLoop) {
                        outerLoop.setActive(false);
                    }
                }
                for (LfBus bus : lfNetwork.getBuses()) {
                    if (bus.getDiscreteVoltageControl() != null && bus.getDiscreteVoltageControl().getMode().equals(DiscreteVoltageControl.Mode.OFF)) {
                        // switch on regulating transformers
                        bus.getDiscreteVoltageControl().setMode(DiscreteVoltageControl.Mode.VOLTAGE);
                    }
                }
                engine.setEquationSystem(null); //FIXME: should be an update only.
                engine.run(reporter);
            }

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
            try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
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

            List<LfContingency> lfContingencies = LfContingency.createContingencies(contingencies, lfNetwork, connectivity, false);

            Map<LfBus, BusState> busStates = BusState.createBusStates(lfNetwork.getBuses());

            // Contingency not breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<LfSensitivityFactor> contingencyFactors = factorHolder.getFactorsForContingency(lfContingency.getContingency().getId());
                contingencyFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));
                contingencyFactors.stream()
                    .filter(lfFactor -> lfFactor.getFunctionElement() instanceof LfBranch)
                    .filter(lfFactor ->  lfContingency.getBranches().contains((LfBranch) lfFactor.getFunctionElement()))
                    .forEach(lfFactor -> lfFactor.setPredefinedResult(0d));
                calculatePostContingencySensitivityValues(contingencyFactors, lfContingency, lfNetwork, engine, factorGroups, slackParticipationByBus, lfParameters,
                        lfParametersExt, lfContingency.getContingency().getId(), lfContingency.getIndex(), valueWriter, reporter);
                BusState.restoreBusStates(busStates);
            }

            // Contingency breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> !lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<LfSensitivityFactor> contingencyFactors = factorHolder.getFactorsForContingency(lfContingency.getContingency().getId());
                contingencyFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));

                cutConnectivity(lfNetwork, connectivity, propagatedContingencyMap.get(lfContingency.getContingency()));
                Set<LfBus> nonConnectedBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
                Set<LfBus> slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
                slackConnectedComponent.removeAll(nonConnectedBuses);
                setPredefinedResults(contingencyFactors, slackConnectedComponent, connectivity); // check if factors are still in the main component

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
                    lfParameters, lfParametersExt, lfContingency.getContingency().getId(), lfContingency.getIndex(), valueWriter, reporter);
                BusState.restoreBusStates(busStates);

                connectivity.reset();
            }
        }
    }
}
