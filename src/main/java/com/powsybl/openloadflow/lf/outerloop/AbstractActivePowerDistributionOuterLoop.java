/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;
import com.powsybl.openloadflow.network.LfGenerator;

import java.util.Objects;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public abstract class AbstractActivePowerDistributionOuterLoop<V extends Enum<V> & Quantity,
        E extends Enum<E> & Quantity,
        P extends AbstractLoadFlowParameters,
        C extends LoadFlowContext<V, E, P>,
        O extends OuterLoopContext<V, E, P, C>>
        implements ActivePowerDistributionOuterLoop<V, E, P, C, O> {

    @Override
    public double getDistributedActivePower(O context) {
        var contextData = context.getData();
        if (contextData instanceof DistributedSlackContextData distributedSlackContextData) {
            return distributedSlackContextData.getDistributedActivePower();
        }
        return Double.NaN;
    }

    @Override
    public OuterLoopResult handleDistributionFailure(O context, DistributedSlackContextData contextData, boolean movedBuses, double totalDistributedActivePower, double remainingMismatch, String errorMessage) {
        OpenLoadFlowParameters.SlackDistributionFailureBehavior slackDistributionFailureBehavior = getSlackDistributionFailureBehavior(context);

        switch (slackDistributionFailureBehavior) {
            case THROW ->
                    throw new PowsyblException(errorMessage);

            case LEAVE_ON_SLACK_BUS -> {
                return new OuterLoopResult(this, movedBuses ? OuterLoopStatus.UNSTABLE : OuterLoopStatus.STABLE);
            }
            case FAIL -> {
                // Mismatches reported in LoadFlowResult on slack bus(es) are the mismatches of the last NR run.
                // Since we will not be re-running an NR, revert distributedActivePower reporting which would otherwise be misleading.
                // Said differently, we report that we didn't distribute anything, and this is indeed consistent with the network state.
                contextData.addDistributedActivePower(-totalDistributedActivePower);
                return new OuterLoopResult(this, OuterLoopStatus.FAILED, errorMessage);
            }
            case DISTRIBUTE_ON_REFERENCE_GENERATOR -> {
                LfGenerator referenceGenerator = context.getNetwork().getReferenceGenerator();
                Objects.requireNonNull(referenceGenerator, () -> "No reference generator in " + context.getNetwork());
                // remaining goes to reference generator, without any limit consideration
                contextData.addDistributedActivePower(remainingMismatch);
                referenceGenerator.setTargetP(referenceGenerator.getTargetP() + remainingMismatch);
                return new OuterLoopResult(this, OuterLoopStatus.UNSTABLE);
            }
            default -> throw new IllegalArgumentException("Unknown slackDistributionFailureBehavior");
        }
    }

}
