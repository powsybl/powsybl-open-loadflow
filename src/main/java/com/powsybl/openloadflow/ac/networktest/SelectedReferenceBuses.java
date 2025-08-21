/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.networktest;

import com.powsybl.openloadflow.network.LfBus;

import java.util.List;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SelectedReferenceBuses {
    private final List<LfBus> lfBuses;
    private final String selectionMethod;

    public SelectedReferenceBuses(List<LfBus> lfBuses, String selectionMethod) {
        this.lfBuses = Objects.requireNonNull(lfBuses);
        this.selectionMethod = Objects.requireNonNull(selectionMethod);
    }

    public List<LfBus> getLfBuses() {
        return lfBuses;
    }

    public String getSelectionMethod() {
        return selectionMethod;
    }

}
