/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkLoadingParameters {

    private final SlackBusSelector slackBusSelector;

    private final boolean generatorVoltageRemoteControl;

    private final boolean minImpedance;

    private final boolean twtSplitShuntAdmittance;

    private final boolean breakers;

    public LfNetworkLoadingParameters(SlackBusSelector slackBusSelector, boolean generatorVoltageRemoteControl,
                                      boolean minImpedance, boolean twtSplitShuntAdmittance, boolean breakers) {
        this.slackBusSelector = slackBusSelector;
        this.generatorVoltageRemoteControl = generatorVoltageRemoteControl;
        this.minImpedance = minImpedance;
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        this.breakers = breakers;
    }

    public LfNetworkLoadingParameters(SlackBusSelector slackBusSelector, boolean generatorVoltageRemoteControl,
                                      boolean minImpedance, boolean twtSplitShuntAdmittance) {
        this(slackBusSelector, generatorVoltageRemoteControl, minImpedance, twtSplitShuntAdmittance, false);
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public boolean isGeneratorVoltageRemoteControl() {
        return generatorVoltageRemoteControl;
    }

    public boolean isMinImpedance() {
        return minImpedance;
    }

    public boolean isTwtSplitShuntAdmittance() {
        return twtSplitShuntAdmittance;
    }

    public boolean isBreakers() {
        return breakers;
    }
}
