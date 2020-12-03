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
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private DenseMatrix initRhs(List<SensitivityFactor> factors, LfNetwork lfNetwork, EquationSystem equationSystem) {
        DenseMatrix rhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factors.size());

        for (int index = 0; index < factors.size(); index++) {
            SensitivityFactor<?, ?> factor = factors.get(index);

            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionIncrease = (BranchFlowPerInjectionIncrease) factor;

                LfBranch branch = lfNetwork.getBranchById(injectionIncrease.getFunction().getBranchId());

                // compute sensitivity regarding side with positive active power flow
                EquationTerm p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1ActiveFlowEquationTerm.class);
                if (p.eval() < 0) {
                    p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide2ActiveFlowEquationTerm.class);
                }
                for (Variable variable : p.getVariables()) {
                    rhs.set(variable.getRow(), index, p.der(variable));
                }
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

                String injectionId = injectionIncrease.getVariable().getInjectionId();
                Injection<?> injection = getInjection(network, injectionId);
                Bus bus = injection.getTerminal().getBusView().getBus();
                LfBus lfBus = lfNetwork.getBusById(bus.getId());

                Equation injEq = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                double value = states.get(injEq.getColumn(), index);
                sensitivityValues.add(new SensitivityValue(factor, value, Double.NaN, Double.NaN));
            }
        }

        return sensitivityValues;
    }

    /**
     * http://www.montefiore.ulg.ac.be/~vct/elec0029/lf.pdf
     */
    public List<SensitivityValue> analyse(Network network, List<SensitivityFactor> factors, OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        // filter invalid factors
        List<SensitivityFactor> validFactors = validateFactors(network, factors, lfNetwork);

        // create AC equation system
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, new VariableSet());

        // create jacobian matrix from current network state
        PreviousValueVoltageInitializer voltageInitializer = new PreviousValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // initialize right hand side from valid factors
        DenseMatrix rhs = initRhs(validFactors, lfNetwork, equationSystem);

        // solve system
        DenseMatrix states = solve(rhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(network, factors, lfNetwork, equationSystem, states);
    }
}
