/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.openloadflow.network.util.ParticipatingElement;
import com.powsybl.openloadflow.util.PropagatedContingency;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private LfBranch getLfBranch(LfNetwork lfNetwork, String branchId) {
        LfBranch branch = lfNetwork.getBranchById(branchId);
        if (branch == null) {
            throw new PowsyblException("Branch '" + branchId + "' not found");
        }
        return branch;
    }

    protected void setBaseCaseSensitivityValues(List<SensitivityFactorGroup> factorGroups, DenseMatrix factorsState) {
        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm> factor : factorGroup.getFactors()) {
                double sensi = factor.getEquationTerm().calculate(factorsState, factorGroup.getIndex());
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
                factor.setBaseCaseSensitivityValue(sensi);
            }
        }
    }

    private List<SensitivityValue> calculateSensitivityValues(Network network, List<SensitivityFactorGroup> factorGroups,
                                                              LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              DenseMatrix states) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>(factorGroups.size());

        for (SensitivityFactorGroup factorGroup : factorGroups) {
            for (LfSensitivityFactor<?> factor : factorGroup.getFactors()) {
                sensitivityValues.add(new SensitivityValue(factor.getFactor(), factor.getBaseSensitivityValue() * PerUnit.SB, factor.getFunctionReference(), Double.NaN));
            }
        }
        return sensitivityValues;
    }

    protected void setReferenceActivePowerFlows(LfNetwork network, EquationSystem equationSystem,
                                                       List<LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm>> factors, LoadFlowParameters lfParameters) {
        for (LfSensitivityFactor factor : factors) {
            factor.setFunctionReference(factor.getFunctionLfBranch().getP1() * PerUnit.SB);
        }
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf / Equation 32 is transposed
     * WARNING: This function assumes that a loadflow has been run before
     */
    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, List<PropagatedContingency> contingencies, LoadFlowParameters lfParameters,
                                          OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);
        checkContingencies(lfNetwork, contingencies);
        checkSensitivities(network, lfNetwork, factors);
        checkLoadFlowParameters(lfParameters);
        // create AC equation system
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, new VariableSet(), new AcEquationSystemCreationParameters(false, false, false, true));

        List<LfSensitivityFactor<ClosedBranchSide1ActiveFlowEquationTerm>> lfFactors = factors.stream().map(factor -> LfSensitivityFactor.create(factor, network, lfNetwork, equationSystem, ClosedBranchSide1ActiveFlowEquationTerm.class)).collect(Collectors.toList());

        // index factors by variable group to compute a minimal number of states
        List<SensitivityFactorGroup> factorGroups = createFactorGroups(network, lfFactors);
        if (factorGroups.isEmpty()) {
            return Pair.of(Collections.emptyList(), Collections.emptyMap());
        }

        // compute the participation for each injection factor (+1 on the injection and then -participation factor on all
        // buses that contain elements participating to slack distribution
        List<ParticipatingElement> participatingElements = null;
        Map<String, Double> slackParticipationByBus;
        if (lfParameters.isDistributedSlack()) {
            participatingElements = getParticipatingElements(lfNetwork, lfParameters);
            slackParticipationByBus = participatingElements.stream().collect(Collectors.toMap(
                element -> element.getLfBus().getId(),
                element -> -element.getFactor(),
                Double::sum
            ));
        } else {
            slackParticipationByBus = Collections.singletonMap(lfNetwork.getSlackBus().getId(), -1d);
        }
        computeInjectionFactors(slackParticipationByBus, factorGroups);

        // create jacobian matrix from current network state
        VoltageInitializer voltageInitializer = new PreviousValueVoltageInitializer();

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);
        // ((DenseMatrix) j.getMatrix()).toJamaMatrix().inverse().transpose().print(1, 6);

        // otherwise, defining the rhs matrix will result in integer overflow
        assert Integer.MAX_VALUE / (equationSystem.getSortedEquationsToSolve().size() * Double.BYTES) > factorGroups.size();
        // initialize right hand side from valid factors
        DenseMatrix factorsStates = initFactorsRhs(lfNetwork, equationSystem, factorGroups); // this is the rhs for the moment

        // solve system
        solveTransposed(factorsStates, j);
        setReferenceActivePowerFlows(lfNetwork, equationSystem, lfFactors, lfParameters);
        setBaseCaseSensitivityValues(factorGroups, factorsStates);
        // calculate sensitivity values
        return Pair.of(calculateSensitivityValues(network, factorGroups, lfNetwork, equationSystem, factorsStates), Collections.emptyMap());
    }
}
