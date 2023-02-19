/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.extensions;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Line;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class CurrentLimitAutomatonAdderImpl extends AbstractExtensionAdder<Line, CurrentLimitAutomaton> implements CurrentLimitAutomatonAdder {

    private double threshold = Double.NaN;

    private String switchId;

    private boolean switchOpen = true;

    public CurrentLimitAutomatonAdderImpl(Line line) {
        super(line);
    }

    @Override
    public Class<? super CurrentLimitAutomaton> getExtensionClass() {
        return CurrentLimitAutomaton.class;
    }

    @Override
    public CurrentLimitAutomatonAdderImpl withThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    @Override
    public CurrentLimitAutomatonAdderImpl withSwitchId(String switchId) {
        this.switchId = Objects.requireNonNull(switchId);
        return this;
    }

    @Override
    public CurrentLimitAutomatonAdderImpl withSwitchOpen(boolean open) {
        this.switchOpen = open;
        return this;
    }

    @Override
    protected CurrentLimitAutomaton createExtension(Line line) {
        if (Double.isNaN(threshold)) {
            throw new PowsyblException("Threshold is not set");
        }
        if (switchId == null) {
            throw new PowsyblException("Switch ID is not set");
        }
        return new CurrentLimitAutomatonImpl(line, threshold, switchId, switchOpen);
    }
}
