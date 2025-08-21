/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network;

import com.powsybl.openloadflow.ac.networktest.ReferenceBusMultipleSelector;
import com.powsybl.openloadflow.ac.networktest.SelectedReferenceBuses;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public interface ReferenceBusSelector {

    ReferenceBusSelectionMode DEFAULT_MODE = ReferenceBusSelectionMode.FIRST_SLACK;
    ReferenceBusSelector DEFAULT_SELECTOR = ReferenceBusSelector.fromMode(DEFAULT_MODE);

    SelectedReferenceBuses select(LfNetwork lfNetwork);

    static ReferenceBusSelector fromMode(ReferenceBusSelectionMode mode) {
        Objects.requireNonNull(mode);
        return switch (mode) {
            case FIRST_SLACK -> new ReferenceBusFirstSlackSelector();
            case GENERATOR_REFERENCE_PRIORITY -> new ReferenceBusGeneratorPrioritySelector();
            case MULTIPLE_REFERENCES -> new ReferenceBusMultipleSelector();
        };
    }
}
