/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf;

import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLoadFlowResult implements LoadFlowResult {

    protected final LfNetwork network;

    protected final double slackBusActivePowerMismatch;
    protected final int outerLoopIterations;
    protected final OuterLoopResult outerLoopResult;

    protected AbstractLoadFlowResult(LfNetwork network, double slackBusActivePowerMismatch, int outerLoopIterations, OuterLoopResult outerLoopResult) {
        this.network = Objects.requireNonNull(network);
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
        this.outerLoopIterations = outerLoopIterations;
        this.outerLoopResult = Objects.requireNonNull(outerLoopResult);
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }

    public int getOuterLoopIterations() {
        return outerLoopIterations;
    }

    public OuterLoopResult getOuterLoopResult() {
        return outerLoopResult;
    }
}
