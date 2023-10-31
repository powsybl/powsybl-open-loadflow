/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.util;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public final class Markers {

    public static final Marker PERFORMANCE_MARKER = MarkerFactory.getMarker("PERFORMANCE");

    private Markers() {
    }
}
