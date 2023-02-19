/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.iidm.network.Line;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class CurrentLimitAutomatonImpl extends AbstractExtension<Line> implements CurrentLimitAutomaton {

    private final double threshold;

    private final String switchId;

    private final boolean switchOpen;

    public CurrentLimitAutomatonImpl(Line line, double threshold, String switchId, boolean switchOpen) {
        super(line);
        this.threshold = threshold;
        this.switchId = Objects.requireNonNull(switchId);
        this.switchOpen = switchOpen;
    }

    @Override
    public double getThreshold() {
        return threshold;
    }

    @Override
    public String getSwitchId() {
        return switchId;
    }

    @Override
    public boolean isSwitchOpen() {
        return switchOpen;
    }
}
