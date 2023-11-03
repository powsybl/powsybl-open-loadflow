/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network.impl;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PropagatedContingencyCreationParameters {

    private boolean contingencyPropagation = false;

    private boolean slackDistributionOnConformLoad = false;

    private boolean shuntCompensatorVoltageControlOn = false;

    private boolean hvdcAcEmulation = false;

    public boolean isContingencyPropagation() {
        return contingencyPropagation;
    }

    public PropagatedContingencyCreationParameters setContingencyPropagation(boolean contingencyPropagation) {
        this.contingencyPropagation = contingencyPropagation;
        return this;
    }

    public boolean isSlackDistributionOnConformLoad() {
        return slackDistributionOnConformLoad;
    }

    public PropagatedContingencyCreationParameters setSlackDistributionOnConformLoad(boolean slackDistributionOnConformLoad) {
        this.slackDistributionOnConformLoad = slackDistributionOnConformLoad;
        return this;
    }

    public boolean isShuntCompensatorVoltageControlOn() {
        return shuntCompensatorVoltageControlOn;
    }

    public PropagatedContingencyCreationParameters setShuntCompensatorVoltageControlOn(boolean shuntCompensatorVoltageControlOn) {
        this.shuntCompensatorVoltageControlOn = shuntCompensatorVoltageControlOn;
        return this;
    }

    public boolean isHvdcAcEmulation() {
        return hvdcAcEmulation;
    }

    public PropagatedContingencyCreationParameters setHvdcAcEmulation(boolean hvdcAcEmulation) {
        this.hvdcAcEmulation = hvdcAcEmulation;
        return this;
    }
}
