/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.commons.parameters.ParameterType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.IncrementalTransformerVoltageControlOuterLoop;
import com.powsybl.openloadflow.ac.VoltageMagnitudeInitializer;
import com.powsybl.openloadflow.ac.equations.AcEquationSystemCreationParameters;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.ac.nr.StateVectorScalingMode;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.OuterLoop;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcValueVoltageInitializer;
import com.powsybl.openloadflow.dc.equations.DcEquationSystemCreationParameters;
import com.powsybl.openloadflow.graph.GraphConnectivityFactory;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.PreviousValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.UniformValueVoltageInitializer;
import com.powsybl.openloadflow.network.util.VoltageInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenLoadFlowParameters.class);

    public static final SlackBusSelectionMode SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE = SlackBusSelectionMode.MOST_MESHED;

    public static final LowImpedanceBranchMode LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE = LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE;

    public static final boolean THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE = false;

    public static final boolean VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE = true;

    public static final boolean REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE = false;

    public static final boolean LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE = false;

    /**
     * Slack bus maximum active power mismatch in MW: 1 Mw => 10^-2 in p.u
     */
    public static final double SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE = 1.0;

    public static final boolean VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE = false;

    public static final double DC_POWER_FACTOR_DEFAULT_VALUE = 1.0;

    private static final boolean NETWORK_CACHE_ENABLED_DEFAULT_VALUE = false;

    public static final boolean SVC_VOLTAGE_MONITORING_DEFAULT_VALUE = true;

    public static final VoltageInitModeOverride VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE = VoltageInitModeOverride.NONE;

    public static final TransformerVoltageControlMode TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE = TransformerVoltageControlMode.WITH_GENERATOR_VOLTAGE_CONTROL;

    public static final String SLACK_BUS_SELECTION_MODE_PARAM_NAME = "slackBusSelectionMode";

    public static final String SLACK_BUSES_IDS_PARAM_NAME = "slackBusesIds";

    public static final String THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME = "throwsExceptionInCaseOfSlackDistributionFailure";

    public static final String VOLTAGE_REMOTE_CONTROL_PARAM_NAME = "voltageRemoteControl";

    public static final String REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME = "reactivePowerRemoteControl";

    public static final String LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME = "lowImpedanceBranchMode";

    public static final String LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME = "loadPowerFactorConstant";

    public static final String PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME = "plausibleActivePowerLimit";

    public static final String SLACK_BUS_P_MAX_MISMATCH_NAME = "slackBusPMaxMismatch";

    public static final String VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME = "voltagePerReactivePowerControl";

    public static final String MAX_ITERATION_NAME = "maxIteration";

    public static final String NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME = "newtonRaphsonConvEpsPerEq";

    public static final String VOLTAGE_INIT_MODE_OVERRIDE_NAME = "voltageInitModeOverride";

    public static final String TRANSFORMER_VOLTAGE_CONTROL_MODE_NAME = "transformerVoltageControlMode";

    public static final String DC_POWER_FACTOR_NAME = "dcPowerFactor";

    public static final String MIN_PLAUSIBLE_TARGET_VOLTAGE_NAME = "minPlausibleTargetVoltage";

    public static final String MAX_PLAUSIBLE_TARGET_VOLTAGE_NAME = "maxPlausibleTargetVoltage";

    public static final String MIN_REALISTIC_VOLTAGE_NAME = "minRealisticVoltage";

    public static final String MAX_REALISTIC_VOLTAGE_NAME = "maxRealisticVoltage";

    public static final String REACTIVE_RANGE_CHECK_MODE_NAME = "reactiveRangeCheckMode";

    public static final String LOW_IMPEDANCE_THRESHOLD_NAME = "lowImpedanceThreshold";

    public static final String NETWORK_CACHE_ENABLED_NAME = "networkCacheEnabled";

    public static final String SVC_VOLTAGE_MONITORING_NAME = "svcVoltageMonitoring";

    public static final String STATE_VECTOR_SCALING_MODE_NAME = "stateVectorScalingMode";

    public static final String MAX_SLACK_BUS_COUNT_NAME = "maxSlackBusCount";

    public static final String DEBUG_DIR_PARAM_NAME = "debugDir";

    public static final String INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME = "incrementalTransformerVoltageControlOuterLoopMaxTapShift";

    private static <E extends Enum<E>> List<Object> getEnumPossibleValues(Class<E> enumClass) {
        return EnumSet.allOf(enumClass).stream().map(Enum::name).collect(Collectors.toList());
    }

    public static final List<Parameter> SPECIFIC_PARAMETERS = List.of(
        new Parameter(SLACK_BUS_SELECTION_MODE_PARAM_NAME, ParameterType.STRING, "Slack bus selection mode", SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(SlackBusSelectionMode.class)),
        new Parameter(SLACK_BUSES_IDS_PARAM_NAME, ParameterType.STRING, "Slack bus IDs", null),
        new Parameter(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, ParameterType.STRING, "Low impedance branch mode", LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(LowImpedanceBranchMode.class)),
        new Parameter(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "Generator voltage remote control", VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE),
        new Parameter(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, ParameterType.BOOLEAN, "Throws an exception in case of slack distribution failure", THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE),
        new Parameter(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, ParameterType.BOOLEAN, "Load power factor is constant", LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE),
        new Parameter(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, ParameterType.DOUBLE, "Plausible active power limit", LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE),
        new Parameter(SLACK_BUS_P_MAX_MISMATCH_NAME, ParameterType.DOUBLE, "Slack bus max active power mismatch", SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE),
        new Parameter(VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME, ParameterType.BOOLEAN, "Voltage per reactive power slope", VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE),
        new Parameter(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, ParameterType.BOOLEAN, "SVC remote reactive power control", REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE),
        new Parameter(MAX_ITERATION_NAME, ParameterType.INTEGER, "Max iterations", NewtonRaphsonParameters.DEFAULT_MAX_ITERATION),
        new Parameter(NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME, ParameterType.DOUBLE, "Newton-Raphson convergence epsilon per equation", DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ),
        new Parameter(VOLTAGE_INIT_MODE_OVERRIDE_NAME, ParameterType.STRING, "Voltage init mode override", VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE.name(), getEnumPossibleValues(VoltageInitModeOverride.class)),
        new Parameter(TRANSFORMER_VOLTAGE_CONTROL_MODE_NAME, ParameterType.STRING, "Transformer voltage control mode", TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(TransformerVoltageControlMode.class)),
        new Parameter(DC_POWER_FACTOR_NAME, ParameterType.DOUBLE, "DC approximation power factor", DC_POWER_FACTOR_DEFAULT_VALUE),
        new Parameter(MIN_PLAUSIBLE_TARGET_VOLTAGE_NAME, ParameterType.DOUBLE, "Min plausible target voltage", LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE),
        new Parameter(MAX_PLAUSIBLE_TARGET_VOLTAGE_NAME, ParameterType.DOUBLE, "Max plausible target voltage", LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE),
        new Parameter(MIN_REALISTIC_VOLTAGE_NAME, ParameterType.DOUBLE, "Min realistic voltage", NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE),
        new Parameter(MAX_REALISTIC_VOLTAGE_NAME, ParameterType.DOUBLE, "Max realistic voltage", NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE),
        new Parameter(REACTIVE_RANGE_CHECK_MODE_NAME, ParameterType.STRING, "Reactive range check mode", LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE.name(), getEnumPossibleValues(ReactiveRangeCheckMode.class)),
        new Parameter(LOW_IMPEDANCE_THRESHOLD_NAME, ParameterType.DOUBLE, "Low impedance threshold in per unit", LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE),
        new Parameter(NETWORK_CACHE_ENABLED_NAME, ParameterType.BOOLEAN, "Network cache enabled", NETWORK_CACHE_ENABLED_DEFAULT_VALUE),
        new Parameter(SVC_VOLTAGE_MONITORING_NAME, ParameterType.BOOLEAN, "SVC voltage monitoring", SVC_VOLTAGE_MONITORING_DEFAULT_VALUE),
        new Parameter(STATE_VECTOR_SCALING_MODE_NAME, ParameterType.STRING, "State vector scaling mode", NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE.name(), getEnumPossibleValues(StateVectorScalingMode.class)),
        new Parameter(MAX_SLACK_BUS_COUNT_NAME, ParameterType.INTEGER, "Maximum slack buses count", LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT),
        new Parameter(DEBUG_DIR_PARAM_NAME, ParameterType.STRING, "Directory to dump debug files", LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE, Collections.emptyList(), ParameterScope.TECHNICAL),
        new Parameter(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME, ParameterType.INTEGER, "Incremental transformer voltage control maximum tap shift per outer loop", IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT)
    );

    public enum VoltageInitModeOverride {
        NONE,
        VOLTAGE_MAGNITUDE,
        FULL_VOLTAGE
    }

    public enum TransformerVoltageControlMode {
        WITH_GENERATOR_VOLTAGE_CONTROL,
        AFTER_GENERATOR_VOLTAGE_CONTROL,
        INCREMENTAL_VOLTAGE_CONTROL
    }

    private SlackBusSelectionMode slackBusSelectionMode = SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE;

    private List<String> slackBusesIds = Collections.emptyList();

    private boolean throwsExceptionInCaseOfSlackDistributionFailure = THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE;

    private boolean voltageRemoteControl = VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE;

    private LowImpedanceBranchMode lowImpedanceBranchMode = LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE;

    public enum LowImpedanceBranchMode {
        REPLACE_BY_ZERO_IMPEDANCE_LINE,
        REPLACE_BY_MIN_IMPEDANCE_LINE
    }

    private boolean loadPowerFactorConstant = LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE;

    private double plausibleActivePowerLimit = LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE;

    private double slackBusPMaxMismatch = SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE;

    private boolean voltagePerReactivePowerControl = VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE;

    private boolean reactivePowerRemoteControl = REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE;

    private int maxIteration = NewtonRaphsonParameters.DEFAULT_MAX_ITERATION;

    private double newtonRaphsonConvEpsPerEq = DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ;

    private VoltageInitModeOverride voltageInitModeOverride = VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE;

    private TransformerVoltageControlMode transformerVoltageControlMode = TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE;

    private double dcPowerFactor = DC_POWER_FACTOR_DEFAULT_VALUE;

    private double minPlausibleTargetVoltage = LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double maxPlausibleTargetVoltage = LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE;

    private double minRealisticVoltage = NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE;

    private double maxRealisticVoltage = NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE;

    private double lowImpedanceThreshold = LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE;

    public enum ReactiveRangeCheckMode {
        MIN_MAX,
        MAX,
        TARGET_P
    }

    private ReactiveRangeCheckMode reactiveRangeCheckMode = LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE;

    private boolean networkCacheEnabled = NETWORK_CACHE_ENABLED_DEFAULT_VALUE;

    private boolean svcVoltageMonitoring = SVC_VOLTAGE_MONITORING_DEFAULT_VALUE;

    private StateVectorScalingMode stateVectorScalingMode = NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE;

    private int maxSlackBusCount = LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT;

    private String debugDir = LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE;

    private int incrementalTransformerVoltageControlOuterLoopMaxTapShift = IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT;

    private boolean disym = false;

    @Override
    public String getName() {
        return "open-load-flow-parameters";
    }

    public SlackBusSelectionMode getSlackBusSelectionMode() {
        return slackBusSelectionMode;
    }

    public OpenLoadFlowParameters setSlackBusSelectionMode(SlackBusSelectionMode slackBusSelectionMode) {
        this.slackBusSelectionMode = Objects.requireNonNull(slackBusSelectionMode);
        return this;
    }

    public List<String> getSlackBusesIds() {
        return slackBusesIds;
    }

    public OpenLoadFlowParameters setSlackBusesIds(List<String> slackBusesIds) {
        this.slackBusesIds = Objects.requireNonNull(slackBusesIds);
        return this;
    }

    public OpenLoadFlowParameters setSlackBusId(String slackBusId) {
        this.slackBusesIds = List.of(Objects.requireNonNull(slackBusId));
        return this;
    }

    public boolean isThrowsExceptionInCaseOfSlackDistributionFailure() {
        return throwsExceptionInCaseOfSlackDistributionFailure;
    }

    public OpenLoadFlowParameters setThrowsExceptionInCaseOfSlackDistributionFailure(boolean throwsExceptionInCaseOfSlackDistributionFailure) {
        this.throwsExceptionInCaseOfSlackDistributionFailure = throwsExceptionInCaseOfSlackDistributionFailure;
        return this;
    }

    public boolean hasVoltageRemoteControl() {
        return voltageRemoteControl;
    }

    public OpenLoadFlowParameters setVoltageRemoteControl(boolean voltageRemoteControl) {
        this.voltageRemoteControl = voltageRemoteControl;
        return this;
    }

    public LowImpedanceBranchMode getLowImpedanceBranchMode() {
        return lowImpedanceBranchMode;
    }

    public OpenLoadFlowParameters setLowImpedanceBranchMode(LowImpedanceBranchMode lowImpedanceBranchMode) {
        this.lowImpedanceBranchMode = Objects.requireNonNull(lowImpedanceBranchMode);
        return this;
    }

    public boolean isLoadPowerFactorConstant() {
        return loadPowerFactorConstant;
    }

    public OpenLoadFlowParameters setLoadPowerFactorConstant(boolean loadPowerFactorConstant) {
        this.loadPowerFactorConstant = loadPowerFactorConstant;
        return this;
    }

    public double getPlausibleActivePowerLimit() {
        return plausibleActivePowerLimit;
    }

    public OpenLoadFlowParameters setPlausibleActivePowerLimit(double plausibleActivePowerLimit) {
        if (plausibleActivePowerLimit <= 0) {
            throw new IllegalArgumentException("Invalid plausible active power limit: " + plausibleActivePowerLimit);
        }
        this.plausibleActivePowerLimit = plausibleActivePowerLimit;
        return this;
    }

    public double getSlackBusPMaxMismatch() {
        return slackBusPMaxMismatch;
    }

    public OpenLoadFlowParameters setSlackBusPMaxMismatch(double pSlackBusPMaxMismatch) {
        this.slackBusPMaxMismatch = pSlackBusPMaxMismatch;
        return this;
    }

    public boolean isVoltagePerReactivePowerControl() {
        return voltagePerReactivePowerControl;
    }

    public OpenLoadFlowParameters setVoltagePerReactivePowerControl(boolean voltagePerReactivePowerControl) {
        this.voltagePerReactivePowerControl = voltagePerReactivePowerControl;
        return this;
    }

    public boolean hasReactivePowerRemoteControl() {
        return reactivePowerRemoteControl;
    }

    public OpenLoadFlowParameters setReactivePowerRemoteControl(boolean reactivePowerRemoteControl) {
        this.reactivePowerRemoteControl = reactivePowerRemoteControl;
        return this;
    }

    public int getMaxIteration() {
        return maxIteration;
    }

    public OpenLoadFlowParameters setMaxIteration(int maxIteration) {
        this.maxIteration = NewtonRaphsonParameters.checkMaxIteration(maxIteration);
        return this;
    }

    public double getNewtonRaphsonConvEpsPerEq() {
        return newtonRaphsonConvEpsPerEq;
    }

    public OpenLoadFlowParameters setNewtonRaphsonConvEpsPerEq(double newtonRaphsonConvEpsPerEq) {
        this.newtonRaphsonConvEpsPerEq = newtonRaphsonConvEpsPerEq;
        return this;
    }

    public VoltageInitModeOverride getVoltageInitModeOverride() {
        return voltageInitModeOverride;
    }

    public OpenLoadFlowParameters setVoltageInitModeOverride(VoltageInitModeOverride voltageInitModeOverride) {
        this.voltageInitModeOverride = Objects.requireNonNull(voltageInitModeOverride);
        return this;
    }

    public TransformerVoltageControlMode getTransformerVoltageControlMode() {
        return transformerVoltageControlMode;
    }

    public OpenLoadFlowParameters setTransformerVoltageControlMode(TransformerVoltageControlMode transformerVoltageControlMode) {
        this.transformerVoltageControlMode = Objects.requireNonNull(transformerVoltageControlMode);
        return this;
    }

    public double getDcPowerFactor() {
        return dcPowerFactor;
    }

    public OpenLoadFlowParameters setDcPowerFactor(double dcPowerFactor) {
        this.dcPowerFactor = dcPowerFactor;
        return this;
    }

    public double getMinPlausibleTargetVoltage() {
        return minPlausibleTargetVoltage;
    }

    public OpenLoadFlowParameters setMinPlausibleTargetVoltage(double minPlausibleTargetVoltage) {
        this.minPlausibleTargetVoltage = minPlausibleTargetVoltage;
        return this;
    }

    public double getMaxPlausibleTargetVoltage() {
        return maxPlausibleTargetVoltage;
    }

    public OpenLoadFlowParameters setMaxPlausibleTargetVoltage(double maxPlausibleTargetVoltage) {
        this.maxPlausibleTargetVoltage = maxPlausibleTargetVoltage;
        return this;
    }

    public double getMinRealisticVoltage() {
        return minRealisticVoltage;
    }

    public OpenLoadFlowParameters setMinRealisticVoltage(double minRealisticVoltage) {
        this.minRealisticVoltage = minRealisticVoltage;
        return this;
    }

    public double getMaxRealisticVoltage() {
        return maxRealisticVoltage;
    }

    public OpenLoadFlowParameters setMaxRealisticVoltage(double maxRealisticVoltage) {
        this.maxRealisticVoltage = maxRealisticVoltage;
        return this;
    }

    public ReactiveRangeCheckMode getReactiveRangeCheckMode() {
        return reactiveRangeCheckMode;
    }

    public OpenLoadFlowParameters setReactiveRangeCheckMode(ReactiveRangeCheckMode reactiveRangeCheckMode) {
        this.reactiveRangeCheckMode = reactiveRangeCheckMode;
        return this;
    }

    public double getLowImpedanceThreshold() {
        return lowImpedanceThreshold;
    }

    public OpenLoadFlowParameters setLowImpedanceThreshold(double lowImpedanceThreshold) {
        if (lowImpedanceThreshold <= 0) {
            throw new PowsyblException("lowImpedanceThreshold must be greater than 0");
        }
        this.lowImpedanceThreshold = lowImpedanceThreshold;
        return this;
    }

    public boolean isNetworkCacheEnabled() {
        return networkCacheEnabled;
    }

    public OpenLoadFlowParameters setNetworkCacheEnabled(boolean networkCacheEnabled) {
        this.networkCacheEnabled = networkCacheEnabled;
        return this;
    }

    public boolean isSvcVoltageMonitoring() {
        return svcVoltageMonitoring;
    }

    public OpenLoadFlowParameters setSvcVoltageMonitoring(boolean svcVoltageMonitoring) {
        this.svcVoltageMonitoring = svcVoltageMonitoring;
        return this;
    }

    public StateVectorScalingMode getStateVectorScalingMode() {
        return stateVectorScalingMode;
    }

    public OpenLoadFlowParameters setStateVectorScalingMode(StateVectorScalingMode stateVectorScalingMode) {
        this.stateVectorScalingMode = Objects.requireNonNull(stateVectorScalingMode);
        return this;
    }

    public int getMaxSlackBusCount() {
        return maxSlackBusCount;
    }

    public OpenLoadFlowParameters setMaxSlackBusCount(int maxSlackBusCount) {
        this.maxSlackBusCount = LfNetworkParameters.checkMaxSlackBusCount(maxSlackBusCount);
        return this;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public OpenLoadFlowParameters setDebugDir(String debugDir) {
        this.debugDir = debugDir;
        return this;
    }

    public int getIncrementalTransformerVoltageControlOuterLoopMaxTapShift() {
        return incrementalTransformerVoltageControlOuterLoopMaxTapShift;
    }

    public OpenLoadFlowParameters setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(int incrementalTransformerVoltageControlOuterLoopMaxTapShift) {
        if (incrementalTransformerVoltageControlOuterLoopMaxTapShift < 1) {
            throw new IllegalArgumentException("Invalid max tap shift value: " + incrementalTransformerVoltageControlOuterLoopMaxTapShift);
        }
        this.incrementalTransformerVoltageControlOuterLoopMaxTapShift = incrementalTransformerVoltageControlOuterLoopMaxTapShift;
        return this;
    }

    public boolean isDisym() {
        return disym;
    }

    public OpenLoadFlowParameters setDisym(boolean disym) {
        this.disym = disym;
        return this;
    }

    public static OpenLoadFlowParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static OpenLoadFlowParameters load(PlatformConfig platformConfig) {
        OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();
        platformConfig.getOptionalModuleConfig("open-loadflow-default-parameters")
            .ifPresent(config -> parameters
                .setSlackBusSelectionMode(config.getEnumProperty(SLACK_BUS_SELECTION_MODE_PARAM_NAME, SlackBusSelectionMode.class, SLACK_BUS_SELECTION_MODE_DEFAULT_VALUE))
                .setSlackBusesIds(config.getStringListProperty(SLACK_BUSES_IDS_PARAM_NAME, Collections.emptyList()))
                .setLowImpedanceBranchMode(config.getEnumProperty(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, LowImpedanceBranchMode.class, LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE))
                .setVoltageRemoteControl(config.getBooleanProperty(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE))
                .setThrowsExceptionInCaseOfSlackDistributionFailure(
                        config.getBooleanProperty(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE)
                )
                .setLoadPowerFactorConstant(config.getBooleanProperty(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE))
                .setPlausibleActivePowerLimit(config.getDoubleProperty(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE))
                .setSlackBusPMaxMismatch(config.getDoubleProperty(SLACK_BUS_P_MAX_MISMATCH_NAME, SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE))
                .setVoltagePerReactivePowerControl(config.getBooleanProperty(VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME, VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE))
                .setReactivePowerRemoteControl(config.getBooleanProperty(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE))
                .setMaxIteration(config.getIntProperty(MAX_ITERATION_NAME, NewtonRaphsonParameters.DEFAULT_MAX_ITERATION))
                .setNewtonRaphsonConvEpsPerEq(config.getDoubleProperty(NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME, DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ))
                .setVoltageInitModeOverride(config.getEnumProperty(VOLTAGE_INIT_MODE_OVERRIDE_NAME, VoltageInitModeOverride.class, VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE))
                .setTransformerVoltageControlMode(config.getEnumProperty(TRANSFORMER_VOLTAGE_CONTROL_MODE_NAME, TransformerVoltageControlMode.class, TRANSFORMER_VOLTAGE_CONTROL_MODE_DEFAULT_VALUE))
                .setDcPowerFactor(config.getDoubleProperty(DC_POWER_FACTOR_NAME, DC_POWER_FACTOR_DEFAULT_VALUE))
                .setMinPlausibleTargetVoltage(config.getDoubleProperty(MIN_PLAUSIBLE_TARGET_VOLTAGE_NAME, LfNetworkParameters.MIN_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE))
                .setMaxPlausibleTargetVoltage(config.getDoubleProperty(MAX_PLAUSIBLE_TARGET_VOLTAGE_NAME, LfNetworkParameters.MAX_PLAUSIBLE_TARGET_VOLTAGE_DEFAULT_VALUE))
                .setMinRealisticVoltage(config.getDoubleProperty(MIN_REALISTIC_VOLTAGE_NAME, NewtonRaphsonParameters.DEFAULT_MIN_REALISTIC_VOLTAGE))
                .setMaxRealisticVoltage(config.getDoubleProperty(MAX_REALISTIC_VOLTAGE_NAME, NewtonRaphsonParameters.DEFAULT_MAX_REALISTIC_VOLTAGE))
                .setReactiveRangeCheckMode(config.getEnumProperty(REACTIVE_RANGE_CHECK_MODE_NAME, ReactiveRangeCheckMode.class, LfNetworkParameters.REACTIVE_RANGE_CHECK_MODE_DEFAULT_VALUE))
                .setLowImpedanceThreshold(config.getDoubleProperty(LOW_IMPEDANCE_THRESHOLD_NAME, LfNetworkParameters.LOW_IMPEDANCE_THRESHOLD_DEFAULT_VALUE))
                .setNetworkCacheEnabled(config.getBooleanProperty(NETWORK_CACHE_ENABLED_NAME, NETWORK_CACHE_ENABLED_DEFAULT_VALUE))
                .setSvcVoltageMonitoring(config.getBooleanProperty(SVC_VOLTAGE_MONITORING_NAME, SVC_VOLTAGE_MONITORING_DEFAULT_VALUE))
                .setNetworkCacheEnabled(config.getBooleanProperty(NETWORK_CACHE_ENABLED_NAME, NETWORK_CACHE_ENABLED_DEFAULT_VALUE))
                .setStateVectorScalingMode(config.getEnumProperty(STATE_VECTOR_SCALING_MODE_NAME, StateVectorScalingMode.class, NewtonRaphsonParameters.DEFAULT_STATE_VECTOR_SCALING_MODE))
                .setMaxSlackBusCount(config.getIntProperty(MAX_SLACK_BUS_COUNT_NAME, LfNetworkParameters.DEFAULT_MAX_SLACK_BUS_COUNT))
                .setDebugDir(config.getStringProperty(DEBUG_DIR_PARAM_NAME, LfNetworkParameters.DEBUG_DIR_DEFAULT_VALUE))
                .setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(config.getIntProperty(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME, IncrementalTransformerVoltageControlOuterLoop.DEFAULT_MAX_TAP_SHIFT)));
        return parameters;
    }

    public static OpenLoadFlowParameters load(Map<String, String> properties) {
        return new OpenLoadFlowParameters().update(properties);
    }

    public OpenLoadFlowParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(SLACK_BUS_SELECTION_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusSelectionMode(SlackBusSelectionMode.valueOf(prop)));
        Optional.ofNullable(properties.get(SLACK_BUSES_IDS_PARAM_NAME))
                .ifPresent(prop -> this.setSlackBusesIds(Arrays.asList(prop.split("[:,]"))));
        Optional.ofNullable(properties.get(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME))
                .ifPresent(prop -> this.setLowImpedanceBranchMode(LowImpedanceBranchMode.valueOf(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_REMOTE_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setVoltageRemoteControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME))
                .ifPresent(prop -> this.setThrowsExceptionInCaseOfSlackDistributionFailure(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME))
                .ifPresent(prop -> this.setLoadPowerFactorConstant(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME))
                .ifPresent(prop -> this.setPlausibleActivePowerLimit(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(SLACK_BUS_P_MAX_MISMATCH_NAME))
                .ifPresent(prop -> this.setSlackBusPMaxMismatch(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME))
                .ifPresent(prop -> this.setVoltagePerReactivePowerControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME))
                .ifPresent(prop -> this.setReactivePowerRemoteControl(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(MAX_ITERATION_NAME))
                .ifPresent(prop -> this.setMaxIteration(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME))
                .ifPresent(prop -> this.setNewtonRaphsonConvEpsPerEq(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(VOLTAGE_INIT_MODE_OVERRIDE_NAME))
                .ifPresent(prop -> this.setVoltageInitModeOverride(VoltageInitModeOverride.valueOf(prop)));
        Optional.ofNullable(properties.get(TRANSFORMER_VOLTAGE_CONTROL_MODE_NAME))
                .ifPresent(prop -> this.setTransformerVoltageControlMode(TransformerVoltageControlMode.valueOf(prop)));
        Optional.ofNullable(properties.get(DC_POWER_FACTOR_NAME))
                .ifPresent(prop -> this.setDcPowerFactor(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MIN_PLAUSIBLE_TARGET_VOLTAGE_NAME))
                .ifPresent(prop -> this.setMinPlausibleTargetVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_PLAUSIBLE_TARGET_VOLTAGE_NAME))
                .ifPresent(prop -> this.setMaxPlausibleTargetVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MIN_REALISTIC_VOLTAGE_NAME))
                .ifPresent(prop -> this.setMinRealisticVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(MAX_REALISTIC_VOLTAGE_NAME))
                .ifPresent(prop -> this.setMaxRealisticVoltage(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(REACTIVE_RANGE_CHECK_MODE_NAME))
                .ifPresent(prop -> this.setReactiveRangeCheckMode(ReactiveRangeCheckMode.valueOf(prop)));
        Optional.ofNullable(properties.get(LOW_IMPEDANCE_THRESHOLD_NAME))
                .ifPresent(prop -> this.setLowImpedanceThreshold(Double.parseDouble(prop)));
        Optional.ofNullable(properties.get(NETWORK_CACHE_ENABLED_NAME))
                .ifPresent(prop -> this.setNetworkCacheEnabled(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(SVC_VOLTAGE_MONITORING_NAME))
                .ifPresent(prop -> this.setSvcVoltageMonitoring(Boolean.parseBoolean(prop)));
        Optional.ofNullable(properties.get(STATE_VECTOR_SCALING_MODE_NAME))
                .ifPresent(prop -> this.setStateVectorScalingMode(StateVectorScalingMode.valueOf(prop)));
        Optional.ofNullable(properties.get(MAX_SLACK_BUS_COUNT_NAME))
                .ifPresent(prop -> this.setMaxSlackBusCount(Integer.parseInt(prop)));
        Optional.ofNullable(properties.get(DEBUG_DIR_PARAM_NAME))
                .ifPresent(this::setDebugDir);
        Optional.ofNullable(properties.get(INCREMENTAL_TRANSFORMER_VOLTAGE_CONTROL_OUTER_LOOP_MAX_TAP_SHIFT_PARAM_NAME))
                .ifPresent(prop -> this.setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(Integer.parseInt(prop)));
        return this;
    }

    @Override
    public String toString() {
        return "OpenLoadFlowParameters(" +
                "slackBusSelectionMode=" + slackBusSelectionMode +
                ", slackBusesIds=" + slackBusesIds +
                ", throwsExceptionInCaseOfSlackDistributionFailure=" + throwsExceptionInCaseOfSlackDistributionFailure +
                ", voltageRemoteControl=" + voltageRemoteControl +
                ", lowImpedanceBranchMode=" + lowImpedanceBranchMode +
                ", loadPowerFactorConstant=" + loadPowerFactorConstant +
                ", plausibleActivePowerLimit=" + plausibleActivePowerLimit +
                ", slackBusPMaxMismatch=" + slackBusPMaxMismatch +
                ", voltagePerReactivePowerControl=" + voltagePerReactivePowerControl +
                ", reactivePowerRemoteControl=" + reactivePowerRemoteControl +
                ", maxIteration=" + maxIteration +
                ", newtonRaphsonConvEpsPerEq=" + newtonRaphsonConvEpsPerEq +
                ", voltageInitModeOverride=" + voltageInitModeOverride +
                ", transformerVoltageControlMode=" + transformerVoltageControlMode +
                ", dcPowerFactor=" + dcPowerFactor +
                ", minPlausibleTargetVoltage=" + minPlausibleTargetVoltage +
                ", maxPlausibleTargetVoltage=" + maxPlausibleTargetVoltage +
                ", minRealisticVoltage=" + minRealisticVoltage +
                ", maxRealisticVoltage=" + maxRealisticVoltage +
                ", reactiveRangeCheckMode=" + reactiveRangeCheckMode +
                ", lowImpedanceThreshold=" + lowImpedanceThreshold +
                ", networkCacheEnabled=" + networkCacheEnabled +
                ", svcVoltageMonitoring=" + svcVoltageMonitoring +
                ", stateVectorScalingMode=" + stateVectorScalingMode +
                ", maxSlackBusCount=" + maxSlackBusCount +
                ", debugDir=" + debugDir +
                ", incrementalTransformerVoltageControlOuterLoopMaxTapShift=" + incrementalTransformerVoltageControlOuterLoopMaxTapShift +
                ')';
    }

    public static OpenLoadFlowParameters get(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenLoadFlowParameters();
        }
        return parametersExt;
    }

    private static OpenLoadFlowParameters create(LoadFlowParameters parameters, Supplier<OpenLoadFlowParameters> parametersExtSupplier) {
        Objects.requireNonNull(parameters);
        OpenLoadFlowParameters parametersExt = parametersExtSupplier.get();
        parameters.addExtension(OpenLoadFlowParameters.class, parametersExt);
        return parametersExt;
    }

    public static OpenLoadFlowParameters create(LoadFlowParameters parameters) {
        return create(parameters, OpenLoadFlowParameters::new);
    }

    public static OpenLoadFlowParameters load(LoadFlowParameters parameters) {
        return create(parameters, OpenLoadFlowParameters::load);
    }

    public static void logDc(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        LOGGER.info("Direct current: {}", parameters.isDc());
        LOGGER.info("Slack bus selection mode: {}", parametersExt.getSlackBusSelectionMode());
        LOGGER.info("Use transformer ratio: {}", parameters.isDcUseTransformerRatio());
        LOGGER.info("Distributed slack: {}", parameters.isDistributedSlack());
        LOGGER.info("Balance type: {}", parameters.getBalanceType());
        LOGGER.info("Plausible active power limit: {}", parametersExt.getPlausibleActivePowerLimit());
        LOGGER.info("Connected component mode: {}", parameters.getConnectedComponentMode());
        LOGGER.info("DC power factor: {}", parametersExt.getDcPowerFactor());
        LOGGER.info("Debug directory: {}", parametersExt.getDebugDir());
    }

    /**
     * Log parameters interesting for AC calculation
     */
    public static void logAc(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt) {
        LOGGER.info("Direct current: {}", parameters.isDc());
        LOGGER.info("Slack bus selection mode: {}", parametersExt.getSlackBusSelectionMode());
        LOGGER.info("Voltage initialization mode: {}", parameters.getVoltageInitMode());
        LOGGER.info("Voltage initialization mode override: {}", parametersExt.getVoltageInitModeOverride());
        LOGGER.info("Distributed slack: {}", parameters.isDistributedSlack());
        LOGGER.info("Balance type: {}", parameters.getBalanceType());
        LOGGER.info("Reactive limits: {}", parameters.isUseReactiveLimits());
        LOGGER.info("Voltage remote control: {}", parametersExt.hasVoltageRemoteControl());
        LOGGER.info("Phase control: {}", parameters.isPhaseShifterRegulationOn());
        LOGGER.info("Split shunt admittance: {}", parameters.isTwtSplitShuntAdmittance());
        LOGGER.info("Transformer voltage control: {}", parameters.isTransformerVoltageControlOn());
        LOGGER.info("Load power factor constant: {}", parametersExt.isLoadPowerFactorConstant());
        LOGGER.info("Plausible active power limit: {}", parametersExt.getPlausibleActivePowerLimit());
        LOGGER.info("Slack bus Pmax mismatch: {}", parametersExt.getSlackBusPMaxMismatch());
        LOGGER.info("Connected component mode: {}", parameters.getConnectedComponentMode());
        LOGGER.info("Voltage per reactive power control: {}", parametersExt.isVoltagePerReactivePowerControl());
        LOGGER.info("Reactive Power Remote control: {}", parametersExt.hasReactivePowerRemoteControl());
        LOGGER.info("Shunt voltage control: {}", parameters.isShuntCompensatorVoltageControlOn());
        LOGGER.info("Hvdc Ac emulation: {}", parameters.isHvdcAcEmulation());
        LOGGER.info("Min plausible target voltage: {}", parametersExt.getMinPlausibleTargetVoltage());
        LOGGER.info("Max plausible target voltage: {}", parametersExt.getMaxPlausibleTargetVoltage());
        LOGGER.info("Min realistic voltage: {}", parametersExt.getMinRealisticVoltage());
        LOGGER.info("Max realistic voltage: {}", parametersExt.getMaxRealisticVoltage());
        LOGGER.info("Reactive range check mode: {}", parametersExt.getReactiveRangeCheckMode());
        LOGGER.info("Network cache enabled: {}", parametersExt.isNetworkCacheEnabled());
        LOGGER.info("Static var compensator voltage monitoring: {}", parametersExt.isSvcVoltageMonitoring());
        LOGGER.info("State vector scaling mode: {}", parametersExt.getStateVectorScalingMode());
        LOGGER.info("Max slack bus count: {}", parametersExt.getMaxSlackBusCount());
        LOGGER.info("Debug directory: {}", parametersExt.getDebugDir());
        LOGGER.info("Incremental transformer voltage control outer loop max tap shift: {}", parametersExt.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift());
    }

    static VoltageInitializer getVoltageInitializer(LoadFlowParameters parameters, LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        switch (parameters.getVoltageInitMode()) {
            case UNIFORM_VALUES:
                return new UniformValueVoltageInitializer();
            case PREVIOUS_VALUES:
                return new PreviousValueVoltageInitializer();
            case DC_VALUES:
                return new DcValueVoltageInitializer(networkParameters, parameters.isDistributedSlack(), parameters.getBalanceType(), parameters.isDcUseTransformerRatio(), matrixFactory);
            default:
                throw new UnsupportedOperationException("Unsupported voltage init mode: " + parameters.getVoltageInitMode());
        }
    }

    static VoltageInitializer getExtendedVoltageInitializer(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                            LfNetworkParameters networkParameters, MatrixFactory matrixFactory) {
        switch (parametersExt.getVoltageInitModeOverride()) {
            case NONE:
                return getVoltageInitializer(parameters, networkParameters, matrixFactory);

            case VOLTAGE_MAGNITUDE:
                return new VoltageMagnitudeInitializer(parameters.isTransformerVoltageControlOn(), matrixFactory, networkParameters.getLowImpedanceThreshold());

            case FULL_VOLTAGE:
                return new FullVoltageInitializer(new VoltageMagnitudeInitializer(parameters.isTransformerVoltageControlOn(), matrixFactory, networkParameters.getLowImpedanceThreshold()),
                        new DcValueVoltageInitializer(networkParameters,
                                                      parameters.isDistributedSlack(),
                                                      parameters.getBalanceType(),
                                                      parameters.isDcUseTransformerRatio(),
                                                      matrixFactory));

            default:
                throw new PowsyblException("Unknown voltage init mode override: " + parametersExt.getVoltageInitModeOverride());
        }
    }

    static LfNetworkParameters getNetworkParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                    SlackBusSelector slackBusSelector, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                    boolean breakers) {
        return new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(parametersExt.hasVoltageRemoteControl())
                .setMinImpedance(parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                .setTwtSplitShuntAdmittance(parameters.isTwtSplitShuntAdmittance())
                .setBreakers(breakers)
                .setPlausibleActivePowerLimit(parametersExt.getPlausibleActivePowerLimit())
                .setComputeMainConnectedComponentOnly(parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN)
                .setCountriesToBalance(parameters.getCountriesToBalance())
                .setDistributedOnConformLoad(parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(parameters.isPhaseShifterRegulationOn())
                .setTransformerVoltageControl(parameters.isTransformerVoltageControlOn())
                .setVoltagePerReactivePowerControl(parametersExt.isVoltagePerReactivePowerControl())
                .setReactivePowerRemoteControl(parametersExt.hasReactivePowerRemoteControl())
                .setDc(parameters.isDc())
                .setShuntVoltageControl(parameters.isShuntCompensatorVoltageControlOn())
                .setReactiveLimits(parameters.isUseReactiveLimits())
                .setHvdcAcEmulation(parameters.isHvdcAcEmulation())
                .setMinPlausibleTargetVoltage(parametersExt.getMinPlausibleTargetVoltage())
                .setMaxPlausibleTargetVoltage(parametersExt.getMaxPlausibleTargetVoltage())
                .setReactiveRangeCheckMode(parametersExt.getReactiveRangeCheckMode())
                .setLowImpedanceThreshold(parametersExt.getLowImpedanceThreshold())
                .setSvcVoltageMonitoring(parametersExt.isSvcVoltageMonitoring())
                .setMaxSlackBusCount(parametersExt.getMaxSlackBusCount())
                .setDebugDir(parametersExt.getDebugDir());
    }

    public static AcLoadFlowParameters createAcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory) {
        return createAcParameters(network, parameters, parametersExt, matrixFactory, connectivityFactory, false, false);
    }

    public static AcLoadFlowParameters createAcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean breakers, boolean forceA1Var) {
        AcLoadFlowParameters acParameters = createAcParameters(parameters, parametersExt, matrixFactory, connectivityFactory, breakers, forceA1Var);
        if (parameters.isReadSlackBus()) {
            acParameters.getNetworkParameters().setSlackBusSelector(new NetworkSlackBusSelector(network, acParameters.getNetworkParameters().getSlackBusSelector()));
        }
        return acParameters;
    }

    public static AcLoadFlowParameters createAcParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean breakers, boolean forceA1Var) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(parametersExt.getSlackBusSelectionMode(), parametersExt.getSlackBusesIds(), parametersExt.getPlausibleActivePowerLimit());

        var networkParameters = getNetworkParameters(parameters, parametersExt, slackBusSelector, connectivityFactory, breakers);

        var equationSystemCreationParameters = new AcEquationSystemCreationParameters(forceA1Var);

        VoltageInitializer voltageInitializer = getExtendedVoltageInitializer(parameters, parametersExt, networkParameters, matrixFactory);

        var newtonRaphsonParameters = new NewtonRaphsonParameters()
                .setStoppingCriteria(new DefaultNewtonRaphsonStoppingCriteria(parametersExt.getNewtonRaphsonConvEpsPerEq()))
                .setMaxIteration(parametersExt.getMaxIteration())
                .setMinRealisticVoltage(parametersExt.getMinRealisticVoltage())
                .setMaxRealisticVoltage(parametersExt.getMaxRealisticVoltage())
                .setStateVectorScalingMode(parametersExt.getStateVectorScalingMode());

        OuterLoopConfig outerLoopConfig = OuterLoopConfig.findOuterLoopConfig(new DefaultOuterLoopConfig());
        List<OuterLoop> outerLoops = outerLoopConfig.configure(parameters, parametersExt);

        return new AcLoadFlowParameters(networkParameters,
                                        equationSystemCreationParameters,
                                        newtonRaphsonParameters,
                                        outerLoops,
                                        matrixFactory,
                                        voltageInitializer,
                                        parametersExt.isDisym());
    }

    public static DcLoadFlowParameters createDcParameters(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean forcePhaseControlOffAndAddAngle1Var) {
        var dcParameters = createDcParameters(parameters, parametersExt, matrixFactory, connectivityFactory, forcePhaseControlOffAndAddAngle1Var);
        if (parameters.isReadSlackBus()) {
            dcParameters.getNetworkParameters().setSlackBusSelector(new NetworkSlackBusSelector(network, dcParameters.getNetworkParameters().getSlackBusSelector()));
        }
        return dcParameters;
    }

    public static DcLoadFlowParameters createDcParameters(LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                                                          MatrixFactory matrixFactory, GraphConnectivityFactory<LfBus, LfBranch> connectivityFactory,
                                                          boolean forcePhaseControlOffAndAddAngle1Var) {
        SlackBusSelector slackBusSelector = SlackBusSelector.fromMode(parametersExt.getSlackBusSelectionMode(), parametersExt.getSlackBusesIds(), parametersExt.getPlausibleActivePowerLimit());

        var networkParameters = new LfNetworkParameters()
                .setSlackBusSelector(slackBusSelector)
                .setConnectivityFactory(connectivityFactory)
                .setGeneratorVoltageRemoteControl(false)
                .setMinImpedance(parametersExt.getLowImpedanceBranchMode() == OpenLoadFlowParameters.LowImpedanceBranchMode.REPLACE_BY_MIN_IMPEDANCE_LINE)
                .setTwtSplitShuntAdmittance(false)
                .setBreakers(false)
                .setPlausibleActivePowerLimit(parametersExt.getPlausibleActivePowerLimit())
                .setComputeMainConnectedComponentOnly(parameters.getConnectedComponentMode() == LoadFlowParameters.ConnectedComponentMode.MAIN)
                .setCountriesToBalance(parameters.getCountriesToBalance())
                .setDistributedOnConformLoad(parameters.isDistributedSlack() && parameters.getBalanceType() == LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setPhaseControl(false)
                .setTransformerVoltageControl(false)
                .setVoltagePerReactivePowerControl(false)
                .setReactivePowerRemoteControl(false)
                .setDc(true)
                .setShuntVoltageControl(false)
                .setReactiveLimits(false)
                .setHvdcAcEmulation(false) // FIXME
                .setMinPlausibleTargetVoltage(parametersExt.getMinPlausibleTargetVoltage())
                .setMaxPlausibleTargetVoltage(parametersExt.getMaxPlausibleTargetVoltage())
                .setReactiveRangeCheckMode(ReactiveRangeCheckMode.MAX) // not useful for DC.
                .setLowImpedanceThreshold(parametersExt.getLowImpedanceThreshold())
                .setSvcVoltageMonitoring(false)
                .setMaxSlackBusCount(1);

        var equationSystemCreationParameters = new DcEquationSystemCreationParameters(true,
                                                                                      forcePhaseControlOffAndAddAngle1Var,
                                                                                      parameters.isDcUseTransformerRatio());

        return new DcLoadFlowParameters(networkParameters,
                                        equationSystemCreationParameters,
                                        matrixFactory,
                                        parameters.isDistributedSlack(),
                                        parameters.getBalanceType(),
                                        true);
    }

    public static boolean equals(LoadFlowParameters parameters1, LoadFlowParameters parameters2) {
        Objects.requireNonNull(parameters1);
        Objects.requireNonNull(parameters2);
        boolean equals = parameters1.getVoltageInitMode() == parameters2.getVoltageInitMode() &&
                parameters1.isTransformerVoltageControlOn() == parameters2.isTransformerVoltageControlOn() &&
                parameters1.isUseReactiveLimits() == parameters2.isUseReactiveLimits() &&
                parameters1.isPhaseShifterRegulationOn() == parameters2.isPhaseShifterRegulationOn() &&
                parameters1.isTwtSplitShuntAdmittance() == parameters2.isTwtSplitShuntAdmittance() &&
                parameters1.isShuntCompensatorVoltageControlOn() == parameters2.isShuntCompensatorVoltageControlOn() &&
                parameters1.isReadSlackBus() == parameters2.isReadSlackBus() &&
                parameters1.isWriteSlackBus() == parameters2.isWriteSlackBus() &&
                parameters1.isDc() == parameters2.isDc() &&
                parameters1.isDistributedSlack() == parameters2.isDistributedSlack() &&
                parameters1.getBalanceType() == parameters2.getBalanceType() &&
                parameters1.isDcUseTransformerRatio() == parameters2.isDcUseTransformerRatio() &&
                parameters1.getCountriesToBalance().equals(parameters2.getCountriesToBalance()) &&
                parameters1.getConnectedComponentMode() == parameters2.getConnectedComponentMode() &&
                parameters1.isHvdcAcEmulation() == parameters2.isHvdcAcEmulation();
        if (!equals) {
            return false;
        }

        OpenLoadFlowParameters extension1 = parameters1.getExtension(OpenLoadFlowParameters.class);
        OpenLoadFlowParameters extension2 = parameters2.getExtension(OpenLoadFlowParameters.class);
        if (extension1 == null && extension2 == null) {
            return true;
        }
        if (extension1 == null) {
            return false;
        }
        if (extension2 == null) {
            return false;
        }

        return extension1.getSlackBusSelectionMode() == extension2.getSlackBusSelectionMode() &&
                extension1.getSlackBusesIds().equals(extension2.getSlackBusesIds()) &&
                extension1.isThrowsExceptionInCaseOfSlackDistributionFailure() == extension2.isThrowsExceptionInCaseOfSlackDistributionFailure() &&
                extension1.hasVoltageRemoteControl() == extension2.hasVoltageRemoteControl() &&
                extension1.getLowImpedanceBranchMode() == extension2.getLowImpedanceBranchMode() &&
                extension1.isLoadPowerFactorConstant() == extension2.isLoadPowerFactorConstant() &&
                extension1.getPlausibleActivePowerLimit() == extension2.getPlausibleActivePowerLimit() &&
                extension1.getSlackBusPMaxMismatch() == extension2.getSlackBusPMaxMismatch() &&
                extension1.isVoltagePerReactivePowerControl() == extension2.isVoltagePerReactivePowerControl() &&
                extension1.hasReactivePowerRemoteControl() == extension2.hasReactivePowerRemoteControl() &&
                extension1.getMaxIteration() == extension2.getMaxIteration() &&
                extension1.getNewtonRaphsonConvEpsPerEq() == extension2.getNewtonRaphsonConvEpsPerEq() &&
                extension1.getVoltageInitModeOverride() == extension2.getVoltageInitModeOverride() &&
                extension1.getTransformerVoltageControlMode() == extension2.getTransformerVoltageControlMode() &&
                extension1.getDcPowerFactor() == extension2.getDcPowerFactor() &&
                extension1.getMinPlausibleTargetVoltage() == extension2.getMinPlausibleTargetVoltage() &&
                extension1.getMaxPlausibleTargetVoltage() == extension2.getMaxPlausibleTargetVoltage() &&
                extension1.getMinRealisticVoltage() == extension2.getMinRealisticVoltage() &&
                extension1.getMaxRealisticVoltage() == extension2.getMaxRealisticVoltage() &&
                extension1.getReactiveRangeCheckMode() == extension2.getReactiveRangeCheckMode() &&
                extension1.getLowImpedanceThreshold() == extension2.getLowImpedanceThreshold() &&
                extension1.isNetworkCacheEnabled() == extension2.isNetworkCacheEnabled() &&
                extension1.isSvcVoltageMonitoring() == extension2.isSvcVoltageMonitoring() &&
                extension1.getStateVectorScalingMode() == extension2.getStateVectorScalingMode() &&
                extension1.getMaxSlackBusCount() == extension2.getMaxSlackBusCount() &&
                Objects.equals(extension1.getDebugDir(), extension2.getDebugDir()) &&
                extension1.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift() == extension2.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift();
    }

    public static LoadFlowParameters clone(LoadFlowParameters parameters) {
        Objects.requireNonNull(parameters);
        LoadFlowParameters parameters2 = new LoadFlowParameters(parameters.getVoltageInitMode(),
                                                                parameters.isTransformerVoltageControlOn(),
                                                                parameters.isUseReactiveLimits(),
                                                                parameters.isPhaseShifterRegulationOn(),
                                                                parameters.isTwtSplitShuntAdmittance(),
                                                                parameters.isShuntCompensatorVoltageControlOn(),
                                                                parameters.isReadSlackBus(),
                                                                parameters.isWriteSlackBus(),
                                                                parameters.isDc(),
                                                                parameters.isDistributedSlack(),
                                                                parameters.getBalanceType(),
                                                                parameters.isDcUseTransformerRatio(),
                                                                new HashSet<>(parameters.getCountriesToBalance()),
                                                                parameters.getConnectedComponentMode(),
                                                                parameters.isHvdcAcEmulation());

        OpenLoadFlowParameters extension = parameters.getExtension(OpenLoadFlowParameters.class);
        if (extension != null) {
            OpenLoadFlowParameters extension2 = new OpenLoadFlowParameters()
                    .setSlackBusSelectionMode(extension.getSlackBusSelectionMode())
                    .setSlackBusesIds(new ArrayList<>(extension.getSlackBusesIds()))
                    .setThrowsExceptionInCaseOfSlackDistributionFailure(extension.isThrowsExceptionInCaseOfSlackDistributionFailure())
                    .setVoltageRemoteControl(extension.hasVoltageRemoteControl())
                    .setLowImpedanceBranchMode(extension.getLowImpedanceBranchMode())
                    .setLoadPowerFactorConstant(extension.isLoadPowerFactorConstant())
                    .setPlausibleActivePowerLimit(extension.getPlausibleActivePowerLimit())
                    .setSlackBusPMaxMismatch(extension.getSlackBusPMaxMismatch())
                    .setVoltagePerReactivePowerControl(extension.isVoltagePerReactivePowerControl())
                    .setReactivePowerRemoteControl(extension.hasReactivePowerRemoteControl())
                    .setMaxIteration(extension.getMaxIteration())
                    .setNewtonRaphsonConvEpsPerEq(extension.getNewtonRaphsonConvEpsPerEq())
                    .setVoltageInitModeOverride(extension.getVoltageInitModeOverride())
                    .setTransformerVoltageControlMode(extension.getTransformerVoltageControlMode())
                    .setDcPowerFactor(extension.getDcPowerFactor())
                    .setMinPlausibleTargetVoltage(extension.getMinPlausibleTargetVoltage())
                    .setMaxPlausibleTargetVoltage(extension.getMaxPlausibleTargetVoltage())
                    .setMinRealisticVoltage(extension.getMinRealisticVoltage())
                    .setMaxRealisticVoltage(extension.getMaxRealisticVoltage())
                    .setReactiveRangeCheckMode(extension.getReactiveRangeCheckMode())
                    .setLowImpedanceThreshold(extension.getLowImpedanceThreshold())
                    .setNetworkCacheEnabled(extension.isNetworkCacheEnabled())
                    .setSvcVoltageMonitoring(extension.isSvcVoltageMonitoring())
                    .setStateVectorScalingMode(extension.getStateVectorScalingMode())
                    .setMaxSlackBusCount(extension.getMaxSlackBusCount())
                    .setDebugDir(extension.getDebugDir())
                    .setIncrementalTransformerVoltageControlOuterLoopMaxTapShift(extension.getIncrementalTransformerVoltageControlOuterLoopMaxTapShift());
            if (extension2 != null) {
                parameters2.addExtension(OpenLoadFlowParameters.class, extension2);
            }
        }

        return parameters2;
    }
}

