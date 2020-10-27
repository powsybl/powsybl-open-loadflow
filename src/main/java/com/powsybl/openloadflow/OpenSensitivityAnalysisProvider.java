/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide1ActiveFlowEquationTerm;
import com.powsybl.openloadflow.ac.equations.ClosedBranchSide2ActiveFlowEquationTerm;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide1DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.ClosedBranchSide2DcFlowEquationTerm;
import com.powsybl.openloadflow.dc.equations.DcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.*;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.tools.PowsyblCoreVersion;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * http://www.montefiore.ulg.ac.be/~vct/elec0029/lf.pdf
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

    private static EquationTerm getP1(EquationSystem equationSystem, LfBranch branch) {
        return equationSystem.getEquationTerms(SubjectType.BRANCH, branch.getNum())
                .stream()
                .filter(term -> term instanceof ClosedBranchSide1ActiveFlowEquationTerm || term instanceof ClosedBranchSide1DcFlowEquationTerm)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    private static EquationTerm getP2(EquationSystem equationSystem, LfBranch branch) {
        return equationSystem.getEquationTerms(SubjectType.BRANCH, branch.getNum())
                .stream()
                .filter(term -> term instanceof ClosedBranchSide2ActiveFlowEquationTerm || term instanceof ClosedBranchSide2DcFlowEquationTerm)
                .findFirst()
                .orElseThrow(IllegalStateException::new);
    }

    public void runAc(Network network, List<String> branchIds) {
        Objects.requireNonNull(branchIds);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork, variableSet);

        // initialize equations with current state
        double[] x = equationSystem.createStateVector(new PreviousValueVoltageInitializer());

        equationSystem.updateEquations(x);

        JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);

        for (String branchId : branchIds) {
            LfBranch branch = lfNetwork.getBranchById(branchId);
            if (branch == null) {
                throw new IllegalArgumentException("Branch '" + branchId + "' not found");
            }

            LfBus bus1 = branch.getBus1();
            LfBus bus2 = branch.getBus2();
            if (bus1 == null || bus2 == null) {
                continue;
            }

            double[] targets = new double[equationSystem.getSortedEquationsToSolve().size()];
            EquationTerm p1 = getP1(equationSystem, branch);
            for (Variable variable : p1.getVariables()) {
                targets[variable.getRow()] += p1.der(variable);
            }
            EquationTerm p2 = getP2(equationSystem, branch);
            for (Variable variable : p2.getVariables()) {
                targets[variable.getRow()] += p2.der(variable);
            }
//            System.out.println(equationSystem.getRowNames());
//            System.out.println(Arrays.toString(targets));

            double[] dx = Arrays.copyOf(targets, targets.length);
            try (LUDecomposition lu = j.decomposeLU()) {
                lu.solve(dx);
//                System.out.println(equationSystem.getColumnNames());
//                System.out.println(Arrays.toString(dx));
            }
            for (LfBus bus : lfNetwork.getBuses()) {
                Variable variable = variableSet.getVariable(bus.getNum(), VariableType.BUS_PHI);
                double s = dx[variable.getRow()];
                System.out.println(bus.getId() + ": " + s);
            }
        }
    }

    private static Generator getGenerator(Network network, String generatorId) {
        Generator generator = network.getGenerator(generatorId);
        if (generator == null) {
            throw new PowsyblException("Generator '" + generatorId + "' not found");
        }
        return generator;
    }

    public List<SensitivityValue> runDc(Network network, List<BranchFlowPerInjectionIncrease> injectionFactors,
                                        OpenLoadFlowParameters lfParametersExt, OpenSensitivityAnalysisParameters sensiParametersExt) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(injectionFactors);
        Objects.requireNonNull(lfParametersExt);
        Objects.requireNonNull(sensiParametersExt);

        List<SensitivityValue> sensitivityValues = new ArrayList<>();

        List<LfNetwork> lfNetworks = LfNetwork.load(network, lfParametersExt.getSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        if (sensiParametersExt.isUseBaseCaseVoltage() && sensiParametersExt.isRunLf()) {
            // run DC loadflow
            DcLoadFlowResult result = new DcLoadFlowEngine(lfNetwork, matrixFactory)
                    .run();
            if (!result.isOk()) {
                throw new PowsyblException("Initial DC loadflow diverged");
            }
        }

        VariableSet variableSet = new VariableSet();
        EquationSystem equationSystem = DcEquationSystem.create(lfNetwork, variableSet, false, true);

        // initialize state vector with base case voltages or nominal voltages
        VoltageInitializer voltageInitializer = sensiParametersExt.isUseBaseCaseVoltage() ? new PreviousValueVoltageInitializer()
                : new UniformValueVoltageInitializer();
        double[] x = equationSystem.createStateVector(voltageInitializer);

        // find list of bus id to to compute sensitivity
        Set<String> busIds = injectionFactors
                .stream()
                .map(injectionFactor -> getGenerator(network, injectionFactor.getVariable().getInjectionId()))
                .map(generator -> generator.getTerminal().getBusView().getBus())
                .filter(Objects::nonNull)
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        if (busIds.isEmpty()) {
            throw new PowsyblException("Empty PTDF region");
        }

        // initialize targets
        double[] targets = new double[equationSystem.getSortedEquationsToSolve().size()];
        for (String busId : busIds) {
            LfBus lfBus = lfNetwork.getBusById(busId);
            Equation p = equationSystem.getEquation(lfBus.getNum(), EquationType.BUS_P).orElseThrow(IllegalStateException::new);
            targets[p.getColumn()] = 1d / PerUnit.SB;
        }

        equationSystem.updateEquations(x);
        JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);
        try {
            double[] dx = Arrays.copyOf(targets, targets.length);

            LUDecomposition lu = j.decomposeLU();
            lu.solveTransposed(dx);

            equationSystem.updateEquations(dx);
        } finally {
            j.cleanLU();
        }

        for (BranchFlowPerInjectionIncrease injectionFactor :  injectionFactors) {
            LfBranch lfBranch = lfNetwork.getBranchById(injectionFactor.getFunction().getBranchId());
            EquationTerm p1 = getP1(equationSystem, lfBranch);
            double value = Math.abs(p1.eval() * PerUnit.SB);
            sensitivityValues.add(new SensitivityValue(injectionFactor, value, 0, 0));
        }

        return sensitivityValues;
    }

    @Override
    public CompletableFuture<SensitivityAnalysisResult> run(Network network, String workingStateId,
                                                            SensitivityFactorsProvider sensitivityFactorsProvider,
                                                            ContingenciesProvider contingenciesProvider,
                                                            SensitivityAnalysisParameters sensitivityAnalysisParameters,
                                                            ComputationManager computationManager) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            List<BranchFlowPerInjectionIncrease> injectionFactors = new ArrayList<>();
            for (SensitivityFactor factor : sensitivityFactorsProvider.getFactors(network)) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    injectionFactors.add(injectionFactor);
                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }

            LoadFlowParameters lfParameters = sensitivityAnalysisParameters.getLoadFlowParameters();
            OpenLoadFlowParameters lfParametersExt = lfParameters.getExtension(OpenLoadFlowParameters.class);
            if (lfParametersExt == null) {
                lfParametersExt = new OpenLoadFlowParameters();
            }
            OpenSensitivityAnalysisParameters sensiParametersExt = sensitivityAnalysisParameters.getExtension(OpenSensitivityAnalysisParameters.class);
            if (sensiParametersExt == null) {
                sensiParametersExt = new OpenSensitivityAnalysisParameters();
            }

            List<SensitivityValue> sensitivityValues;
            if (lfParameters.isDc()) {
                sensitivityValues = runDc(network, injectionFactors, lfParametersExt, sensiParametersExt);
            } else {
                sensitivityValues = Collections.emptyList();
            }

            boolean ok = true;
            Map<String, String> metrics = new HashMap<>();
            String logs = "";
            return new SensitivityAnalysisResult(ok, metrics, logs, sensitivityValues);
        });
    }
}
