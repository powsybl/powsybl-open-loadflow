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
import com.powsybl.openloadflow.network.SlackBusSelectionMode;

import java.util.Objects;

import static com.powsybl.openloadflow.util.ParameterConstants.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private SlackBusSelectionMode slackBusSelectionMode = SLACK_BUS_SELECTION_DEFAULT_VALUE;

    private String slackBusId = null;

    private boolean throwsExceptionInCaseOfSlackDistributionFailure = THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE;

    private boolean voltageRemoteControl = VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE;

    private LowImpedanceBranchMode lowImpedanceBranchMode = LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE;

    public enum LowImpedanceBranchMode {
        REPLACE_BY_ZERO_IMPEDANCE_LINE,
        REPLACE_BY_MIN_IMPEDANCE_LINE
    }

    private boolean loadPowerFactorConstant = LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE;

    private boolean dcUseTransformerRatio = DC_USE_TRANSFORMER_RATIO_DEFAULT_VALUE;

    private double plausibleActivePowerLimit = PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE;

    private boolean addRatioToLinesWithDifferentNominalVoltageAtBothEnds = ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE;

    private double slackBusPMaxMismatch = SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE;

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

    public String getSlackBusId() {
        return slackBusId;
    }

    public OpenLoadFlowParameters setSlackBusId(String slackBusId) {
        this.slackBusId = slackBusId;
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

    public boolean isDcUseTransformerRatio() {
        return dcUseTransformerRatio;
    }

    public OpenLoadFlowParameters setDcUseTransformerRatio(boolean dcUseTransformerRatio) {
        this.dcUseTransformerRatio = dcUseTransformerRatio;
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

    public static OpenLoadFlowParameters load() {
        return new OpenLoadFlowConfigLoader().load(PlatformConfig.defaultConfig());
    }

    @Override
    public String toString() {
        return "OpenLoadFlowParameters(" +
                "slackBusSelectionMode=" + slackBusSelectionMode +
                ", slackBusId='" + slackBusId + "'" +
                ", throwsExceptionInCaseOfSlackDistributionFailure=" + throwsExceptionInCaseOfSlackDistributionFailure +
                ", voltageRemoteControl=" + voltageRemoteControl +
                ", lowImpedanceBranchMode=" + lowImpedanceBranchMode +
                ", loadPowerFactorConstant=" + loadPowerFactorConstant +
                ", dcUseTransformerRatio=" + dcUseTransformerRatio +
                ", plausibleActivePowerLimit=" + plausibleActivePowerLimit +
                ", addRatioToLinesWithDifferentNominalVoltageAtBothEnds=" + addRatioToLinesWithDifferentNominalVoltageAtBothEnds +
                ')';
    }

    @AutoService(LoadFlowParameters.ConfigLoader.class)
    public static class OpenLoadFlowConfigLoader implements LoadFlowParameters.ConfigLoader<OpenLoadFlowParameters> {

        @Override
        public OpenLoadFlowParameters load(PlatformConfig platformConfig) {
            OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();

            platformConfig.getOptionalModuleConfig("open-loadflow-default-parameters")
                .ifPresent(config -> parameters
                        .setSlackBusSelectionMode(config.getEnumProperty(SLACK_BUS_SELECTION_PARAM_NAME, SlackBusSelectionMode.class, SLACK_BUS_SELECTION_DEFAULT_VALUE))
                        .setSlackBusId(config.getStringProperty(SLACK_BUS_ID_PARAM_NAME, null))
                        .setLowImpedanceBranchMode(config.getEnumProperty(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, LowImpedanceBranchMode.class, LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE))
                        .setVoltageRemoteControl(config.getBooleanProperty(VOLTAGE_REMOTE_CONTROL_PARAM_NAME, VOLTAGE_REMOTE_CONTROL_DEFAULT_VALUE))
                        .setThrowsExceptionInCaseOfSlackDistributionFailure(
                                config.getBooleanProperty(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE)
                        )
                        .setLoadPowerFactorConstant(config.getBooleanProperty(LOAD_POWER_FACTOR_CONSTANT_PARAM_NAME, LOAD_POWER_FACTOR_CONSTANT_DEFAULT_VALUE))
                        .setDcUseTransformerRatio(config.getBooleanProperty(DC_USE_TRANSFORMER_RATIO_PARAM_NAME, DC_USE_TRANSFORMER_RATIO_DEFAULT_VALUE))
                        .setPlausibleActivePowerLimit(config.getDoubleProperty(PLAUSIBLE_ACTIVE_POWER_LIMIT_PARAM_NAME, PLAUSIBLE_ACTIVE_POWER_LIMIT_DEFAULT_VALUE))
                        .setAddRatioToLinesWithDifferentNominalVoltageAtBothEnds(config.getBooleanProperty(ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_NAME, ADD_RATIO_TO_LINES_WITH_DIFFERENT_NOMINAL_VOLTAGE_AT_BOTH_ENDS_DEFAULT_VALUE))
                        .setSlackBusPMaxMismatch(config.getDoubleProperty(SLACK_BUS_P_MAX_MISMATCH_NAME, SLACK_BUS_P_MAX_MISMATCH_DEFAULT_VALUE))
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

}
