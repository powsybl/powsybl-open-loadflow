/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.ac.networktest.SelectedReferenceBuses;

import java.util.List;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SelectedGeneratorReferenceBuses extends SelectedReferenceBuses {
    private final LfGenerator lfGenerator;

    public SelectedGeneratorReferenceBuses(List<LfBus> lfBuses, String selectionMethod, LfGenerator lfGenerator) {
        super(lfBuses, selectionMethod);
        this.lfGenerator = Objects.requireNonNull(lfGenerator);
    }

    public LfGenerator getLfGenerator() {
        return lfGenerator;
    }
}
