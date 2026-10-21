/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 */
public class AreaState extends ElementState<LfArea> {

    double interchangeTarget;

    public AreaState(LfArea area) {
        super(area);
        this.interchangeTarget = area.getInterchangeTarget();
    }

    public static AreaState save(LfArea area) {
        return new AreaState(area);
    }

    @Override
    public void restore() {
        element.setInterchangeTarget(interchangeTarget);
    }
}
