/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystem;
import com.powsybl.openloadflow.equations.*;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSensitivityAnalysis {

    private final MatrixFactory matrixFactory;

    public OpenSensitivityAnalysis(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    public void run(Network network, List<String> branchIds) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(branchIds);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork);

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

            // initialize equations with current state
            double[] x = equationSystem.createStateVector(new PreviousValueVoltageInitializer());

            equationSystem.updateEquations(x);

            JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);

            double[] targets = new double[equationSystem.getSortedEquationsToSolve().size()];
            EquationTerm p1 = (EquationTerm) branch.getP1();
            for (Variable variable : p1.getVariables()) {
                targets[variable.getColumn()] += p1.der(variable);
            }
            EquationTerm p2 = (EquationTerm) branch.getP2();
            for (Variable variable : p2.getVariables()) {
                targets[variable.getColumn()] += p2.der(variable);
            }
            System.out.println(equationSystem.getRowNames());
            System.out.println(Arrays.toString(targets));

            double[] dx = Arrays.copyOf(targets, targets.length);
            try (LUDecomposition lu = j.decomposeLU()) {
                lu.solve(dx);
                System.out.println(equationSystem.getColumnNames());
                System.out.println(Arrays.toString(dx));
            }

            equationSystem.updateEquations(dx);

            System.out.println(branch.getP1().eval());
            System.out.println(branch.getP2().eval());
        }
    }
}
