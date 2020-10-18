/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.google.auto.service.AutoService;
import com.powsybl.commons.config.ModuleConfig;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.nr.AcLoadFlowObserver;
import com.powsybl.openloadflow.network.SlackBusSelector;
import com.powsybl.openloadflow.network.SlackBusSelectorParametersReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import static com.powsybl.openloadflow.util.ParameterConstants.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenLoadFlowParameters extends AbstractExtension<LoadFlowParameters> {

    private SlackBusSelector slackBusSelector = SLACK_BUS_SELECTOR_DEFAULT_VALUE;

    private boolean distributedSlack = DISTRIBUTED_SLACK_DEFAULT_VALUE;

    private boolean throwsExceptionInCaseOfSlackDistributionFailure = THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE;

    private BalanceType balanceType = BALANCE_TYPE_DEFAULT_VALUE;

    private boolean dc = DC_DEFAULT_VALUE;

    private boolean voltageRemoteControl = VOLTAGE_REMOTE_CONTROLE_DEFAULT_VALUE;

    private LowImpedanceBranchMode lowImpedanceBranchMode = LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE;

    public enum BalanceType {
        PROPORTIONAL_TO_GENERATION_P, // Not implemented yet.
        PROPORTIONAL_TO_GENERATION_P_MAX,
        PROPORTIONAL_TO_LOAD,
        PROPORTIONAL_TO_CONFORM_LOAD,
    }

    public enum LowImpedanceBranchMode {
        REPLACE_BY_ZERO_IMPEDANCE_LINE,
        REPLACE_BY_MIN_IMPEDANCE_LINE
    }

    private final List<AcLoadFlowObserver> additionalObservers = new ArrayList<>();

    @Override
    public String getName() {
        return "SimpleLoadFlowParameters";
    }

    public SlackBusSelector getSlackBusSelector() {
        return slackBusSelector;
    }

    public OpenLoadFlowParameters setSlackBusSelector(SlackBusSelector slackBusSelector) {
        this.slackBusSelector = Objects.requireNonNull(slackBusSelector);
        return this;
    }

    public boolean isDistributedSlack() {
        return distributedSlack;
    }

    public OpenLoadFlowParameters setDistributedSlack(boolean distributedSlack) {
        this.distributedSlack = distributedSlack;
        return this;
    }

    public boolean isThrowsExceptionInCaseOfSlackDistributionFailure() {
        return throwsExceptionInCaseOfSlackDistributionFailure;
    }

    public OpenLoadFlowParameters setThrowsExceptionInCaseOfSlackDistributionFailure(boolean throwsExceptionInCaseOfSlackDistributionFailure) {
        this.throwsExceptionInCaseOfSlackDistributionFailure = throwsExceptionInCaseOfSlackDistributionFailure;
        return this;
    }

    public OpenLoadFlowParameters setBalanceType(BalanceType balanceType) {
        this.balanceType = Objects.requireNonNull(balanceType);
        return this;
    }

    public BalanceType getBalanceType() {
        return balanceType; }

    public boolean isDc() {
        return dc;
    }

    public OpenLoadFlowParameters setDc(boolean dc) {
        this.dc = dc;
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

    public List<AcLoadFlowObserver> getAdditionalObservers() {
        return additionalObservers;
    }

    public static OpenLoadFlowParameters load() {
        return new OpenLoadFlowConfigLoader().load(PlatformConfig.defaultConfig());
    }

    @AutoService(LoadFlowParameters.ConfigLoader.class)
    public static class OpenLoadFlowConfigLoader implements LoadFlowParameters.ConfigLoader<OpenLoadFlowParameters> {

        @Override
        public OpenLoadFlowParameters load(PlatformConfig platformConfig) {
            OpenLoadFlowParameters parameters = new OpenLoadFlowParameters();

            platformConfig.getOptionalModuleConfig("open-loadflow-default-parameters")
                    .ifPresent(config -> {
                        parameters.setSlackBusSelector(getSlackBusSelector(config));
                        parameters.setBalanceType(config.getEnumProperty(BALANCE_TYPE_PARAM_NAME, BalanceType.class, BALANCE_TYPE_DEFAULT_VALUE));
                        parameters.setDc(config.getBooleanProperty(DC_PARAM_NAME, DC_DEFAULT_VALUE));
                        parameters.setDistributedSlack(config.getBooleanProperty(DISTRIBUTED_SLACK_PARAM_NAME, DISTRIBUTED_SLACK_DEFAULT_VALUE));
                        parameters.setLowImpedanceBranchMode(config.getEnumProperty(LOW_IMPEDANCE_BRANCH_MODE_PARAM_NAME, LowImpedanceBranchMode.class, LOW_IMPEDANCE_BRANCH_MODE_DEFAULT_VALUE));
                        parameters.setVoltageRemoteControl(config.getBooleanProperty(VOLTAGE_REMOTE_CONTROLE_PARAM_NAME, VOLTAGE_REMOTE_CONTROLE_DEFAULT_VALUE));
                        parameters.setThrowsExceptionInCaseOfSlackDistributionFailure(
                                config.getBooleanProperty(THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_PARAM_NAME, THROWS_EXCEPTION_IN_CASE_OF_SLACK_DISTRIBUTION_FAILURE_DEFAULT_VALUE)
                        );
                    });
            return parameters;
        }

        private SlackBusSelector getSlackBusSelector(ModuleConfig config) {
            String type = config.getStringProperty("slackBusSelectorType");
            SlackBusSelector slackBusSelector = null;
            for (SlackBusSelectorParametersReader reader : ServiceLoader.load(SlackBusSelectorParametersReader.class)) {
                if (type.equals(reader.getName())) {
                    slackBusSelector = reader.read(config);
                }
            }
            return slackBusSelector != null ? slackBusSelector : SLACK_BUS_SELECTOR_DEFAULT_VALUE;
        }

        @Override
        public String getExtensionName() {
            return "openLoadflowParameters";
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
