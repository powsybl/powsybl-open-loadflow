/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.openloadflow.dc;

import com.powsybl.openloadflow.lf.outerloop.OuterLoopContext;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */

public class DcOuterLoopContextImpl implements OuterLoopContext {

    private final LfNetwork network;

    private int iteration;

    private Object data;

    private DcLoadFlowContext loadFlowContext;

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

    public DcLoadFlowContext getDcLoadFlowContext() {
        return loadFlowContext;
    }

    public void setLoadFlowContext(DcLoadFlowContext loadFlowContext) {
        this.loadFlowContext = loadFlowContext;
    }
}
