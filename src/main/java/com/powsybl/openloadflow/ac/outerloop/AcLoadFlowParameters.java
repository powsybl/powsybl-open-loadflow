/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.equations.VoltageInitializer;
import com.powsybl.openloadflow.network.LfNetworkParameters;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowParameters {

    private final LfNetworkParameters networkParameters;

    private VoltageInitializer voltageInitializer;

    private final NewtonRaphsonStoppingCriteria stoppingCriteria;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    private final boolean forceA1Var;

    private final Set<String> branchesWithCurrent;

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, VoltageInitializer voltageInitializer,
                                NewtonRaphsonStoppingCriteria stoppingCriteria, List<OuterLoop> outerLoops,
                                MatrixFactory matrixFactory, boolean forceA1Var, Set<String> branchesWithCurrent) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.voltageInitializer = Objects.requireNonNull(voltageInitializer);
        this.stoppingCriteria = Objects.requireNonNull(stoppingCriteria);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        this.forceA1Var = forceA1Var;
        this.branchesWithCurrent = branchesWithCurrent;
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public VoltageInitializer getVoltageInitializer() {
        return voltageInitializer;
    }

    public void setVoltageInitializer(VoltageInitializer voltageInitializer) {
        this.voltageInitializer = voltageInitializer;
    }

    public NewtonRaphsonStoppingCriteria getStoppingCriteria() {
        return stoppingCriteria;
    }

    public List<OuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }

    public boolean isForceA1Var() {
        return forceA1Var;
    }

    public Set<String> getBranchesWithCurrent() {
        return branchesWithCurrent;
    }
}
