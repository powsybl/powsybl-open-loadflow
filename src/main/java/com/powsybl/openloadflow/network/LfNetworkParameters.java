/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class LfNetworkParameters {

    public static final double PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE = 5000;

    public static final boolean USE_ACTIVE_LIMITS_DEFAULT_VALUE = true;

    /**
     * Minimal and maximal plausible target V in p.u
     */
    public static final double MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE = 0.8;

    public static final double MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE = 1.2;

    public static final double MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_DEFAULT_VALUE = 20;

    public static final double LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE = Math.pow(10, -8); // in per unit

    public static final OpenLoadFlowParameters.ReactiveRangeCheckMode REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE = OpenLoadFlowParameters.ReactiveRangeCheckMode.MAX;

    public static final int DEFAULT_MAX_SLACK_BUS_COUNT = 1;

    public static final String DEBUG_DIR_DEFAULT_VALUE = null;

    public static final boolean SECONDARY_VOLTAGE_CONTROL_DEFAULT_VALUE = false;

    public static final boolean CACHE_ENABLED_DEFAULT_VALUE = false;

    public static final boolean ASYMMETRICAL_DEFAULT_VALUE = false;

    public static final boolean USE_LOAD_MODE_DEFAULT_VALUE = false;

    public static final Set<Country> SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE = Collections.emptySet();

    public static final boolean SIMULATE_AUTOMATION_SYSTEMS_DEFAULT_VALUE = false;

    private SlackBusSelector slackBusSelector = new FirstSlackBusSelector(SLACK_BUS_COUNTRY_FILTER_DEFAULT_VALUE);

    private GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory = new EvenShiloachGraphDecrementalConnectivityFactory<>();

    public static final LinePerUnitMode LINE_PER_UNIT_MODE_DEFAULT_VALUE = LinePerUnitMode.IMPEDANCE;

    private boolean generatorVoltageRemoteControl = true;

    private boolean minImpedance = false;

    private boolean twtSplitShuntAdmittance = false;

    private boolean breakers = false;

    private double plausibleActivePowerLimit = PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE;

    private boolean useActiveLimits = USE_ACTIVE_LIMITS_DEFAULT_VALUE;

    private boolean computeMainConnectedComponentOnly = true;

    private Set<Country> countriesToBalance = Collections.emptySet();

    public static final boolean DISTRIBUTED_ON_CONFORM_LOAD_DEFAULT_VALUE = false;

    private boolean distributedOnConformLoad = DISTRIBUTED_ON_CONFORM_LOAD_DEFAULT_VALUE;

    private boolean phaseControl = false;

    private boolean transformerVoltageControl = false;

    private boolean voltagePerReactivePowerControl = false;

    private boolean generatorReactivePowerRemoteControl = false;

    private boolean transformerReactivePowerControl = false;

    private LoadFlowModel loadFlowModel = LoadFlowModel.AC;

    private boolean shuntVoltageControl = false;

    private boolean reactiveLimits = true;

    private boolean hvdcAcEmulation = false;

    private double minPlausibleTargetVoltage = MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double maxPlausibleTargetVoltage = MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double minNominalVoltageTargetVoltageCheck = MIN_NOMINAL_VOLTAGE_TARGET_VOLTAGE_CHECK_DEFAULT_VALUE;

    private Set<String> loaderPostProcessorSelection = Collections.emptySet();

    private OpenLoadFlowParameters.ReactiveRangeCheckMode reactiveRangeCheckMode = REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE;

    private double lowImpedanceThreshold = LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE;

    private boolean svcVoltageMonitoring = true;

    private int maxSlackBusCount = DEFAULT_MAX_SLACK_BUS_COUNT;

    private String debugDir = DEBUG_DIR_DEFAULT_VALUE;

    private boolean secondaryVoltageControl = SECONDARY_VOLTAGE_CONTROL_DEFAULT_VALUE;

    private boolean cacheEnabled = CACHE_ENABLED_DEFAULT_VALUE;

    private boolean asymmetrical = ASYMMETRICAL_DEFAULT_VALUE;

    private LinePerUnitMode linePerUnitMode = LINE_PER_UNIT_MODE_DEFAULT_VALUE;

    private boolean useLoadModel = USE_LOAD_MODE_DEFAULT_VALUE;

    private boolean simulateAutomationSystems = SIMULATE_AUTOMATION_SYSTEMS_DEFAULT_VALUE;

    public LfNetworkParameters() {
    }

    public LfNetworkParameters(LfNetworkParameters other) {
        Objects.requireNonNull(other);
        this.slackBusSelector = other.slackBusSelector;
        this.connectivityFactory = other.connectivityFactory;
        this.generatorVoltageRemoteControl = other.generatorVoltageRemoteControl;
        this.minImpedance = other.minImpedance;
        this.twtSplitShuntAdmittance = other.twtSplitShuntAdmittance;
        this.breakers = other.breakers;
        this.plausibleActivePowerLimit = other.plausibleActivePowerLimit;
        this.computeMainConnectedComponentOnly = other.computeMainConnectedComponentOnly;
        this.countriesToBalance = new HashSet<>(other.countriesToBalance);
        this.distributedOnConformLoad = other.distributedOnConformLoad;
        this.phaseControl = other.phaseControl;
        this.transformerVoltageControl = other.transformerVoltageControl;
        this.voltagePerReactivePowerControl = other.voltagePerReactivePowerControl;
        this.generatorReactivePowerRemoteControl = other.generatorReactivePowerRemoteControl;
        this.transformerReactivePowerControl = other.transformerReactivePowerControl;
        this.loadFlowModel = other.loadFlowModel;
        this.shuntVoltageControl = other.shuntVoltageControl;
        this.reactiveLimits = other.reactiveLimits;
        this.hvdcAcEmulation = other.hvdcAcEmulation;
        this.minPlausibleTargetVoltage = other.minPlausibleTargetVoltage;
        this.maxPlausibleTargetVoltage = other.maxPlausibleTargetVoltage;
        this.minNominalVoltageTargetVoltageCheck = other.minNominalVoltageTargetVoltageCheck;
        this.loaderPostProcessorSelection = new HashSet<>(other.loaderPostProcessorSelection);
        this.reactiveRangeCheckMode = other.reactiveRangeCheckMode;
        this.lowImpedanceThreshold = other.lowImpedanceThreshold;
        this.svcVoltageMonitoring = other.svcVoltageMonitoring;
        this.maxSlackBusCount = other.maxSlackBusCount;
        this.debugDir = other.debugDir;
        this.secondaryVoltageControl = other.secondaryVoltageControl;
        this.cacheEnabled = other.cacheEnabled;
        this.asymmetrical = other.asymmetrical;
        this.linePerUnitMode = other.linePerUnitMode;
        this.useLoadModel = other.useLoadModel;
        this.simulateAutomationSystems = other.simulateAutomationSystems;
    }

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

    public boolean isUseActiveLimits() {
        return useActiveLimits;
    }

    public LfNetworkParameters setUseActiveLimits(boolean useActiveLimits) {
        this.useActiveLimits = useActiveLimits;
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

    public boolean isGeneratorReactivePowerRemoteControl() {
        return generatorReactivePowerRemoteControl;
    }

    public LfNetworkParameters setGeneratorReactivePowerRemoteControl(boolean generatorReactivePowerRemoteControl) {
        this.generatorReactivePowerRemoteControl = generatorReactivePowerRemoteControl;
        return this;
    }

    public boolean isTransformerReactivePowerControl() {
        return transformerReactivePowerControl;
    }

    public LfNetworkParameters setTransformerReactivePowerControl(boolean transformerReactivePowerControl) {
        this.transformerReactivePowerControl = transformerReactivePowerControl;
        return this;
    }

    public LoadFlowModel getLoadFlowModel() {
        return loadFlowModel;
    }

    public LfNetworkParameters setLoadFlowModel(LoadFlowModel loadFlowModel) {
        this.loadFlowModel = Objects.requireNonNull(loadFlowModel);
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

    public double getMinNominalVoltageTargetVoltageCheck() {
        return minNominalVoltageTargetVoltageCheck;
    }

    public LfNetworkParameters setMinNominalVoltageTargetVoltageCheck(double minNominalVoltageTargetVoltageCheck) {
        this.minNominalVoltageTargetVoltageCheck = minNominalVoltageTargetVoltageCheck;
        return this;
    }

    public double getLowImpedanceThreshold() {
        return lowImpedanceThreshold;
    }

    public LfNetworkParameters setLowImpedanceThreshold(double lowImpedanceThreshold) {
        if (lowImpedanceThreshold <= 0) {
            throw new PowsyblException("lowImpedanceThreshold must be greater than 0");
        }
        this.lowImpedanceThreshold = lowImpedanceThreshold;
        return this;
    }

    public OpenLoadFlowParameters.ReactiveRangeCheckMode getReactiveRangeCheckMode() {
        return reactiveRangeCheckMode;
    }

    public LfNetworkParameters setReactiveRangeCheckMode(OpenLoadFlowParameters.ReactiveRangeCheckMode reactiveRangeCheckMode) {
        this.reactiveRangeCheckMode = reactiveRangeCheckMode;
        return this;
    }

    public boolean isSvcVoltageMonitoring() {
        return svcVoltageMonitoring;
    }

    public LfNetworkParameters setSvcVoltageMonitoring(boolean svcVoltageMonitoring) {
        this.svcVoltageMonitoring = svcVoltageMonitoring;
        return this;
    }

    public Set<String> getLoaderPostProcessorSelection() {
        return loaderPostProcessorSelection;
    }

    public LfNetworkParameters setLoaderPostProcessorSelection(Set<String> loaderPostProcessorSelection) {
        this.loaderPostProcessorSelection = Objects.requireNonNull(loaderPostProcessorSelection);
        return this;
    }

    public int getMaxSlackBusCount() {
        return maxSlackBusCount;
    }

    public static int checkMaxSlackBusCount(int maxSlackBusCount) {
        if (maxSlackBusCount < 1) {
            throw new IllegalArgumentException("Max slack bus count should be >= 1");
        }
        return maxSlackBusCount;
    }

    public LfNetworkParameters setMaxSlackBusCount(int maxSlackBusCount) {
        this.maxSlackBusCount = checkMaxSlackBusCount(maxSlackBusCount);
        return this;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public LfNetworkParameters setDebugDir(String debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    public boolean isSecondaryVoltageControl() {
        return secondaryVoltageControl;
    }

    public LfNetworkParameters setSecondaryVoltageControl(boolean secondaryVoltageControl) {
        this.secondaryVoltageControl = secondaryVoltageControl;
        return this;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public LfNetworkParameters setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        return this;
    }

    public boolean isAsymmetrical() {
        return asymmetrical;
    }

    public LfNetworkParameters setAsymmetrical(boolean asymmetrical) {
        this.asymmetrical = asymmetrical;
        return this;
    }

    public LinePerUnitMode getLinePerUnitMode() {
        return linePerUnitMode;
    }

    public LfNetworkParameters setLinePerUnitMode(LinePerUnitMode linePerUnitMode) {
        this.linePerUnitMode = Objects.requireNonNull(linePerUnitMode);
        return this;
    }

    public boolean isUseLoadModel() {
        return useLoadModel;
    }

    public LfNetworkParameters setUseLoadModel(boolean useLoadModel) {
        this.useLoadModel = useLoadModel;
        return this;
    }

    public boolean isSimulateAutomationSystems() {
        return simulateAutomationSystems;
    }

    public LfNetworkParameters setSimulateAutomationSystems(boolean simulateAutomationSystems) {
        this.simulateAutomationSystems = simulateAutomationSystems;
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
                ", computeMainConnectedComponentOnly=" + computeMainConnectedComponentOnly +
                ", countriesToBalance=" + countriesToBalance +
                ", distributedOnConformLoad=" + distributedOnConformLoad +
                ", phaseControl=" + phaseControl +
                ", transformerVoltageControl=" + transformerVoltageControl +
                ", voltagePerReactivePowerControl=" + voltagePerReactivePowerControl +
                ", generatorReactivePowerRemoteControl=" + generatorReactivePowerRemoteControl +
                ", transformerReactivePowerControl=" + transformerReactivePowerControl +
                ", loadFlowModel=" + loadFlowModel +
                ", reactiveLimits=" + reactiveLimits +
                ", hvdcAcEmulation=" + hvdcAcEmulation +
                ", minPlausibleTargetVoltage=" + minPlausibleTargetVoltage +
                ", maxPlausibleTargetVoltage=" + maxPlausibleTargetVoltage +
                ", loaderPostProcessorSelection=" + loaderPostProcessorSelection +
                ", reactiveRangeCheckMode=" + reactiveRangeCheckMode +
                ", lowImpedanceThreshold=" + lowImpedanceThreshold +
                ", svcVoltageMonitoring=" + svcVoltageMonitoring +
                ", maxSlackBusCount=" + maxSlackBusCount +
                ", debugDir=" + debugDir +
                ", secondaryVoltageControl=" + secondaryVoltageControl +
                ", cacheEnabled=" + cacheEnabled +
                ", asymmetrical=" + asymmetrical +
                ", minNominalVoltageTargetVoltageCheck=" + minNominalVoltageTargetVoltageCheck +
                ", linePerUnitMode=" + linePerUnitMode +
                ", useLoadModel=" + useLoadModel +
                ", simulateAutomationSystems=" + simulateAutomationSystems +
                ')';
    }
}
