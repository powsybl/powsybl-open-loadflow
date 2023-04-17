/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.OuterLoopContext;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */

public class DcOuterLoopContextImpl implements OuterLoopContext {

    private final LfNetwork network;

    private int iteration;

    private Object data;

    private LoadFlowContext loadFlowContext;

    DcOuterLoopContextImpl(LfNetwork network) {
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

    public DcLoadFlowContext getDcLoadFlowContext() {
        if (loadFlowContext.getClass() == DcLoadFlowContext.class) {
            return (DcLoadFlowContext) loadFlowContext;
        } else {
            throw new ClassCastException("loadFlowContext attribute should be of type DcLoadFlowContext in DcOuterLoopContextImpl");
        }
    }

    @Override
    public void setLoadFlowContext(LoadFlowContext loadFlowContext) {
        this.loadFlowContext = loadFlowContext;
    }
}
