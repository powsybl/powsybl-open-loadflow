/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple.ac.outerloop;

import com.powsybl.loadflow.simple.ac.nr.NewtonRaphsonResult;
import com.powsybl.loadflow.simple.equations.EquationSystem;
import com.powsybl.loadflow.simple.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OuterLoopContext {

    private final int iteration;

    private final LfNetwork network;

    private final EquationSystem equationSystem;

    private final NewtonRaphsonResult lastNewtonRaphsonResult;

    OuterLoopContext(int iteration, LfNetwork network, EquationSystem equationSystem, NewtonRaphsonResult lastNewtonRaphsonResult) {
        this.iteration = iteration;
        this.network = Objects.requireNonNull(network);
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.lastNewtonRaphsonResult = Objects.requireNonNull(lastNewtonRaphsonResult);
    }

    public int getIteration() {
        return iteration;
    }

    public LfNetwork getNetwork() {
        return network;
    }

    public EquationSystem getEquationSystem() {
        return equationSystem;
    }

    public NewtonRaphsonResult getLastNewtonRaphsonResult() {
        return lastNewtonRaphsonResult;
    }
}
