/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface ActivePowerDistributionOuterLoop<V extends Enum<V> & Quantity,
            E extends Enum<E> & Quantity,
            P extends AbstractLoadFlowParameters,
            C extends LoadFlowContext<V, E, P>,
            O extends OuterLoopContext<V, E, P, C>>
        extends OuterLoop<V, E, P, C, O> {

    double getDistributedActivePower(O context);

    double getSlackBusActivePowerMismatch(O context);

    OpenLoadFlowParameters.SlackDistributionFailureBehavior getSlackDistributionFailureBehavior(O context);

    OuterLoopResult handleDistributionFailure(O context, DistributedSlackContextData contextData, boolean movedBuses, double totalDistributedActivePower, double remainingMismatch, String errorMessage);

}
