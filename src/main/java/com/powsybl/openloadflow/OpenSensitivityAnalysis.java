/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.BranchFlowPerInjectionIncrease;
import com.powsybl.tools.PowsyblCoreVersion;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * http://www.montefiore.ulg.ac.be/~vct/elec0029/lf.pdf
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysis implements SensitivityComputation {

    private static final String NAME = "OpenSensitivityAnalysis";

    private final Network network;

    private final MatrixFactory matrixFactory;

    public OpenSensitivityAnalysis(Network network, MatrixFactory matrixFactory) {
        this.network = Objects.requireNonNull(network);
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

    public void run(List<String> branchIds) {
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
            EquationTerm p1 = (EquationTerm) branch.getP1();
            for (Variable variable : p1.getVariables()) {
                targets[variable.getColumn()] += p1.der(variable);
            }
            EquationTerm p2 = (EquationTerm) branch.getP2();
            for (Variable variable : p2.getVariables()) {
                targets[variable.getColumn()] += p2.der(variable);
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
                double s = dx[variable.getColumn()];
                System.out.println(bus.getId() + ": " + s);
            }
        }
    }

    @Override
    public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider factorsProvider, String workingStateId,
                                                                SensitivityComputationParameters sensiParameters) {
        return CompletableFuture.supplyAsync(() -> {
            network.getVariantManager().setWorkingVariant(workingStateId);

            List<SensitivityFactor> factors = factorsProvider.getFactors(network);
            for (SensitivityFactor factor : factors) {
                if (factor instanceof BranchFlowPerInjectionIncrease) {
                    BranchFlowPerInjectionIncrease injectionFactor = (BranchFlowPerInjectionIncrease) factor;
                    String branchId = factor.getFunction().getId();
                    String injectionId = factor.getVariable().getId();

                } else {
                    throw new UnsupportedOperationException("Factor type '" + factor.getClass().getSimpleName() + "' not yet supported");
                }
            }

            boolean ok = true;
            Map<String, String> metrics = new HashMap<>();
            String logs = "";
            List<SensitivityValue> sensitivityValues = new ArrayList<>();
            return new SensitivityComputationResults(ok, metrics, logs, sensitivityValues);
        });
    }

    @Override
    public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider factorsProvider, ContingenciesProvider contingenciesProvider,
                                                                String workingStateId, SensitivityComputationParameters sensiParameters) {
        throw new UnsupportedOperationException("TODO");
    }
}
