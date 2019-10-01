/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.loadflow.simple;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.simple.ac.nr.AcLoadFlowObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SimpleLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private SlackBusSelectionMode slackBusSelectionMode = SlackBusSelectionMode.MOST_MESHED;

    private boolean distributedSlack = false;

    private boolean reactiveLimits = false;

    private boolean dc = false;

    private final List<AcLoadFlowObserver> additionalObservers = new ArrayList<>();

    @Override
    public String getName() {
        return "SimpleLoadFlowParameters";
    }

    public SlackBusSelectionMode getSlackBusSelectionMode() {
        return slackBusSelectionMode;
    }

    public SimpleLoadFlowParameters setSlackBusSelectionMode(SlackBusSelectionMode slackBusSelectionMode) {
        this.slackBusSelectionMode = Objects.requireNonNull(slackBusSelectionMode);
        return this;
    }

    public boolean isDistributedSlack() {
        return distributedSlack;
    }

    public SimpleLoadFlowParameters setDistributedSlack(boolean distributedSlack) {
        this.distributedSlack = distributedSlack;
        return this;
    }

    public boolean hasReactiveLimits() {
        return reactiveLimits;
    }

    public SimpleLoadFlowParameters setReactiveLimits(boolean reactiveLimits) {
        this.reactiveLimits = reactiveLimits;
        return this;
    }

    public boolean isDc() {
        return dc;
    }

    public SimpleLoadFlowParameters setDc(boolean dc) {
        this.dc = dc;
        return this;
    }

    public List<AcLoadFlowObserver> getAdditionalObservers() {
        return additionalObservers;
    }
}
