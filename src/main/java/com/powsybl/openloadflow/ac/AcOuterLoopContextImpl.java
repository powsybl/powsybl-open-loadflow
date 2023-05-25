/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.lf.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcOuterLoopContextImpl implements OuterLoopContext {

    private final LfNetwork network;

    private int iteration;

    private NewtonRaphsonResult lastNewtonRaphsonResult;

    private Object data;

    private AcLoadFlowContext loadFlowContext;

    AcOuterLoopContextImpl(LfNetwork network) {
        this.network = Objects.requireNonNull(network);
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public NewtonRaphsonResult getLastNewtonRaphsonResult() {
        return lastNewtonRaphsonResult;
    }

    public void setLastNewtonRaphsonResult(NewtonRaphsonResult lastNewtonRaphsonResult) {
        this.lastNewtonRaphsonResult = lastNewtonRaphsonResult;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public void setData(Object data) {
        this.data = data;
    }

    public AcLoadFlowContext getAcLoadFlowContext() {
        return loadFlowContext;
    }

    public void setAcLoadFlowContext(AcLoadFlowContext loadFlowContext) {
        this.loadFlowContext = loadFlowContext;
    }
}
