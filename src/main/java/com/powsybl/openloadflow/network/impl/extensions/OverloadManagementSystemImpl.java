/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl.extensions;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OverloadManagementSystemImpl implements OverloadManagementSystem {

    private final String lineIdToMonitor;

    private final double threshold;

    private final String switchIdToOperate;

    private final boolean switchOpen;

    public OverloadManagementSystemImpl(String lineIdToMonitor, double threshold, String switchIdToOperate, boolean switchOpen) {
        this.lineIdToMonitor = Objects.requireNonNull(lineIdToMonitor);
        this.threshold = threshold;
        this.switchIdToOperate = Objects.requireNonNull(switchIdToOperate);
        this.switchOpen = switchOpen;
    }

    @Override
    public String getLineIdToMonitor() {
        return lineIdToMonitor;
    }

    @Override
    public double getThreshold() {
        return threshold;
    }

    @Override
    public String getSwitchIdToOperate() {
        return switchIdToOperate;
    }

    @Override
    public boolean isSwitchOpen() {
        return switchOpen;
    }
}
