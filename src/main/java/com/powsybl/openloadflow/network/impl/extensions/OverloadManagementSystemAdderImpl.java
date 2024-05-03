/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl.extensions;

import com.powsybl.commons.PowsyblException;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OverloadManagementSystemAdderImpl<T> implements OverloadManagementSystemAdder<T> {

    private final T parent;

    private final Consumer<OverloadManagementSystemImpl> parentAdder;

    public OverloadManagementSystemAdderImpl(T parent, Consumer<OverloadManagementSystemImpl> parentAdder) {
        this.parent = Objects.requireNonNull(parent);
        this.parentAdder = Objects.requireNonNull(parentAdder);
    }

    private boolean enabled = true;

    private String monitoredLineId;

    private double threshold = Double.NaN;

    private String switchIdToOperate;

    private boolean switchOpen = true;

    @Override
    public OverloadManagementSystemAdder<T> withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public OverloadManagementSystemAdderImpl<T> withMonitoredLineId(String monitoredLineId) {
        this.monitoredLineId = Objects.requireNonNull(monitoredLineId);
        return this;
    }

    @Override
    public OverloadManagementSystemAdderImpl<T> withThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    @Override
    public OverloadManagementSystemAdderImpl<T> withSwitchIdToOperate(String switchIdToOperate) {
        this.switchIdToOperate = Objects.requireNonNull(switchIdToOperate);
        return this;
    }

    @Override
    public OverloadManagementSystemAdderImpl<T> withSwitchOpen(boolean open) {
        this.switchOpen = open;
        return this;
    }

    @Override
    public T add() {
        if (monitoredLineId == null) {
            throw new PowsyblException("Line ID to monitor is not set");
        }
        if (Double.isNaN(threshold)) {
            throw new PowsyblException("Threshold is not set");
        }
        if (switchIdToOperate == null) {
            throw new PowsyblException("Switch ID to operate is not set");
        }
        OverloadManagementSystemImpl system = new OverloadManagementSystemImpl(enabled, monitoredLineId, threshold, switchIdToOperate, switchOpen);
        parentAdder.accept(system);
        return parent;
    }
}
