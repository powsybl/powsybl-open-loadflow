/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import java.util.Set;

/**
 * @author Valentin Mouradian {@literal <valentin.mouradian at artelys.com>}
 */
public interface LfArea extends PropertyBag {
    String getId();

    double getInterchangeTarget();

    void setInterchangeTarget(double interchangeTarget);

    double getInterchange();

    Set<LfBus> getBuses();

    Set<Boundary> getBoundaries();

    LfNetwork getNetwork();

    public interface Boundary {
        LfBranch getBranch();

        double getP();

    }

}
