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
import com.powsybl.sensitivity.factors.BranchIntensityPerPSTAngle;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;

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

    private void calculateSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState,
                                            String contingencyId, SensitivityValueWriter writer) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor factor : factorGroup.getFactors()) {
                if (factor.getPredefinedResult() != null) {
                    writer.write(factor.getContext(), contingencyId, factor.getPredefinedResult(), factor.getPredefinedResult());
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
                writer.write(factor.getContext(), contingencyId, sensi * PerUnit.SB, factor.getFunctionReference() * PerUnit.SB);
            }
        }
    }

    protected void setFunctionReferences(List<LfSensitivityFactor> factors) {
        for (LfSensitivityFactor factor : factors) {
            double functionReference = factor.getEquationTerm().eval();
            factor.setFunctionReference(functionReference);
        }
    }

    private void calculatePostContingencySensitivityValues(List<LfSensitivityFactor> lfFactors, LfContingency lfContingency,
                                                           LfNetwork lfNetwork, AcloadFlowEngine engine, List<SensitivityFactorGroup> factorGroups,
                                                           LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt,
                                                           String contingencyId, SensitivityValueWriter writer) {
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
        try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
            // solve system
            DenseMatrix factorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), factorGroups); // this is the rhs for the moment
            j.solveTransposed(factorsStates);
            setFunctionReferences(lfFactors);

            // calculate sensitivity values
            calculateSensitivityValues(factorGroups, factorsStates, contingencyId, writer);
        }

        LfContingency.reactivateEquations(deactivatedEquations, deactivatedEquationTerms);
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     */
    public void analyse(Network network, List<SensitivityFactor> factors, List<PropagatedContingency> contingencies,
                        LoadFlowParameters lfParameters, OpenLoadFlowParameters lfParametersExt, SensitivityValueWriter writer) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, new LfNetworkParameters(lfParametersExt.getSlackBusSelector(), false, true, lfParameters.isTwtSplitShuntAdmittance(), false, lfParametersExt.getPlausibleActivePowerLimit(), false));
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkSensitivities(network, factors);
        checkLoadFlowParameters(lfParameters);
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
            zeroFactors.forEach(lfFactor -> writer.write(lfFactor.getContext(), null, 0, Double.NaN));

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
            computeInjectionFactors(slackParticipationByBus, factorGroups);

            // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
            try (JacobianMatrix j = createJacobianMatrix(engine.getEquationSystem(), new PreviousValueVoltageInitializer())) {
                // otherwise, defining the rhs matrix will result in integer overflow
                assert Integer.MAX_VALUE / (engine.getEquationSystem().getSortedEquationsToSolve().size() * Double.BYTES) > factorGroups.size();
                // initialize right hand side from valid factors
                DenseMatrix factorsStates = initFactorsRhs(lfNetwork, engine.getEquationSystem(), factorGroups); // this is the rhs for the moment

                // solve system
                j.solveTransposed(factorsStates);

                // calculate sensitivity values
                setFunctionReferences(lfFactors);
                calculateSensitivityValues(factorGroups, factorsStates, null, writer);
            }

            GraphDecrementalConnectivity<LfBus> connectivity = lfNetwork.createDecrementalConnectivity(connectivityProvider);

            List<LfContingency> lfContingencies = LfContingency.createContingencies(contingencies, lfNetwork, connectivity, false);

            Map<LfBus, BusState> busStates = BusState.createBusStates(lfNetwork.getBuses());

            // Contingency not breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));
                lfFactors.stream()
                    .filter(lfFactor -> lfContingency.getBranches().contains(lfFactor.getFunctionLfBranch()))
                    .forEach(lfFactor -> lfFactor.setPredefinedResult(0d));
                zeroFactors.forEach(lfFactor -> writer.write(lfFactor.getContext(), lfContingency.getContingency().getId(), 0, Double.NaN));
                calculatePostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, lfParameters,
                        lfParametersExt, lfContingency.getContingency().getId(), writer);
                BusState.restoreBusStates(busStates);
            }

            // Contingency breaking connectivity
            for (LfContingency lfContingency : lfContingencies.stream().filter(lfContingency -> !lfContingency.getBuses().isEmpty()).collect(Collectors.toSet())) {
                lfFactors.forEach(lfFactor -> lfFactor.setPredefinedResult(null));

                cutConnectivity(lfNetwork, connectivity, propagatedContingencyMap.get(lfContingency.getContingency()));
                Set<LfBus> nonConnectedBuses = connectivity.getNonConnectedVertices(lfNetwork.getSlackBus());
                Set<LfBus> slackConnectedComponent = new HashSet<>(lfNetwork.getBuses());
                slackConnectedComponent.removeAll(nonConnectedBuses);
                setPredefinedResults(lfFactors, slackConnectedComponent, connectivity); // check if factors are still in the main component

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

                computeInjectionFactors(slackParticipationByBusForThisConnectivity, factorGroups);

                zeroFactors.forEach(lfFactor -> writer.write(lfFactor.getContext(), lfContingency.getContingency().getId(), 0, Double.NaN));
                calculatePostContingencySensitivityValues(lfFactors, lfContingency, lfNetwork, engine, factorGroups, lfParameters, lfParametersExt,
                        lfContingency.getContingency().getId(), writer);
                BusState.restoreBusStates(busStates);

                connectivity.reset();
            }
        }
    }
}
