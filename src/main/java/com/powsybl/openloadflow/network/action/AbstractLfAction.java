/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public abstract class AbstractLfAction<A extends Action> {

    protected final String id;

    protected final A action;

    AbstractLfAction(String id, A action) {
        this.id = id;
        this.action = action;
    }

    public String getId() {
        return id;
    }

    public abstract boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity);
}
