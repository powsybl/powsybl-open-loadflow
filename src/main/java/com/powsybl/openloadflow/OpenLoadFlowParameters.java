/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.auto.service.AutoService;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.DefaultNewtonRaphsonStoppingCriteria;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonParameters;
import com.powsybl.openloadflow.network.LfNetworkParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    public static final SlackBusSelectionMode SLACK_BUS_SELECTION_DEFAULT_VALUE = SlackBusSelectionMode.MOST_MESHED;

    public static final LowImpedanceBranchMode LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE = LowImpedanceBranchMode.REPLACE_BY_ZERO_IMPEDANCE_LINE;

    public static final boolean THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE = false;

    public static final boolean VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE = true;

    public static final boolean REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE = false;

    public static final boolean LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE = false;

    public static final boolean ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE = false;

    /**
     * Slack bus maximum active power mismatch in MW: 1 Mw => 10^-2 in p.u
     */
    public static final double SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE = 1.0;

    public static final boolean VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE = false;

    public enum VoltageInitModeOverride {
        NONE,
        VOLTAGE_MAGNITUDE,
        FULL_VOLTAGE
    }

    public static final VoltageInitModeOverride VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE = VoltageInitModeOverride.NONE;

    private SlackBusSelectionMode slackBusSelectionMode = SLACK_BUS_SELECTION_DEFAULT_VALUE;

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

    private boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds = ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE;

    private double slackBusPMaxMismatch = SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE;

    private boolean voltagePerReactivePowerControl = VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE;

    private boolean reactivePowerRemoteControl = REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE;

    private int maxIteration = NewtonRaphsonParameters.DEFAULT_MAX_ITERATION;

    private double newtonRaphsonConvEpsPerEq = DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ;

    private VoltageInitModeOverride voltageInitModeOverride = VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE;

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

    public boolean isAddRatioToLinesWithDifferentNominalVoltageAtBothEnds() {
        return addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
    }

    public OpenLoadFlowParameters setAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds) {
        this.addRatioToLinesWithDifferentNominalVoltageAtBothEnds = addRatioToLinesWithDifferentNominalVoltageAtBothEnds;
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

    public static OpenLoadFlowParameters load() {
        return new OpenLoadFlowConfigLoader().load(PlatformConfig.defaultConfig());
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
                ", addRatioToLinesWithDifferentNominalVoltageAtBothEnds=" + addRatioToLinesWithDifferentNominalVoltageAtBothEnds +
                ", slackBusPMaxMismatch=" + slackBusPMaxMismatch +
                ", voltagePerReactivePowerControl=" + voltagePerReactivePowerControl +
                ", reactivePowerRemoteControl=" + reactivePowerRemoteControl +
                ", maxIteration=" + maxIteration +
                ", newtonRaphsonConvEpsPerEq=" + newtonRaphsonConvEpsPerEq +
                ", voltageInitModeOverride=" + voltageInitModeOverride +
                ')';
    }

    @AutoService(LoadFlowParameters.ConfigLoader.class)
    public static class OpenLoadFlowConfigLoader implements LoadFlowParameters.ConfigLoader<OpenLoadFlowParameters> {

        public static final String SLACK_BUS_SELECTION_PARAM_NAME = "slackBusSelectionMode";

        public static final String SLACK_BUSES_IDS_PARAM_NAME = "slackBusesIds";

        public static final String THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME = "throwsExceptionInCaseOfSlackDistributionFailure";

        public static final String VOLTAGE_REMOTE_CONTROL_PARAM_NAME = "voltageRemoteControl";

        public static final String REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME = "reactivePowerRemoteControl";

        public static final String LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME = "lowImpedanceBranchMode";

        public static final String LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME = "loadPowerFactorConstant";

        public static final String PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME = "plausibleActivePowerLimit";

        public static final String ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_NAME = "addRatioToLinesWithDifferentNominalVoltageAtBothEnds";

        public static final String SLACK_BUS_P_MAX_MISMATCH_NAME = "slackBusPMaxMismatch";

        public static final String VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME = "voltagePerReactivePowerControl";

        public static final String MAX_ITERATION_NAME = "maxIteration";

        public static final String NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME = "newtonRaphsonConvEpsPerEq";

        public static final String VOLTAGE_INIT_MODE_OVERRIDE_NAME = "voltageInitModeOverride";

        @Override
        public OpenLoadFlowParameters load(PlatformConfig platformConfig) {
            OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();

            platformConfig.getOptionalModuleConfig("open-loadflow-default-parameters")
                .ifPresent(config -> parameters
                        .setSlackBusSelectionMode(config.getEnumProperty(SLACK_BUS_SELECTION_PARAM_NAME, SlackBusSelectionMode.class, SLACK_BUS_SELECTION_DEFAULT_VALUE))
                        .setSlackBusesIds(config.getStringListProperty(SLACK_BUSES_IDS_PARAM_NAME, Collections.emptyList()))
                        .setLowImpedanceBranchMode(config.getEnumProperty(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, LowImpedanceBranchMode.class, LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE))
                        .setVoltageRemoteControl(config.getBooleanProperty(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE))
                        .setThrowsExceptionInCaseOfSlackDistributionFailure(
                                config.getBooleanProperty(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE)
                        )
                        .setLoadPowerFactorConstant(config.getBooleanProperty(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE))
                        .setPlausibleActivePowerLimit(config.getDoubleProperty(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, LfNetworkParameters.PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE))
                        .setAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(config.getBooleanProperty(ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_NAME, ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE))
                        .setSlackBusPMaxMismatch(config.getDoubleProperty(SLACK_BUS_P_MAX_MISMATCH_NAME, SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE))
                        .setVoltagePerReactivePowerControl(config.getBooleanProperty(VOLTAGE_PER_REACTIVE_POWER_CONTROL_NAME, VOLTAGE_PER_REACTIVE_POWER_CONTROL_DEFAULT_VALUE))
                        .setReactivePowerRemoteControl(config.getBooleanProperty(REACTIVE_POWER_REMOTE_CONTROL_PARAM_NAME, REACTIVE_POWER_REMOTE_CONTROL_DEFAULT_VALUE))
                        .setMaxIteration(config.getIntProperty(MAX_ITERATION_NAME, NewtonRaphsonParameters.DEFAULT_MAX_ITERATION))
                        .setNewtonRaphsonConvEpsPerEq(config.getDoubleProperty(NEWTON_RAPHSON_CONV_EPS_PER_EQ_NAME, DefaultNewtonRaphsonStoppingCriteria.DEFAULT_CONV_EPS_PER_EQ))
                        .setVoltageInitModeOverride(config.getEnumProperty(VOLTAGE_INIT_MODE_OVERRIDE_NAME, VoltageInitModeOverride.class, VOLTAGE_INIT_MODE_OVERRIDE_DEFAULT_VALUE))
                );
            return parameters;
        }

        @Override
        public String getExtensionName() {
            return "open-load-flow-parameters";
        }

        @Override
        public String getCategoryName() {
            return "loadflow-parameters";
        }

        @Override
        public Class<? super OpenLoadFlowParameters> getExtensionClass() {
            return OpenLoadFlowParameters.class;
        }
    }

    public static OpenLoadFlowParameters get(LoadFlowParameters parameters) {
        OpenLoadFlowParameters parametersExt = parameters.getExtension(OpenLoadFlowParameters.class);
        if (parametersExt == null) {
            parametersExt = OpenLoadFlowParameters.load();
        }
        return parametersExt;
    }
}
