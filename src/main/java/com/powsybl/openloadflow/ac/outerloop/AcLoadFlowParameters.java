/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowParameters {

    private final LfNetworkParameters networkParameters;

    private final AcEquationSystemCreationParameters equationSystemCreationParameters;

    private final NewtonRaphsonParameters newtonRaphsonParameters;

    private final List<OuterLoop> outerLoops;

    private final MatrixFactory matrixFactory;

    public AcLoadFlowParameters(LfNetworkParameters networkParameters, AcEquationSystemCreationParameters equationSystemCreationParameters,
                                NewtonRaphsonParameters newtonRaphsonParameters, List<OuterLoop> outerLoops, MatrixFactory matrixFactory) {
        this.networkParameters = Objects.requireNonNull(networkParameters);
        this.equationSystemCreationParameters = Objects.requireNonNull(equationSystemCreationParameters);
        this.newtonRaphsonParameters = Objects.requireNonNull(newtonRaphsonParameters);
        this.outerLoops = Objects.requireNonNull(outerLoops);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
    }

    public LfNetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    public AcEquationSystemCreationParameters getEquationSystemCreationParameters() {
        return equationSystemCreationParameters;
    }

    public NewtonRaphsonParameters getNewtonRaphsonParameters() {
        return newtonRaphsonParameters;
    }

    public List<OuterLoop> getOuterLoops() {
        return outerLoops;
    }

    public MatrixFactory getMatrixFactory() {
        return matrixFactory;
    }
}
