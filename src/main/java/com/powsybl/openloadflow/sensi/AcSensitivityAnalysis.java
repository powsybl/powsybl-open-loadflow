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
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.util.ActivePowerDistribution;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.BusState;
import com.powsybl.openloadflow.util.LfContingency;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Gael Macherel <gael.macherel at artelys.com>
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private List<SensitivityValue> calculateSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>(factorGroups.size());

        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm> factor : factorGroup.getFactors()) {
                if (factor.getPredefinedResult() != null) {
                    sensitivityValues.add(new SensitivityValue(factor.getFactor(), factor.getPredefinedResult(), factor.getPredefinedResult(), Double.NaN));
                    continue;
                }

                if (!factor.getEquationTerm().isActive()) {
                    throw new PowsyblException("Found an inactive equation for a factor that has no predefined result");
                }
                double sensi = factor.getEquationTerm().calculateDer(factorsState, factorGroup.getIndex());
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

    protected void setReferenceActivePowerFlows(List<LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm>> factors) {
        for (LfSensitivityFactor factor : factors) {
            factor.setFunctionReference(factor.getFunctionLfBranch().getP1());
        }
    }

    private List<SensitivityValue> getPostContingencySensitivityValues(List<LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm>> lfFactors,
                                                               LfContingency lfContingency, LfNetwork lfNetwork,
                                                               AcloadFlowEngine engine, List<SensitivityFactorGroup> factorGroups,
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
        List<SensitivityValue> sensitivityValues = new ArrayList<>();
        try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
            // solve system
            DenseMatrix factorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), factorGroups); // this is the rhs for the moment
            j.solveTransposed(factorsStates);
            setReferenceActivePowerFlows(lfFactors);

            // calculate sensitivity values
            sensitivityValues = calculateSensitivityValues(factorGroups, factorsStates);
        }

        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
        return sensitivityValues;
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<PropagatedContingency> contingencies, LoadFlowParameters lfParameters,
                                          OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkParameters(lfParametersExt.getSlackBusSelector(), false, true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(), false));
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkSensitivities(network, lfNetwork, factors);
        checkLoadFlowParameters(lfParameters);
        Map<Contingency, Collection<String>> propagatedContingencyMap = contingencies.stream().collect(
            Collectors.toMap(PropagatedContingency::getContingency, contingency -> new HashSet<>(contingency.getBranchIdsToOpen()))
        );
        // create AC engine
        AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters, lfParametersExt, true, true);
        try (AcloadFlowEngine engine = new AcloadFlowEngine(lfNetwork, acParameters)) {

            engine.run();

            List<LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm>> lfFactors = factors.stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork, engine.getEquationSystem(), ClosedBranchSide1ActiveFlowEquationTerm.class)).collect(Collectors.toList());

            // index factors by variable group to compute a minimal number of states
            List<SensitivityFactorGroup> factorGroups = createFactorGroups(network, lfFactors);
            if (factorGroups.isEmpty()) {
                return Pair.of(Collections.emptyList(), Collections.emptyMap());
            }

            // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
            // buses that contain elements participating to slack distribution

            Map<String, Double> slackParticipationByBus;
            if (lfParameters.isDistributedSlack()) {
                List<ParticipatingElement> participatingElements = getParticipatingElements(lfNetwork.getBuses(), lfParameters, lfParametersExt);
                slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                    element -> element.getLfBus().getId(),
                    element -> -element.getFactor(),
                    Double::sum
                ));
            } else {
                slackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
            }
            computeInjectionFactors(slackParticipationByBus, factorGroups);

            List<SensitivityValue> baseValues;

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
            try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
                // otherwise, defining the rhs matrix will result in integer overflow
                assert Integer.MAX_VALUE / (engine.getEquationSystem().getSortedEquationsToSolve().size() * Double.BYTES) > factorGroups.size();
                // initialize right hand side from valid factors
                DenseMatrix factorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), factorGroups); // this is the rhs for the moment

                // solve system
                j.solveTransposed(factorsStates);

                // calculate sensitivity values
                setReferenceActivePowerFlows(lfFactors);
                baseValues = calculateSensitivityValues(factorGroups, factorsStates);
            }

            GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity();

            List<LfContingency> lfContingencies = LfContingency.createContingencies(contingencies, lfNetwork, connectivity, false);

            Map<LfBus, BusState> busStates = BusState.createBusStates(lfNetwork.getBuses());

            Map<String, List<SensitivityValue>> contingenciesValues = new HashMap<>();

            // Contingency not breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));
                lfFactors.stream()
                    .filter(lfFactor -> lfContingency.getBranches().contains(lfFactor.getFunctionLfBranch()))
                    .forEach(lfFactor -> lfFactor.setPredefinedResult(0d));
                contingenciesValues.put(lfContingency.getContingency().getId(),
                    getPostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, lfParameters, lfParametersExt));
                BusState.restoreBusStates(busStates);
            }

            // Contingency breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> !lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));

                cutConnectivity(lfNetwork, connectivity, propagatedContingencyMap.get(lfContingency.getContingency()));
                int mainComponent = connectivity.getComponentNumber(lfNetwork.getSlackBus());
                Set<LfBus> nonConnectedBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
                Collection<LfBus> slackConnectedComponent = new ArrayList<>(lfNetwork.getBuses());
                slackConnectedComponent.removeAll(nonConnectedBuses);
                setPredefinedResults(lfFactors, connectivity, mainComponent); // check if factors are still in the main component

                rescaleGlsk(factorGroups, nonConnectedBuses);

                // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
                // buses that contain elements participating to slack distribution
                Map<String, Double> slackParticipationByBusForThisConnectivity;

                if (lfParameters.isDistributedSlack()) {
                    List<ParticipatingElement> participatingElementsForThisConnectivity = getParticipatingElements(
                        slackConnectedComponent, lfParameters, lfParametersExt); // will also be used to recompute the loadflow
                    slackParticipationByBusForThisConnectivity = participatingElementsForThisConnectivity.stream().collect(Collectors.toMap(
                        element -> element.getLfBus().getId(),
                        element -> -element.getFactor(),
                        Double::sum
                    ));
                } else {
                    slackParticipationByBusForThisConnectivity = Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
                }

                computeInjectionFactors(slackParticipationByBusForThisConnectivity, factorGroups);

                contingenciesValues.put(lfContingency.getContingency().getId(),
                    getPostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, lfParameters, lfParametersExt));
                BusState.restoreBusStates(busStates);

                connectivity.reset();
            }

            return Pair.of(baseValues, contingenciesValues);
        }
    }
}
