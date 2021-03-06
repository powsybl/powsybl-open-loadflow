/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;
import com.powsybl.openloadflow.util.ParameterConstants;

import java.util.Collections;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkParameters {

    private final SlackBusSelector slackBusSelector;

    private final boolean generatorVoltageRemoteControl;

    private final boolean minImpedance;

    private final boolean twtSplitShuntAdmittance;

    private final boolean breakers;

    private final double plausibleActivePowerLimit;

    private final boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds;

    private final boolean computeMainConnectedComponentOnly;

    private final Set<Country> countriesToBalance;

    private boolean distributedOnConformLoad;

    private final boolean phaseControl;

    private final boolean transformerVoltageControl;

    private final boolean voltagePerReactivePowerControl;

    public LfNetworkParameters(SlackBusSelector slackBusSelector) {
        this(slackBusSelector, false, false, false, false,
                ParameterConstants.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false,
                true, Collections.emptySet(), false, false, false, false);
    }

    public LfNetworkParameters(SlackBusSelector slackBusSelector, boolean generatorVoltageRemoteControl,
                               boolean minImpedance, boolean twtSplitShuntAdmittance, boolean breakers,
                               double plausibleActivePowerLimit, boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds,
                               boolean computeMainConnectedComponentOnly, Set<Country> countriesToBalance, boolean distributedOnConformLoad,
                               boolean phaseControl, boolean transformerVoltageControl, boolean voltagePerReactivePowerControl) {
        this.slackBusSelector = slackBusSelector;
        this.generatorVoltageRemoteControl = generatorVoltageRemoteControl;
        this.minImpedance = minImpedance;
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        this.breakers = breakers;
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        this.addRatioToLinesWithDifferentNominalVoltageAtBothEnds = addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
        this.computeMainConnectedComponentOnly = computeMainConnectedComponentOnly;
        this.countriesToBalance = countriesToBalance;
        this.distributedOnConformLoad = distributedOnConformLoad;
        this.phaseControl = phaseControl;
        this.transformerVoltageControl = transformerVoltageControl;
        this.voltagePerReactivePowerControl = voltagePerReactivePowerControl;
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

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }

    public boolean isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds() {
        return addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
    }

    public boolean isComputeMainConnectedComponentOnly() {
        return computeMainConnectedComponentOnly;
    }

    public Set<Country> getCountriesToBalance() {
        return Collections.unmodifiableSet(countriesToBalance);
    }

    public boolean isDistributedOnConformLoad() {
        return distributedOnConformLoad;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public boolean isTransformerVoltageControl() {
        return transformerVoltageControl;
    }

    public boolean isVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
    }
}
