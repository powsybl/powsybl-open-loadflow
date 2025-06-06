/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractOuterLoopContext<V extends Enum<V> & Quantity,
                                               E extends Enum<E> & Quantity,
                                               P extends AbstractLoadFlowParameters,
                                               C extends LoadFlowContext<V, E, P>>
        implements OuterLoopContext<V, E, P, C> {

    protected final LfNetwork network;

    protected Object data;

    protected C loadFlowContext;
    private int outerLoopTotalIterations; // current total iterations over all outer loop types, for reporting purposes
    private int iteration;

    protected AbstractOuterLoopContext(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public C getLoadFlowContext() {
        return loadFlowContext;
    }

    @Override
    public void setLoadFlowContext(C loadFlowContext) {
        this.loadFlowContext = loadFlowContext;
    }

    public int getOuterLoopTotalIterations() {
        return outerLoopTotalIterations;
    }

    public void setOuterLoopTotalIterations(int outerLoopTotalIterations) {
        this.outerLoopTotalIterations = outerLoopTotalIterations;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }
}
