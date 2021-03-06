/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface SlackBusSelector {

    LfBus select(List<LfBus> buses);

    static SlackBusSelector fromMode(SlackBusSelectionMode mode, String slackBusId) {
        Objects.requireNonNull(mode);
        switch (mode) {
            case FIRST:
                return new FirstSlackBusSelector();
            case MOST_MESHED:
                return new MostMeshedSlackBusSelector();
            case NAME:
                return new NameSlackBusSelector(slackBusId);
            default:
                throw new IllegalStateException("Unknown slack bus selection mode: " + mode);
        }
    }
}
