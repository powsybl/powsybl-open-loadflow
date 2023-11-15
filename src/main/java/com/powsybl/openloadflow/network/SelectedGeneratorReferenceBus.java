/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SelectedGeneratorReferenceBus extends SelectedReferenceBus {
    private final LfGenerator lfGenerator;

    public SelectedGeneratorReferenceBus(LfBus lfBus, String selectionMethod, LfGenerator lfGenerator) {
        super(lfBus, selectionMethod);
        this.lfGenerator = Objects.requireNonNull(lfGenerator);
    }

    public LfGenerator getLfGenerator() {
        return lfGenerator;
    }
}
