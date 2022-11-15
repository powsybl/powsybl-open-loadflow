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

    public static final LfGenerator.RangeMode REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE = LfGenerator.RangeMode.MAX;

    private SlackBusSelector slackBusSelector = new FirstSlackBusSelector();

    private GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

    private boolean generatorVoltageRemoteControl = false;

    private boolean minImpedance = false;

    private boolean twtSplitShuntAdmittance = false;

    private boolean breakers = false;

    private double plausibleActivePowerLimit = PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE;

    private boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds = false;

    private boolean computeMainConnectedComponentOnly = true;

    private Set<Country> countriesToBalance = Collections.emptySet();

    private boolean distributedOnConformLoad = false;

    private boolean phaseControl = false;

    private boolean transformerVoltageControl = false;

    private boolean voltagePerReactivePowerControl = false;

    private boolean reactivePowerRemoteControl = false;

    private boolean dc = false;

    private boolean shuntVoltageControl = false;

    private boolean reactiveLimits = true;

    private boolean hvdcAcEmulation = false;

    private double minPlausibleTargetVoltage = MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double maxPlausibleTargetVoltage = MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private Set<String> loaderPostProcessorSelection = Collections.emptySet();

    private LfGenerator.RangeMode rangeMode = LfGenerator.RangeMode.MAX;

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public LfNetworkParameters setSlackBusSelector(SlackBusSelector slackBusSelector) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        return this;
    }

    public GraphConnectivityFactory<LfBus, LfBranch> getConnectivityFactory() {
        return connectivityFactory;
    }

    public LfNetworkParameters setConnectivityFactory(GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        this.connectivityFactory = Objects.requireNonNull(connectivityFactory);
        return this;
    }

    public boolean isGeneratorVoltageRemoteControl() {
        return generatorVoltageRemoteControl;
    }

    public LfNetworkParameters setGeneratorVoltageRemoteControl(boolean generatorVoltageRemoteControl) {
        this.generatorVoltageRemoteControl = generatorVoltageRemoteControl;
        return this;
    }

    public boolean isMinImpedance() {
        return minImpedance;
    }

    public LfNetworkParameters setMinImpedance(boolean minImpedance) {
        this.minImpedance = minImpedance;
        return this;
    }

    public boolean isTwtSplitShuntAdmittance() {
        return twtSplitShuntAdmittance;
    }

    public LfNetworkParameters setTwtSplitShuntAdmittance(boolean twtSplitShuntAdmittance) {
        this.twtSplitShuntAdmittance = twtSplitShuntAdmittance;
        return this;
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

    public LfNetworkParameters setPlausibleActivePowerLimit(double plausibleActivePowerLimit) {
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        return this;
    }

    public boolean isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds() {
        return addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
    }

    public LfNetworkParameters setAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds) {
        this.addRatioToLinesWithDifferentNominalVoltageAtBothEnds = addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
        return this;
    }

    public boolean isComputeMainConnectedComponentOnly() {
        return computeMainConnectedComponentOnly;
    }

    public LfNetworkParameters setComputeMainConnectedComponentOnly(boolean computeMainConnectedComponentOnly) {
        this.computeMainConnectedComponentOnly = computeMainConnectedComponentOnly;
        return this;
    }

    public Set<Country> getCountriesToBalance() {
        return Collections.unmodifiableSet(countriesToBalance);
    }

    public LfNetworkParameters setCountriesToBalance(Set<Country> countriesToBalance) {
        this.countriesToBalance = Objects.requireNonNull(countriesToBalance);
        return this;
    }

    public boolean isDistributedOnConformLoad() {
        return distributedOnConformLoad;
    }

    public LfNetworkParameters setDistributedOnConformLoad(boolean distributedOnConformLoad) {
        this.distributedOnConformLoad = distributedOnConformLoad;
        return this;
    }

    public boolean isPhaseControl() {
        return phaseControl;
    }

    public LfNetworkParameters setPhaseControl(boolean phaseControl) {
        this.phaseControl = phaseControl;
        return this;
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

    public LfNetworkParameters setVoltagePerReactivePowerControl(boolean voltagePerReactivePowerControl) {
        this.voltagePerReactivePowerControl = voltagePerReactivePowerControl;
        return this;
    }

    public boolean isReactivePowerRemoteControl() {
        return reactivePowerRemoteControl;
    }

    public LfNetworkParameters setReactivePowerRemoteControl(boolean reactivePowerRemoteControl) {
        this.reactivePowerRemoteControl = reactivePowerRemoteControl;
        return this;
    }

    public boolean isDc() {
        return dc;
    }

    public LfNetworkParameters setDc(boolean dc) {
        this.dc = dc;
        return this;
    }

    public boolean isShuntVoltageControl() {
        return shuntVoltageControl;
    }

    public LfNetworkParameters setShuntVoltageControl(boolean shuntVoltageControl) {
        this.shuntVoltageControl = shuntVoltageControl;
        return this;
    }

    public boolean isReactiveLimits() {
        return reactiveLimits;
    }

    public LfNetworkParameters setReactiveLimits(boolean reactiveLimits) {
        this.reactiveLimits = reactiveLimits;
        return this;
    }

    public boolean isHvdcAcEmulation() {
        return hvdcAcEmulation;
    }

    public LfNetworkParameters setHvdcAcEmulation(boolean hvdcAcEmulation) {
        this.hvdcAcEmulation = hvdcAcEmulation;
        return this;
    }

    public double getMinPlausibleTargetVoltage() {
        return minPlausibleTargetVoltage;
    }

    public LfNetworkParameters setMinPlausibleTargetVoltage(double minPlausibleTargetVoltage) {
        this.minPlausibleTargetVoltage = minPlausibleTargetVoltage;
        return this;
    }

    public double getMaxPlausibleTargetVoltage() {
        return maxPlausibleTargetVoltage;
    }

    public LfNetworkParameters setMaxPlausibleTargetVoltage(double maxPlausibleTargetVoltage) {
        this.maxPlausibleTargetVoltage = maxPlausibleTargetVoltage;
        return this;
    }

    public LfGenerator.RangeMode getRangeMode() {
        return rangeMode;
    }

    public LfNetworkParameters setRangeMode(LfGenerator.RangeMode rangeMode) {
        this.rangeMode = rangeMode;
        return this;
    }

    public Set<String> getLoaderPostProcessorSelection() {
        return loaderPostProcessorSelection;
    }

    public LfNetworkParameters setLoaderPostProcessorSelection(Set<String> loaderPostProcessorSelection) {
        this.loaderPostProcessorSelection = Objects.requireNonNull(loaderPostProcessorSelection);
        return this;
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
                ", dc=" + dc +
                ", reactiveLimits=" + reactiveLimits +
                ", hvdcAcEmulation=" + hvdcAcEmulation +
                ", minPlausibleTargetVoltage=" + minPlausibleTargetVoltage +
                ", maxPlausibleTargetVoltage=" + maxPlausibleTargetVoltage +
                ", loaderPostProcessorSelection=" + loaderPostProcessorSelection +
                ", rangeMode=" + rangeMode +
                ')';
    }
}
