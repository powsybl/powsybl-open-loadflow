/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OverloadManagementFunctionImpl implements OverloadManagementFunction {

    private final String lineId;

    private final double threshold;

    private final String switchId;

    private final boolean switchOpen;

    public OverloadManagementFunctionImpl(String lineId, double threshold, String switchId, boolean switchOpen) {
        this.lineId = Objects.requireNonNull(lineId);
        this.threshold = threshold;
        this.switchId = Objects.requireNonNull(switchId);
        this.switchOpen = switchOpen;
    }

    @Override
    public String getLineId() {
        return lineId;
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
