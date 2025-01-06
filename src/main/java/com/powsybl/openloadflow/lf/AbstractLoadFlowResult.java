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

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLoadFlowResult implements LoadFlowResult {

    protected final LfNetwork network;
    protected final List<com.powsybl.loadflow.LoadFlowResult.SlackBusResult> slackBusResults;
    protected final int outerLoopIterations;
    protected final OuterLoopResult outerLoopResult;
    protected final double distributedActivePower;

    protected AbstractLoadFlowResult(LfNetwork network,
                                     List<com.powsybl.loadflow.LoadFlowResult.SlackBusResult> slackBusResults,
                                     int outerLoopIterations,
                                     OuterLoopResult outerLoopResult,
                                     double distributedActivePower) {
        this.network = Objects.requireNonNull(network);
        this.slackBusResults = slackBusResults;
        this.outerLoopIterations = outerLoopIterations;
        this.outerLoopResult = Objects.requireNonNull(outerLoopResult);
        this.distributedActivePower = distributedActivePower;
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    /*@Override
    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch;
    }*/

    @Override
    public List<com.powsybl.loadflow.LoadFlowResult.SlackBusResult> getSlackBusResults() {
        return slackBusResults;
    }

    public int getOuterLoopIterations() {
        return outerLoopIterations;
    }

    public OuterLoopResult getOuterLoopResult() {
        return outerLoopResult;
    }

    public double getDistributedActivePower() {
        return distributedActivePower;
    }
}
