/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.math.matrix.DenseMatrix;
import com.powsybl.math.matrix.LUDecomposition;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractSensitivityAnalysis {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractSensitivityAnalysis.class);

    protected final MatrixFactory matrixFactory;

    private final Provider<GraphDecrementalConnectivity<LfBus>> connectivityProvider = EvenShiloachGraphDecrementalConnectivity::new;

    protected AbstractSensitivityAnalysis(MatrixFactory matrixFactory) {
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    protected static Injection<?> getInjection(Network network, String injectionId) {
        return getInjection(network, injectionId, true);
    }

    protected static Injection<?> getInjection(Network network, String injectionId, boolean failIfAbsent) {
        Injection<?> injection = network.getGenerator(injectionId);
        if (injection == null) {
            injection = network.getLoad(injectionId);
            if (injection == null) {
                if (failIfAbsent) {
                    throw new PowsyblException("Injection '" + injectionId + "' not found");
                } else {
                    return null;
                }
            }
        }
        return injection;
    }

    protected JacobianMatrix createJacobianMatrix(EquationSystem equationSystem, VoltageInitializer voltageInitializer) {
        double[] x = equationSystem.createStateVector(voltageInitializer);
        equationSystem.updateEquations(x);
        return JacobianMatrix.create(equationSystem, matrixFactory);
    }

    protected DenseMatrix solve(DenseMatrix rhs, JacobianMatrix j) {
        try {
            LUDecomposition lu = j.decomposeLU();
            lu.solve(rhs);
        } finally {
            j.cleanLU();
        }
        return rhs; // rhs now contains state matrix
    }

    protected DenseMatrix solveTransposed(DenseMatrix rhs, JacobianMatrix j) {
        try {
            LUDecomposition lu = j.decomposeLU();
            lu.solveTransposed(rhs);
        } finally {
            j.cleanLU();
        }
        return rhs; // rhs now contains state matrix
    }

    // todo: this is a copy/paste from OpenSecurityAnalysis, find a way to refactor and not duplicate code
    protected GraphDecrementalConnectivity<LfBus> createConnectivity(LfNetwork network) {
        GraphDecrementalConnectivity<LfBus> connectivity = connectivityProvider.get();
        for (LfBus bus : network.getBuses()) {
            connectivity.addVertex(bus);
        }
        for (LfBranch branch : network.getBranches()) {
            connectivity.addEdge(branch.getBus1(), branch.getBus2());
        }
        return connectivity;
    }
}
