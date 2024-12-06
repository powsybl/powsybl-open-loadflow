/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.lf.outerloop;

import com.powsybl.openloadflow.equations.Quantity;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.lf.LoadFlowContext;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public abstract class AbstractActivePowerDistributionOuterLoop<V extends Enum<V> & Quantity,
        E extends Enum<E> & Quantity,
        P extends AbstractLoadFlowParameters<P>,
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

}
