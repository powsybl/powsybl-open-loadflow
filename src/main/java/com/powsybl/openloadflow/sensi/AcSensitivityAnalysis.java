/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
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

    private List<SensitivityFactor> validateFactors(Network network, List<SensitivityFactor> factors, LfNetwork lfNetwork) {
        return factors.stream().filter(factor -> {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionIncrease = (BranchFlowPerInjectionIncrease) factor;

                String branchId = injectionIncrease.getFunction().getBranchId();
                String injectionId = injectionIncrease.getVariable().getInjectionId();

                LfBranch branch = getLfBranch(lfNetwork, branchId);

                // skip open branches
                LfBus bus1 = branch.getBus1();
                LfBus bus2 = branch.getBus2();
                if (bus1 == null || bus2 == null) {
                    return false;
                }

                Injection<?> injection = getInjection(network, injectionId);
                Bus bus = injection.getTerminal().getBusView().getBus();

                // skip disconnected injections
                if (bus == null) {
                    return false;
                }

                LfBus lfBus = lfNetwork.getBusById(bus.getId());
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot analyse sensitivity of slack bus");
                }
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
            return true;
        })
        .collect(Collectors.toList());
    }

    private DenseMatrix initRhs(List<SensitivityFactor> factors, Network network, LfNetwork lfNetwork, EquationSystem equationSystem) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factors.size());

        for (int index = 0; index < factors.size(); index++) {
            SensitivityFactor<?, ?> factor = factors.get(index);

            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionIncrease = (BranchFlowPerInjectionIncrease) factor;
                LfBus lfBus = getInjectionLfBus(network, lfNetwork, injectionIncrease);
                if (lfBus.isSlack()) {
                    // cannot inject on slack atm (will change with compensation)
                    // this is because slack is Theta/V, so we do not have any P equation
                    continue;
                }
                Equation pEquation = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow();

                rhs.set(pEquation.getColumn(), index, 1);
            }
        }

        return rhs;
    }

    private List<SensitivityValue> calculateSensitivityValues(Network network, List<SensitivityFactor> factors,
                                                              LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              DenseMatrix states) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>(factors.size());

        for (int index = 0; index < factors.size(); index++) {
            SensitivityFactor<?, ?> factor = factors.get(index);
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionIncrease = (BranchFlowPerInjectionIncrease) factor;
                LfBranch branch = lfNetwork.getBranchById(injectionIncrease.getFunction().getBranchId());
                ClosedBranchSide1ActiveFlowEquationTerm eq = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1ActiveFlowEquationTerm.class);
                sensitivityValues.add(new SensitivityValue(factor, eq.calculate(states, index), Double.NaN, Double.NaN));
            }
        }

        return sensitivityValues;
    }

    /**
     * https://people.montefiore.uliege.be/vct/elec0029/lf.pdf
     */
    public Pair<List<SensitivityValue>, Map<String, List<SensitivityValue>>> analyse(Network network, List<SensitivityFactor> factors, LoadFlowParameters lfParameters,
                                          OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParameters);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        // filter invalid factors
        List<SensitivityFactor> validFactors = validateFactors(network, factors, lfNetwork);

        // create AC equation system
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, new VariableSet());

        // create jacobian matrix from current network state
        VoltageInitializer voltageInitializer = new PreviousValueVoltageInitializer();

        // we make the assumption that we ran a loadflow before, and thus this jacobian is the right one
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);
        // ((DenseMatrix) j.getMatrix()).toJamaMatrix().inverse().transpose().print(1, 6);

        // otherwise, defining the rhs matrix will result in integer overflow
        assert Integer.MAX_VALUE / (equationSystem.getSortedEquationsToSolve().size() * Double.BYTES) > validFactors.size();
        // initialize right hand side from valid factors
        DenseMatrix rhs = initRhs(validFactors, network, lfNetwork, equationSystem);

        // solve system
        DenseMatrix states = solveTransposed(rhs, j);
        // calculate sensitivity values
        return Pair.of(calculateSensitivityValues(network, factors, lfNetwork, equationSystem, states), Collections.emptyMap());
    }
}
