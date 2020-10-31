/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.PerUnit;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.tools.PowsyblCoreVersion;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysisProvider implements SensitivityAnalysisProvider {

    private static final String NAME = "OpenSensitivityAnalysis";

    private final MatrixFactory matrixFactory;

    public OpenSensitivityAnalysisProvider(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return new PowsyblCoreVersion().getMavenProjectVersion();
    }

    private static Injection<?> getInjection(Network network, String injectionId) {
        Injection<?> injection = network.getGenerator(injectionId);
        if (injection == null) {
            injection = network.getLoad(injectionId);
            if (injection == null) {
                throw new PowsyblException("Injection '" + injectionId + "' not found");
            }
        }
        return injection;
    }

    private JacobianMatrix createJacobianMatrix(EquationSystem equationSystem, VoltageInitializer voltageInitializer) {
        double[] x = equationSystem.createStateVector(voltageInitializer);
        equationSystem.updateEquations(x);
        return JacobianMatrix.create(equationSystem, matrixFactory);
    }

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

    private List<SensitivityValue> calculateDcSensitivityValues(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig, DenseMatrix states) {
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

    private DenseMatrix solve(DenseMatrix transposedRhs, JacobianMatrix j) {
        try {
            LUDecomposition lu = j.decomposeLU();
            lu.solveTransposed(transposedRhs);
        } finally {
            j.cleanLU();
        }
        return transposedRhs; // rhs now contains state matrix
    }

    private DenseMatrix initDcTransposedRhs(LfNetwork lfNetwork, EquationSystem equationSystem, Map<SensitivityVariableConfiguration, List<SensitivityFactor<?, ?>>> factorsByVarConfig) {
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

    public List<SensitivityValue> runDc(Network network, List<SensitivityFactor> factors, OpenLoadFlowParameters lfParametersExt,
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
        DenseMatrix transposedRhs = initDcTransposedRhs(lfNetwork, equationSystem, factorsByVarConfig);

        // create jacobian matrix
        // initialize state vector with base case voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                                                                                          : new UniformValueVoltageInitializer();
        JacobianMatrix j = createJacobianMatrix(equationSystem, voltageInitializer);

        // solve system
        DenseMatrix states = solve(transposedRhs, j);

        // calculate sensitivity values
        return calculateDcSensitivityValues(lfNetwork, equationSystem, factorsByVarConfig, states);
    }

    /**
     * http://www.montefiore.ulg.ac.be/~vct/elec0029/lf.pdf
     */
    private SensitivityValue calculateAcSensitivityValue(Network network, LfNetwork lfNetwork, EquationSystem equationSystem,
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

    public List<SensitivityValue> runAc(Network network, List<SensitivityFactor> factors, OpenLoadFlowParameters lfParametersExt) {
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

                SensitivityValue sensitivityValue = calculateAcSensitivityValue(network, lfNetwork, equationSystem, j, branchId, injectionId, factor);
                if (sensitivityValue != null) {
                    sensitivityValues.add(sensitivityValue);
                }
            } else {
                throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
            }
        }

        return sensitivityValues;
    }

    private static OpenSensitivityAnalysisParameters getSensitivityAnalysisParametersExtension(SensitivityAnalysisParameters sensitivityAnalysisParameters) {
        OpenSensitivityAnalysisParameters sensiParametersExt = sensitivityAnalysisParameters.getExtension(OpenSensitivityAnalysisParameters.class);
        if (sensiParametersExt == null) {
            sensiParametersExt = new OpenSensitivityAnalysisParameters();
        }
        return sensiParametersExt;
    }

    private static OpenLoadFlowParameters getLoadFlowParametersExtension(LoadFlowParameters lfParameters) {
        OpenLoadFlowParameters lfParametersExt = lfParameters.getExtension(OpenLoadFlowParameters.class);
        if (lfParametersExt == null) {
            lfParametersExt = new OpenLoadFlowParameters();
        }
        return lfParametersExt;
    }

    @Override
    public CompletableFuture<SensitivityAnalysisResult> run(Network network, String workingStateId,
                                                            SensitivityFactorsProvider sensitivityFactorsProvider,
                                                            ContingenciesProvider contingenciesProvider,
                                                            SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                                            ComputationManager computationManager) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            List<SensitivityFactor> factors = sensitivityFactorsProvider.getFactors(network);

            LoadFlowParameters lfParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
            OpenLoadFlowParameters lfParametersExt = getLoadFlowParametersExtension(lfParameters);
            OpenSensitivityAnalysisParameters sensiParametersExt = getSensitivityAnalysisParametersExtension(sensitivityAnalysisParameters);

            List<SensitivityValue> sensitivityValues;
            if (lfParameters.isDc()) {
                sensitivityValues = runDc(network, factors, lfParametersExt, sensiParametersExt);
            } else {
                sensitivityValues = runAc(network, factors, lfParametersExt);
            }

            boolean ok = true;
            Map<String, String> metrics = new HashMap<>();
            String logs = "";
            return new SensitivityAnalysisResult(ok, metrics, logs, sensitivityValues);
        });
    }
}
