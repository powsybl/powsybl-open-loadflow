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
import com.powsybl.math.matrix.LUDecomposition;
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

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    public AcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    /**
     * http://www.montefiore.ulg.ac.be/~vct/elec0029/lf.pdf
     */
    private SensitivityValue calculateSensitivityValue(Network network, LfNetwork lfNetwork, EquationSystem equationSystem,
                                                       JacobianMatrix j, String branchId, String injectionId, SensitivityFactor<?, ?> factor) {
        LfBranch branch = lfNetwork.getBranchById(branchId);
        if (branch == null) {
            throw new IllegalArgumentException("Branch '" + branchId + "' not found");
        }

        // skip open branches
        LfBus bus1 = branch.getBus1();
        LfBus bus2 = branch.getBus2();
        if (bus1 == null || bus2 == null) {
            return null;
        }

        Injection<?> injection = getInjection(network, injectionId);
        Bus bus = injection.getTerminal().getBusView().getBus();

        // skip disconnected injections
        if (bus == null) {
            return null;
        }

        LfBus lfBus = lfNetwork.getBusById(bus.getId());
        if (lfBus.isSlack()) {
            throw new PowsyblException("Cannot analyse sensitivity of slack bus");
        }

        double[] rhs = new double[equationSystem.getSortedEquationsToSolve().size()];
        // compute sensitivity regarding side with positive active power flow
        EquationTerm p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide1ActiveFlowEquationTerm.class);
        if (p.eval() < 0) {
            p = equationSystem.getEquationTerm(SubjectType.BRANCH, branch.getNum(), ClosedBranchSide2ActiveFlowEquationTerm.class);
        }
        for (Variable variable : p.getVariables()) {
            rhs[variable.getRow()] = p.der(variable);
        }

        try (LUDecomposition lu = j.decomposeLU()) {
            lu.solve(rhs);
        }

        Equation injEq = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
        double value = rhs[injEq.getColumn()];
        return new SensitivityValue(factor, value, Double.NaN, Double.NaN);
    }

    public List<SensitivityValue> analyse(Network network, List<SensitivityFactor> factors, OpenLoadFlowParameters lfParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);

        List<SensitivityValue> sensitivityValues = new ArrayList<>(factors.size());

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        // create AC equation system
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, new VariableSet());

        // initialize equations with current state
        PreviousValueVoltageInitializer voltageInitializer = new PreviousValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionIncrease = (BranchFlowPerInjectionIncrease) factor;

                String branchId = injectionIncrease.getFunction().getBranchId();
                String injectionId = injectionIncrease.getVariable().getInjectionId();

                SensitivityValue sensitivityValue = calculateSensitivityValue(network, lfNetwork, equationSystem, j, branchId, injectionId, factor);
                if (sensitivityValue != null) {
                    sensitivityValues.add(sensitivityValue);
                }
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }

        return sensitivityValues;
    }
}
