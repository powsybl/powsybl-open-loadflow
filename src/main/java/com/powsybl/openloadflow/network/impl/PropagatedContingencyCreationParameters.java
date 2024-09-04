/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.impl;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PropagatedContingencyCreationParameters {

    private boolean contingencyPropagation = OpenSecurityAnalysisParameters.CONTINGENCY_PROPAGATION_DEFAULT_VALUE;

    private boolean slackDistributionOnConformLoad = LfNetworkParameters.DISTRIBUTED_ON_CONFORM_LOAD_DEFAULT_VALUE;

    private boolean shuntCompensatorVoltageControlOn = LoadFlowParameters.DEFAULT_SHUNT_COMPENSATOR_VOLTAGE_CONTROL_ON;

    private boolean hvdcAcEmulation = LoadFlowParameters.DEFAULT_HVDC_AC_EMULATION_ON;

    private boolean areaInterchangeControlOn = OpenLoadFlowParameters.AREA_INTERCHANGE_CONTROL_DEFAULT_VALUE;

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

    public boolean isAreaInterchangeControlOn() {
        return areaInterchangeControlOn;
    }

    public PropagatedContingencyCreationParameters setAreaInterchangeControlOn(boolean areaInterchangeControlOn) {
        this.areaInterchangeControlOn = areaInterchangeControlOn;
        return this;
    }
}
