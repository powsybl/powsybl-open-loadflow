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

import java.util.Map;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLoadFlowResult implements LoadFlowResult {

    protected final LfNetwork network;

    protected final Map<Integer, Double> slackBusActivePowerMismatch;
    protected final int outerLoopIterations;
    protected final OuterLoopResult outerLoopResult;
    protected final Map<Integer, Double> distributedActivePower;

    protected AbstractLoadFlowResult(LfNetwork network, Map<Integer, Double> slackBusActivePowerMismatch, int outerLoopIterations, OuterLoopResult outerLoopResult, Map<Integer, Double> distributedActivePower) {
        this.network = Objects.requireNonNull(network);
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
        this.outerLoopIterations = outerLoopIterations;
        this.outerLoopResult = Objects.requireNonNull(outerLoopResult);
        this.distributedActivePower = distributedActivePower;
    }

    @Override
    public LfNetwork getNetwork() {
        return network;
    }

    @Override
    public double getSlackBusActivePowerMismatch() {
        return slackBusActivePowerMismatch.values().stream().reduce(0.0, Double::sum);
    }

    public double getSlackBusActivePowerMismatch(int numSc) {
        return slackBusActivePowerMismatch.get(numSc);
    }

    public int getOuterLoopIterations() {
        return outerLoopIterations;
    }

    public OuterLoopResult getOuterLoopResult() {
        return outerLoopResult;
    }

    @Override
    public double getDistributedActivePower() {
        return distributedActivePower.values().stream().reduce(0.0, Double::sum);
    }

    public double getDistributedActivePower(int numSc) {
        return distributedActivePower.get(numSc);
    }
}
