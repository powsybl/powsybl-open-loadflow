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
import com.powsybl.iidm.network.TwoWindingsTransformer;
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
import com.powsybl.sensitivity.factors.BranchFlowPerPSTAngle;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class DcSensitivityAnalysis extends AbstractSensitivityAnalysis {

    static class SensitivityVariableConfiguration {

        private final Set<String> busIds;

        private final Set<String> phaseTapChangerHolderIds;

        SensitivityVariableConfiguration(Set<String> busIds, Set<String> phaseTapChangerHolderIds) {
            this.busIds = Objects.requireNonNull(busIds);
            this.phaseTapChangerHolderIds = Objects.requireNonNull(phaseTapChangerHolderIds);
            if (busIds.isEmpty() && phaseTapChangerHolderIds.isEmpty()) {
                throw new IllegalArgumentException("Bus ID and phase tap changer holder ID list is empty");
            }
        }

        Set<String> getBusIds() {
            return busIds;
        }

        Set<String> getPhaseTapChangerHolderIds() {
            return phaseTapChangerHolderIds;
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
            return busIds.equals(that.busIds) && phaseTapChangerHolderIds.equals(that.phaseTapChangerHolderIds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(busIds);
        }
    }

    static class SensitivityFactorGroup {

        private final List<SensitivityFactor<?, ?>> factors = new ArrayList<>();

        private int index = -1;

        List<SensitivityFactor<?, ?>> getFactors() {
            return factors;
        }

        int getIndex() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }
    }

    public DcSensitivityAnalysis(MatrixFactory matrixFactory) {
        super(matrixFactory);
    }

    private List<SensitivityValue> calculateSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem,
                                                              Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig, DenseMatrix states) {
        List<SensitivityValue> sensitivityValues = new ArrayList<>();

        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityFactorGroup factorGroup = e.getValue();
            for (SensitivityFactor<?, ?> factor : factorGroup.getFactors()) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    LfBranch lfBranch = lfNetwork.getBranchById(injectionFactor.getFunction().getBranchId());
                    ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                    double value = Math.abs(p1.calculate(states, factorGroup.getIndex()) * PerUnit.SB);
                    sensitivityValues.add(new SensitivityValue(injectionFactor, value, 0, 0));
                }  else if (factor instanceof BranchFlowPerPSTAngle) {
                    BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                    LfBranch lfBranch = lfNetwork.getBranchById(pstAngleFactor.getFunction().getBranchId());
                    ClosedBranchSide1DcFlowEquationTerm p1 = equationSystem.getEquationTerm(SubjectType.BRANCH, lfBranch.getNum(), ClosedBranchSide1DcFlowEquationTerm.class);
                    double value = Math.abs(p1.calculate(states, factorGroup.getIndex()) * PerUnit.SB);
                    sensitivityValues.add(new SensitivityValue(pstAngleFactor, value, 0, 0));
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }
        }

        return sensitivityValues;
    }

    private DenseMatrix initTransposedRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig) {
        DenseMatrix transposedRhs = new DenseMatrix(equationSystem.getSortedEquationsToSolve().size(), factorsByVarConfig.size());
        for (Map.Entry<SensitivityVariableConfiguration, SensitivityFactorGroup> e : factorsByVarConfig.entrySet()) {
            SensitivityVariableConfiguration configuration = e.getKey();
            SensitivityFactorGroup factorGroup = e.getValue();
            for (String busId : configuration.getBusIds()) {
                LfBus lfBus = lfNetwork.getBusById(busId);
                if (lfBus.isSlack()) {
                    throw new PowsyblException("Cannot analyse sensitivity of slack bus");
                }
                Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
                transposedRhs.set(p.getColumn(), factorGroup.getIndex(), 1d / PerUnit.SB);
            }
            for (String phaseTapChangerHolderId : configuration.getPhaseTapChangerHolderIds()) {
                LfBranch lfBranch = lfNetwork.getBranchById(phaseTapChangerHolderId);
                Equation a1 = equationSystem.getEquation(lfBranch.getNum(), EquationType.BRANCH_ALPHA1).orElseThrow(IllegalStateException::new);
                transposedRhs.set(a1.getColumn(), factorGroup.getIndex(), Math.toRadians(1d));
            }
        }
        return transposedRhs;
    }

    private Map<SensitivityVariableConfiguration, SensitivityFactorGroup> indexFactorsByVariableConfig(Network network, List<SensitivityFactor> factors) {
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig = new LinkedHashMap<>(factors.size());

        // index factors by variable config
        for (SensitivityFactor<?, ?> factor : factors) {
            if (factor instanceof BranchFlowPerInjectionIncrease) {
                BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                Injection<?> injection = getInjection(network, injectionFactor.getVariable().getInjectionId());
                Bus bus = injection.getTerminal().getBusView().getBus();
                // skip disconnected injections
                if (bus != null) {
                    SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.singleton(bus.getId()), Collections.emptySet());
                    factorsByVarConfig.computeIfAbsent(varConfig, k -> new SensitivityFactorGroup())
                            .getFactors().add(injectionFactor);
                }
            } else if (factor instanceof BranchFlowPerPSTAngle) {
                BranchFlowPerPSTAngle pstAngleFactor = (BranchFlowPerPSTAngle) factor;
                String phaseTapChangerHolderId = pstAngleFactor.getVariable().getPhaseTapChangerHolderId();
                TwoWindingsTransformer twt = network.getTwoWindingsTransformer(phaseTapChangerHolderId);
                if (twt == null) {
                    throw new PowsyblException("Phase shifter '" + phaseTapChangerHolderId + "' not found");
                }
                SensitivityVariableConfiguration varConfig = new SensitivityVariableConfiguration(Collections.emptySet(), Collections.singleton(phaseTapChangerHolderId));
                factorsByVarConfig.computeIfAbsent(varConfig, k -> new SensitivityFactorGroup())
                        .getFactors().add(pstAngleFactor);
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }

        // assign an index to each factor group
        int index = 0;
        for (SensitivityFactorGroup factorGroup : factorsByVarConfig.values()) {
            factorGroup.setIndex(index++);
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
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, new VariableSet(), false, true, true);

        // index factors by variable configuration to compute minimal number of DC state
        Map<SensitivityVariableConfiguration, SensitivityFactorGroup> factorsByVarConfig
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
