/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum ReferenceBusSelectionMode {
    /**
     * angle reference bus selected as the first slack bus among potentially multiple slacks
     */
    FIRST_SLACK,
    /**
     * angle reference bus selected from generator reference priorities
     */
    GENERATOR_REFERENCE_PRIORITY
}
