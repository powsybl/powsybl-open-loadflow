/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public abstract class AbstractLfAction<A extends Action> implements LfAction {

    protected final A action;

    AbstractLfAction(A action) {
        this.action = Objects.requireNonNull(action);
    }

    @Override
    public String getId() {
        return action.getId();
    }

    @Override
    public String getType() {
        return action.getType();
    }

    @Override
    public boolean checkError(Network network) {
        return false;
    }
}
