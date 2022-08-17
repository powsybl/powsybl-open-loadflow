/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.iidm.network.Country;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class LfNetworkParameters {

    public static final double PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE = 5000;

    /**
     * Minimal and maximal plausible target V in p.u
     */
    public static final double MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE = 0.8;

    public static final double MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE = 1.2;

    private SlackBusSelector slackBusSelector;

    private final GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory;

    private final boolean generatorVoltageRemoteControl;

    private final boolean minImpedance;

    private final boolean twtSplitShuntAdmittance;

    private boolean breakers;

    private final double plausibleActivePowerLimit;

    private final boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds;

    private final boolean computeMainConnectedComponentOnly;

    private final Set<Country> countriesToBalance;

    private final boolean distributedOnConformLoad;

    private final boolean phaseControl;

    private boolean transformerVoltageControl;

    private final boolean voltagePerReactivePowerControl;

    private final boolean reactivePowerRemoteControl;

    private final boolean isDc;

    private final boolean shuntVoltageControl;

    private final boolean reactiveLimits;

    private final boolean hvdcAcEmulation;

    private double minPlausibleTargetVoltage;

    private double maxPlausibleTargetVoltage;

    public LfNetworkParameters() {
        this(new FirstSlackBusSelector());
    }

    public LfNetworkParameters(SlackBusSelector slackBusSelector) {
        this(slackBusSelector, new EvenShiloachGraphDecrementalConnectivityFactory<>());
    }

    public LfNetworkParameters(SlackBusSelector slackBusSelector, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this(slackBusSelector, connectivityFactory, false, false, false, false,
                PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE, false,
                true, Collections.emptySet(), false, false, false, false, false, false, false, true, false,
                MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE, MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE);
    }

    public LfNetworkParameters(SlackBusSelector slackBusSelector, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                               boolean generatorVoltageRemoteControl, boolean minImpedance, boolean twtSplitShuntAdmittance, boolean breakers,
                               double plausibleActivePowerLimit, boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds,
                               boolean computeMainConnectedComponentOnly, Set<Country> countriesToBalance, boolean distributedOnConformLoad,
                               boolean phaseControl, boolean transformerVoltageControl, boolean voltagePerReactivePowerControl, boolean reactivePowerRemoteControl,
                               boolean isDc, boolean shuntVoltageControl, boolean reactiveLimits, boolean hvdcAcEmulation, double minPlausibleTargetVoltage,
                               double maxPlausibleTargetVoltage) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
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
        this.reactivePowerRemoteControl = reactivePowerRemoteControl;
        this.isDc = isDc;
        this.shuntVoltageControl = shuntVoltageControl;
        this.reactiveLimits = reactiveLimits;
        this.hvdcAcEmulation = hvdcAcEmulation;
        this.minPlausibleTargetVoltage = minPlausibleTargetVoltage;
        this.maxPlausibleTargetVoltage = maxPlausibleTargetVoltage;
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public void setSlackBusSelector(SlackBusSelector slackBusSelector) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
    }

    public GraphConnectivityFactory<LfBus, LfBranch> getConnectivityFactory() {
        return connectivityFactory;
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

    public LfNetworkParameters setBreakers(boolean breakers) {
        this.breakers = breakers;
        return this;
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

    public LfNetworkParameters setTransformerVoltageControl(boolean transformerVoltageControl) {
        this.transformerVoltageControl = transformerVoltageControl;
        return this;
    }

    public boolean isVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
    }

    public boolean isReactivePowerRemoteControl() {
        return reactivePowerRemoteControl;
    }

    public boolean isDc() {
        return isDc;
    }

    public boolean isShuntVoltageControl() {
        return shuntVoltageControl;
    }

    public boolean isReactiveLimits() {
        return reactiveLimits;
    }

    public boolean isHvdcAcEmulation() {
        return hvdcAcEmulation;
    }

    public double getMinPlausibleTargetVoltage() {
        return minPlausibleTargetVoltage;
    }

    public double getMaxPlausibleTargetVoltage() {
        return maxPlausibleTargetVoltage;
    }

    @Override
    public String toString() {
        return "LfNetworkParameters(" +
                "slackBusSelector=" + slackBusSelector.getClass().getSimpleName() +
                ", connectivityFactory=" + connectivityFactory.getClass().getSimpleName() +
                ", generatorVoltageRemoteControl=" + generatorVoltageRemoteControl +
                ", minImpedance=" + minImpedance +
                ", twtSplitShuntAdmittance=" + twtSplitShuntAdmittance +
                ", breakers=" + breakers +
                ", plausibleActivePowerLimit=" + plausibleActivePowerLimit +
                ", addRatioToLinesWithDifferentNominalVoltageAtBothEnds=" + addRatioToLinesWithDifferentNominalVoltageAtBothEnds +
                ", computeMainConnectedComponentOnly=" + computeMainConnectedComponentOnly +
                ", countriesToBalance=" + countriesToBalance +
                ", distributedOnConformLoad=" + distributedOnConformLoad +
                ", phaseControl=" + phaseControl +
                ", transformerVoltageControl=" + transformerVoltageControl +
                ", voltagePerReactivePowerControl=" + voltagePerReactivePowerControl +
                ", reactivePowerRemoteControl=" + reactivePowerRemoteControl +
                ", isDc=" + isDc +
                ", reactiveLimits=" + reactiveLimits +
                ", hvdcAcEmulation=" + hvdcAcEmulation +
                ", minPlausibleTargetVoltage=" + minPlausibleTargetVoltage +
                ", maxPlausibleTargetVoltage=" + maxPlausibleTargetVoltage +
                ')';
    }
}
