/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac.outerloop;

import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.equations.VariableSet;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OuterLoopContext {

    private final int iteration;

    private final LfNetwork network;

    private final VariableSet variableSet;

    private final NewtonRaphsonResult lastNewtonRaphsonResult;

    OuterLoopContext(int iteration, LfNetwork network, VariableSet variableSet,
                     NewtonRaphsonResult lastNewtonRaphsonResult) {
        this.iteration = iteration;
        this.network = Objects.requireNonNull(network);
        this.variableSet = variableSet;
        this.lastNewtonRaphsonResult = Objects.requireNonNull(lastNewtonRaphsonResult);
    }

    public int getIteration() {
        return iteration;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public VariableSet getVariableSet() {
        return variableSet;
    }

    public NewtonRaphsonResult getLastNewtonRaphsonResult() {
        return lastNewtonRaphsonResult;
    }
}
