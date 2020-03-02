/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.AcLoadFlowObserver;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import com.powsybl.openloadflow.network.SlackBusSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private SlackBusSelector slackBusSelector = new MostMeshedSlackBusSelector();

    private boolean distributedSlack = true;

    private boolean dc = false;

    private boolean voltageRemoteControl = false;

    private final List<AcLoadFlowObserver> additionalObservers = new ArrayList<>();

    @Override
    public String getName() {
        return "SimpleLoadFlowParameters";
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public OpenLoadFlowParameters setSlackBusSelector(SlackBusSelector slackBusSelector) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        return this;
    }

    public boolean isDistributedSlack() {
        return distributedSlack;
    }

    public OpenLoadFlowParameters setDistributedSlack(boolean distributedSlack) {
        this.distributedSlack = distributedSlack;
        return this;
    }

    public boolean isDc() {
        return dc;
    }

    public OpenLoadFlowParameters setDc(boolean dc) {
        this.dc = dc;
        return this;
    }

    public boolean hasVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public OpenLoadFlowParameters setVoltageRemoteControl(boolean voltageRemoteControl) {
        this.voltageRemoteControl = voltageRemoteControl;
        return this;
    }

    public List<AcLoadFlowObserver> getAdditionalObservers() {
        return additionalObservers;
    }
}
