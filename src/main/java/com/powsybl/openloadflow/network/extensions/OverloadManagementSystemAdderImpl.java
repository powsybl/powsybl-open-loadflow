/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.commons.PowsyblException;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OverloadManagementSystemAdderImpl<T> implements OverloadManagementSystemAdder<T> {

    private final T parent;

    private final Consumer<OverloadManagementSystemImpl> parentAdder;

    public OverloadManagementSystemAdderImpl(T parent, Consumer<OverloadManagementSystemImpl> parentAdder) {
        this.parent = Objects.requireNonNull(parent);
        this.parentAdder = Objects.requireNonNull(parentAdder);
    }

    private String lineIdToMonitor;

    private double threshold = Double.NaN;

    private String switchIdToOperate;

    private boolean switchOpen = true;

    @Override
    public OverloadManagementSystemAdderImpl<T> withLineIdToMonitor(String lineIdToMonitor) {
        this.lineIdToMonitor = Objects.requireNonNull(lineIdToMonitor);
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
        if (lineIdToMonitor == null) {
            throw new PowsyblException("Line ID to monitor is not set");
        }
        if (Double.isNaN(threshold)) {
            throw new PowsyblException("Threshold is not set");
        }
        if (switchIdToOperate == null) {
            throw new PowsyblException("Switch ID to operate is not set");
        }
        OverloadManagementSystemImpl system = new OverloadManagementSystemImpl(lineIdToMonitor, threshold, switchIdToOperate, switchOpen);
        parentAdder.accept(system);
        return parent;
    }
}
