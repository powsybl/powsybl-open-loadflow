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
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.PreviousValueVoltageInitializer;
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


    public void run(Network network) {
        Objects.requireNonNull(network);

        List<LfNetwork> lfNetworks = LfNetwork.load(network, new MostMeshedSlackBusSelector());
        LfNetwork lfNetwork = lfNetworks.get(0);

        EquationSystem equationSystem = AcEquationSystem.create(lfNetwork);

        // initialize equations with current state
        double[] x = equationSystem.createStateVector(new PreviousValueVoltageInitializer());
        equationSystem.updateEquations(x);

        double[] targets = new double[equationSystem.getSortedEquationsToSolve().size()];
        targets[0] = 1;

        double[] dx = Arrays.copyOf(targets, targets.length);
        JacobianMatrix j = JacobianMatrix.create(equationSystem, matrixFactory);
        try (LUDecomposition lu = j.decomposeLU()) {
            lu.solve(dx);
            System.out.println(Arrays.toString(dx));
        }

        equationSystem.updateEquations(dx);
    }
}
