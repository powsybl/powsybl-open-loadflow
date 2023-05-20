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
public class OverloadManagementFunctionAdderImpl<T> implements OverloadManagementFunctionAdder<T> {

    private final T parent;

    private final Consumer<OverloadManagementFunctionImpl> parentAdder;

    public OverloadManagementFunctionAdderImpl(T parent, Consumer<OverloadManagementFunctionImpl> parentAdder) {
        this.parent = Objects.requireNonNull(parent);
        this.parentAdder = Objects.requireNonNull(parentAdder);
    }

    private String lineId;

    private double threshold = Double.NaN;

    private String switchId;

    private boolean switchOpen = true;

    @Override
    public OverloadManagementFunctionAdderImpl<T> withLineId(String lineId) {
        this.lineId = Objects.requireNonNull(lineId);
        return this;
    }

    @Override
    public OverloadManagementFunctionAdderImpl<T> withThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    @Override
    public OverloadManagementFunctionAdderImpl<T> withSwitchId(String switchId) {
        this.switchId = Objects.requireNonNull(switchId);
        return this;
    }

    @Override
    public OverloadManagementFunctionAdderImpl<T> withSwitchOpen(boolean open) {
        this.switchOpen = open;
        return this;
    }

    @Override
    public T add() {
        if (lineId == null) {
            throw new PowsyblException("Line ID is not set");
        }
        if (Double.isNaN(threshold)) {
            throw new PowsyblException("Threshold is not set");
        }
        if (switchId == null) {
            throw new PowsyblException("Switch ID is not set");
        }
        OverloadManagementFunctionImpl function = new OverloadManagementFunctionImpl(lineId, threshold, switchId, switchOpen);
        parentAdder.accept(function);
        return parent;
    }
}
