/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.dc;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openloadflow.lf.AbstractLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.util.PerUnit;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowResult extends AbstractLoadFlowResult {

    private final boolean solverSuccess;

    public static DcLoadFlowResult createNoCalculationResult(LfNetwork network) {
        return new DcLoadFlowResult(network, 0, false, OuterLoopResult.stable(), Collections.EMPTY_LIST, Double.NaN);
    }

    public DcLoadFlowResult(LfNetwork network, int outerLoopIterations, boolean solverSuccess, OuterLoopResult outerLoopResult, List<LoadFlowResult.SlackBusResult> slackBusResults, double distributedActivePower) {
        super(network, slackBusResults, outerLoopIterations, outerLoopResult, distributedActivePower);
        this.solverSuccess = solverSuccess;
    }

    @Override
    public boolean isSuccess() {
        return solverSuccess && getOuterLoopResult().status() == OuterLoopStatus.STABLE;
    }

    @Override
    public Status toComponentResultStatus() {
        if (network.getValidity() != LfNetwork.Validity.VALID) {
            return new Status(LoadFlowResult.ComponentResult.Status.NO_CALCULATION, network.getValidity().toString());
        }
        if (getOuterLoopResult().status() == OuterLoopStatus.UNSTABLE) {
            return new Status(LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, "Reached outer loop max iterations limit. Last outer loop name: " + getOuterLoopResult().outerLoopName());
        } else if (getOuterLoopResult().status() == OuterLoopStatus.FAILED) {
            return new Status(LoadFlowResult.ComponentResult.Status.FAILED, "Outer loop failed: " + getOuterLoopResult().statusText());
        }
        if (solverSuccess) {
            return new Status(LoadFlowResult.ComponentResult.Status.CONVERGED, "Converged");
        }
        return new Status(LoadFlowResult.ComponentResult.Status.FAILED, "Solver Failed");
    }

    @Override
    public String toString() {
        return "DcLoadFlowResult(outerLoopIterations=" + outerLoopIterations
                + ", solverSuccess=" + solverSuccess
                + ", outerLoopStatus=" + outerLoopResult.status()
                + ", slackBusResults=SlackBusResult("
                    + slackBusResults.stream()
                    .map(s -> "(id=" + s.getId() + ", activePowerMismatch=" + s.getActivePowerMismatch() + ")")
                    .collect(Collectors.joining(""))
                    + ")"
                + ", distributedActivePower=" + distributedActivePower * PerUnit.SB
                + ")";
    }
}
