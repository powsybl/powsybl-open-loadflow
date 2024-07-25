/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.util.Evaluable;

import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface LfArea extends PropertyBag {
    String getId();

    double getInterchangeTarget();

    double getInterchange();

    Set<LfBus> getBuses();

    void addBus(LfBus bus);

    void addBoundaryP(Supplier<Evaluable> p);

    LfNetwork getNetwork();

}
