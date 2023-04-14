/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.openloadflow.OuterLoopContext;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonResult;
import com.powsybl.openloadflow.lf.LoadFlowContext;
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

    private LoadFlowContext loadFlowContext;

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

    @Override
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

    @Override
    public LoadFlowContext getLoadFlowContext() {
        return loadFlowContext;
    }

    public AcLoadFlowContext getAcLoadFlowContext() {
        if (loadFlowContext.getClass() == AcLoadFlowContext.class) {
            return (AcLoadFlowContext) loadFlowContext;
        } else {
            throw new ClassCastException("loadFlowContext attribute should be of type AcLoadFlowContext in AcOuterLoopContextImpl");
        }
    }

    @Override
    public void setLoadFlowContext(LoadFlowContext loadFlowContext) {
        this.loadFlowContext = loadFlowContext;
    }
}
