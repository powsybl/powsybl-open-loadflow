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
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static class SensitivityVariableConfiguration {

        private final Set<String> busIds;

        public SensitivityVariableConfiguration(Set<String> busIds) {
            this.busIds = Objects.requireNonNull(busIds);
            if (busIds.isEmpty()) {
                throw new IllegalArgumentException("Bus ID list is empty");
            }
        }

        public Set<String> getBusIds() {
            return busIds;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SensitivityVariableConfiguration that = (SensitivityVariableConfiguration) o;
            return busIds.equals(that.busIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(busIds);
        }
    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig, DenseMatrix states) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>();

        int column = 0;
        for (Map.Entry<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> e : factorsByVarConfig.entrySet()) {
            for (SensitivityFactor<?, ?> factor : e.getValue()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    LfBranch lfBranch = lfNetwork.getBranchById(injectionFactor.getFunction().getBranchId());
                    ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                    double value = Math.abs(p1.calculate(states, column) * PerUnit.SB);
                    sensitivityValues.add(new SensitivityValue(injectionFactor, value, 0, 0));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }
            column++;
        }

        return sensitivityValues;
    }

    private DenseMatrix initTransposedRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig) {
        DenseMatrix transposedRhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size());
        int row = 0;
        for (Map.Entry<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> e : factorsByVarConfig.entrySet()) {
            SensitivityVariableConfiguration configuration = e.getKey();
            for (String busId : configuration.getBusIds()) {
                LfBus lfBus = lfNetwork.getBusById(busId);
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot analyse sensitivity of slack bus");
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                transposedRhs.set(p.getColumn(), row, 1d / PerUnit.SB);
            }
            row++;
        }
        return transposedRhs;
    }

    private Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> indexFactorsByVariableConfig(Network network, List<SensitivityFactor> factors) {
        Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig = new LinkedHashMap<>(factors.size());
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                if (bus != null) {
                    SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.singleton(bus.getId()));
                    factorsByVarConfig.computeIfAbsent(varConfig, k -> new ArrayList<>())
                            .add(injectionFactor);
                }
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }
        return factorsByVarConfig;
    }

    public List<SensitivityValue> analyse(Network network, List<SensitivityFactor> factors, OpenLoadFlowParameters lfParametersExt,
                                          OpenSensitivityAnalysisParameters sensiParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(factors);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(sensiParametersExt);

        // create LF network (we only manage main connected component)
        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        // create DC equation system
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(), false, true);

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig
                = indexFactorsByVariableConfig(network, factors);
        if (factorsByVarConfig.isEmpty()) {
            return Collections.emptyList();
        }

        // initialize right hand side
        DenseMatrix transposedRhs = initTransposedRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // initialize state vector with base case voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();

        // create jacobian matrix
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solve(transposedRhs, j);

        // calculate sensitivity values
        return calculateSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states);
    }
}
