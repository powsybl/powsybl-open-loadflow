/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.LfContingency;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFactorsProvider;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
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

    private List<SensitivityValue> calculateSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>(factorGroups.size());

        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor factor : factorGroup.getFactors()) {
                if (factor.getPredefinedResult() != null) {
                    sensitivityValues.add(new SensitivityValue(factor.getFactor(), factor.getPredefinedResult(), factor.getPredefinedResult(), Double.NaN));
                    continue;
                }

                if (!factor.getEquationTerm().isActive()) {
                    throw new PowsyblException("Found an inactive equation for a factor that has no predefined result");
                }
                double sensi = factor.getEquationTerm().calculateSensi(factorsState, factorGroup.getIndex());
                if (factor.getFunctionLfBranch().getId().equals(factorGroup.getId())) {
                    // add nabla_p eta, fr specific cases
                    // the only case currently: if we are computing the sensitivity of a phasetap change on itself
                    Variable phi1Var = factor.getEquationTerm().getVariables()
                        .stream()
                        .filter(var -> var.getNum() == factor.getFunctionLfBranch().getNum() && var.getType().equals(VariableType.BRANCH_ALPHA1))
                        .findAny()
                        .orElseThrow(() -> new PowsyblException("No alpha_1 variable on the function branch"));
                    sensi += Math.toRadians(factor.getEquationTerm().der(phi1Var));
                }
                sensitivityValues.add(new SensitivityValue(factor.getFactor(), sensi * PerUnit.SB, factor.getFunctionReference() * PerUnit.SB, Double.NaN));
            }
        }
        return sensitivityValues;
    }

    protected void setFunctionReferences(List<LfSensitivityFactor> factors) {
        for (LfSensitivityFactor factor : factors) {
            double functionReference;
            if (factor.getFactor().getFunction() instanceof BranchFlow) {
                functionReference = factor.getFunctionLfBranch().getP1().eval();
            } else if (factor.getFactor().getFunction() instanceof BranchIntensity) {
                functionReference = factor.getFunctionLfBranch().getI1().eval();
            } else {
                throw new PowsyblException("Function reference cannot be computed for function: " + factor.getFactor().getFunction().getClass().getSimpleName());
            }
            factor.setFunctionReference(functionReference);
        }
    }

    private List<SensitivityValue> getPostContingencySensitivityValues(List<LfSensitivityFactor> lfFactors,
                                                               LfContingency lfContingency, LfNetwork lfNetwork,
                                                               AcloadFlowEngine engine, List<SensitivityFactorGroup> factorGroups,
                                                               List<LfSensitivityFactor> additionalLfFactors,
                                                               List<SensitivityFactorGroup> additionalFactorGroups,
                                                               LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt
    ) {
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
        engine.run();

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
        List<SensitivityValue> sensitivityValues = computeResults(engine, lfNetwork, lfFactors, factorGroups, additionalLfFactors, additionalFactorGroups);
        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
        return sensitivityValues;
    }

    private List<SensitivityValue> computeResults(AcloadFlowEngine engine, LfNetwork lfNetwork,
                                List<LfSensitivityFactor> lfFactors, List<SensitivityFactorGroup> factorGroups,
                                List<LfSensitivityFactor> additionalLfFactors, List<SensitivityFactorGroup> additionalFactorGroups) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>(lfFactors.size() + additionalLfFactors.size());
        try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
            assert Integer.MAX_VALUE / (engine.getEquationSystem().getSortedEquationsToSolve().size() * Double.BYTES) > factorGroups.size();
            DenseMatrix factorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), factorGroups); // this is the rhs for the moment
            j.solveTransposed(factorsStates);
            setFunctionReferences(lfFactors);
            sensitivityValues.addAll(calculateSensitivityValues(factorGroups, factorsStates));

            assert Integer.MAX_VALUE / (engine.getEquationSystem().getSortedEquationsToSolve().size() * Double.BYTES) > additionalFactorGroups.size();
            DenseMatrix additionalFactorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), additionalFactorGroups); // this is the rhs for the moment
            j.solveTransposed(additionalFactorsStates);
            setFunctionReferences(additionalLfFactors);
            sensitivityValues.addAll(calculateSensitivityValues(additionalFactorGroups, additionalFactorsStates));
        }
        return sensitivityValues;
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, SensitivityFactorsProvider factorsProvider, List<PropagatedContingency> contingencies,
                                                                                     LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkParameters(lfParametersExt.getSlackBusSelector(), false, true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(), false));
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkLoadFlowParameters(lfParameters);

        List<SensitivityFactor> factors = factorsProvider.getCommonFactors(network);
        checkSensitivities(network, factors);
        List<SensitivityFactor> additionalFactors = factorsProvider.getAdditionalFactors(network);
        checkSensitivities(network, additionalFactors);
        Map<String, List<SensitivityFactor>> additionalFactorsPerContingency = new HashMap<>();
        for (PropagatedContingency contingency : contingencies) {
            List<SensitivityFactor> contingencyAdditionalFactors = factorsProvider.getAdditionalFactors(network, contingency.getContingency().getId());
            checkSensitivities(network, contingencyAdditionalFactors);
            additionalFactorsPerContingency.put(contingency.getContingency().getId(), contingencyAdditionalFactors);
        }

        Map<Contingency, Collection<String>> propagatedContingencyMap = contingencies.stream().collect(
            Collectors.toMap(PropagatedContingency::getContingency, contingency -> new HashSet<>(contingency.getBranchIdsToOpen()))
        );

        Set<String> branchesWithMeasuredCurrent = factors.stream()
            .filter(BranchIntensityPerPSTAngle.class::isInstance)
            .map(BranchIntensityPerPSTAngle.class::cast)
            .map(BranchIntensityPerPSTAngle::getFunction)
            .map(BranchIntensity::getBranchId)
            .collect(Collectors.toSet());
        // create AC engine
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters,
            lfParametersExt, true, true,
            branchesWithMeasuredCurrent);
        try (AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters)) {

            engine.run();

            List<LfSensitivityFactor> lfFactors = factors.stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork)).collect(Collectors.toList());
            List<LfSensitivityFactor> zeroFactors = lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.ZERO)).collect(Collectors.toList());
            warnSkippedFactors(lfFactors);
            lfFactors = lfFactors.stream().filter(factor -> factor.getStatus().equals(LfSensitivityFactor.Status.VALID)).collect(Collectors.toList());
            List<SensitivityValue> baseValues = new ArrayList<>(zeroFactors.size() + lfFactors.size());
            baseValues.addAll(zeroFactors.stream().map(AbstractSensitivityAnalysis::createZeroValue).collect(Collectors.toList()));

            // index factors by variable group to compute a minimal number of states
            List<SensitivityFactorGroup> factorGroups = createFactorGroups(network, lfFactors);

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
            computeInjectionFactors(slackParticipationByBus, factorGroups);
            List<LfSensitivityFactor> additionalLfFactors = additionalFactors.stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork)).collect(Collectors.toList());
            List<SensitivityFactorGroup> additionalFactorGroups = getAdditionalFactorGroups(additionalLfFactors, baseValues, network, null);
            computeInjectionFactors(slackParticipationByBus, additionalFactorGroups);

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
            baseValues.addAll(computeResults(engine, lfNetwork, lfFactors, factorGroups, additionalLfFactors, additionalFactorGroups));

            GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity(connectivityProvider);

            List<LfContingency> lfContingencies = LfContingency.createContingencies(contingencies, lfNetwork, connectivity, false);

            Map<LfBus, BusState> busStates = BusState.createBusStates(lfNetwork.getBuses());

            Map<String, List<SensitivityValue>> contingenciesValues = new HashMap<>();

            // Contingency not breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<SensitivityValue> contingencyValues = new ArrayList<>(zeroFactors.size() + lfFactors.size());
                additionalLfFactors = additionalFactorsPerContingency.get(lfContingency.getContingency().getId()).stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork)).collect(Collectors.toList());
                additionalFactorGroups = getAdditionalFactorGroups(additionalLfFactors, contingencyValues, network, null);
                computeInjectionFactors(slackParticipationByBus, additionalFactorGroups);
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));
                lfFactors.stream()
                    .filter(lfFactor -> lfContingency.getBranches().contains(lfFactor.getFunctionLfBranch()))
                    .forEach(lfFactor -> lfFactor.setPredefinedResult(0d));
                additionalLfFactors.stream()
                    .filter(lfFactor -> lfContingency.getBranches().contains(lfFactor.getFunctionLfBranch()))
                    .forEach(lfFactor -> lfFactor.setPredefinedResult(0d));
                contingencyValues.addAll(zeroFactors.stream().map(AbstractSensitivityAnalysis::createZeroValue).collect(Collectors.toList()));
                contingencyValues.addAll(getPostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, additionalLfFactors, additionalFactorGroups, lfParameters, lfParametersExt));
                contingenciesValues.put(lfContingency.getContingency().getId(), contingencyValues);
                BusState.restoreBusStates(busStates);
            }

            // Contingency breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> !lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                List<SensitivityValue> contingencyValues = new ArrayList<>(zeroFactors.size() + lfFactors.size());
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));
                additionalLfFactors = additionalFactorsPerContingency.get(lfContingency.getContingency().getId()).stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork)).collect(Collectors.toList());
                cutConnectivity(lfNetwork, connectivity, propagatedContingencyMap.get(lfContingency.getContingency()));
                Set<LfBus> nonConnectedBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
                Set<LfBus> slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
                slackConnectedComponent.removeAll(nonConnectedBuses);
                setPredefinedResults(lfFactors, slackConnectedComponent, connectivity); // check if factors are still in the main component
                setPredefinedResults(additionalLfFactors, slackConnectedComponent, connectivity); // check if factors are still in the main component

                additionalFactorGroups = getAdditionalFactorGroups(additionalLfFactors, contingencyValues, network, nonConnectedBuses);
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

                computeInjectionFactors(slackParticipationByBusForThisConnectivity, additionalFactorGroups);
                computeInjectionFactors(slackParticipationByBusForThisConnectivity, factorGroups);

                contingencyValues.addAll(zeroFactors.stream().map(AbstractSensitivityAnalysis::createZeroValue).collect(Collectors.toList()));
                contingencyValues.addAll(getPostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, additionalLfFactors, additionalFactorGroups, lfParameters, lfParametersExt));
                contingenciesValues.put(lfContingency.getContingency().getId(), contingencyValues);
                BusState.restoreBusStates(busStates);

                connectivity.reset();
            }

            return Pair.of(baseValues, contingenciesValues);
        }
    }
}
