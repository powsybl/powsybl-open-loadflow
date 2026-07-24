/*
 * Copyright (c) 2022-2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.security.SecurityAnalysisParameters;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class OpenSecurityAnalysisParameters extends AbstractExtension<SecurityAnalysisParameters> {

    private boolean createResultExtension = CREATE_RESULT_EXTENSION_DEFAULT_VALUE;

    private boolean contingencyPropagation = CONTINGENCY_PROPAGATION_DEFAULT_VALUE;

    private int threadCount = THREAD_COUNT_DEFAULT_VALUE;

    private boolean dcFastMode = DC_FAST_MODE_DEFAULT_VALUE;

    private String contingencyActivePowerLossDistribution = CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_DEFAULT_VALUE;

    private boolean startWithFrozenACEmulation = START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE;

    private NetworkPerThreadMode networkPerThreadMode = NETWORK_PER_THREAD_MODE_DEFAULT_VALUE;

    private ContingencyPartitioningMode contingencyPartitioningMode = CONTINGENCY_PARTITIONING_MODE_DEFAULT_VALUE;

    /**
     * How the contingencies of a multi-threaded analysis are assigned to the partitions:
     * {@link #SLICE} gives each partition a contiguous slice of the contingency list (legacy behavior;
     * contingency lists are usually ordered by electrical region, so a partition can get a much heavier
     * region than another), {@link #ROUND_ROBIN} interleaves them (contingency i goes to partition
     * i modulo thread count), decorrelating partitions from regions and balancing the load. Results
     * and reports are restored to the contingency list order in both modes.
     */
    public enum ContingencyPartitioningMode {
        SLICE,
        ROUND_ROBIN
    }

    /**
     * How each additional thread of a multi-threaded analysis gets its own LfNetwork:
     * {@link #COPY} deep copies the network built once (faster, results identical to single thread),
     * {@link #REBUILD} rebuilds it from the IIDM network under a lock (legacy behavior).
     */
    public enum NetworkPerThreadMode {
        COPY,
        REBUILD
    }

    public static final String CREATE_RESULT_EXTENSION_PARAM_NAME = "createResultExtension";
    public static final boolean CREATE_RESULT_EXTENSION_DEFAULT_VALUE = false;
    public static final String CONTINGENCY_PROPAGATION_PARAM_NAME = "contingencyPropagation";
    public static final boolean CONTINGENCY_PROPAGATION_DEFAULT_VALUE = true;
    public static final String THREAD_COUNT_PARAM_NAME = "threadCount";
    public static final int THREAD_COUNT_DEFAULT_VALUE = 1;
    public static final String DC_FAST_MODE_PARAM_NAME = "dcFastMode";
    public static final boolean DC_FAST_MODE_DEFAULT_VALUE = false;
    public static final String START_WITH_FROZEN_AC_EMULATION_PARAM_NAME = "startWithFrozenACEmulation";
    public static final boolean START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE = true;
    public static final String CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_PARAM_NAME = "contingencyActivePowerLossDistribution";
    public static final String CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_DEFAULT_VALUE = "Default";
    public static final String NETWORK_PER_THREAD_MODE_PARAM_NAME = "networkPerThreadMode";
    public static final NetworkPerThreadMode NETWORK_PER_THREAD_MODE_DEFAULT_VALUE = NetworkPerThreadMode.COPY;
    public static final String CONTINGENCY_PARTITIONING_MODE_PARAM_NAME = "contingencyPartitioningMode";
    public static final ContingencyPartitioningMode CONTINGENCY_PARTITIONING_MODE_DEFAULT_VALUE = ContingencyPartitioningMode.ROUND_ROBIN;
    public static final List<String> SPECIFIC_PARAMETERS_NAMES = List.of(CREATE_RESULT_EXTENSION_PARAM_NAME,
            CONTINGENCY_PROPAGATION_PARAM_NAME,
            THREAD_COUNT_PARAM_NAME,
            DC_FAST_MODE_PARAM_NAME,
            CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_PARAM_NAME,
            START_WITH_FROZEN_AC_EMULATION_PARAM_NAME,
            NETWORK_PER_THREAD_MODE_PARAM_NAME,
            CONTINGENCY_PARTITIONING_MODE_PARAM_NAME);

    @Override
    public String getName() {
        return "open-security-analysis-parameters";
    }

    public boolean isCreateResultExtension() {
        return createResultExtension;
    }

    public OpenSecurityAnalysisParameters setCreateResultExtension(boolean createResultExtension) {
        this.createResultExtension = createResultExtension;
        return this;
    }

    public boolean isContingencyPropagation() {
        return contingencyPropagation;
    }

    public OpenSecurityAnalysisParameters setContingencyPropagation(boolean contingencyPropagation) {
        this.contingencyPropagation = contingencyPropagation;
        return this;
    }

    public boolean isDcFastMode() {
        return dcFastMode;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public OpenSecurityAnalysisParameters setThreadCount(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Invalid thread count value: " + threadCount);
        }
        this.threadCount = threadCount;
        return this;
    }

    public OpenSecurityAnalysisParameters setDcFastMode(boolean dcFastMode) {
        this.dcFastMode = dcFastMode;
        return this;
    }

    public String getContingencyActivePowerLossDistribution() {
        return contingencyActivePowerLossDistribution;
    }

    public OpenSecurityAnalysisParameters setContingencyActivePowerLossDistribution(String contingencyActivePowerLossDistribution) {
        ContingencyActivePowerLossDistribution.find(contingencyActivePowerLossDistribution); // will throw if not found
        this.contingencyActivePowerLossDistribution = contingencyActivePowerLossDistribution;
        return this;
    }

    public boolean isStartWithFrozenACEmulation() {
        return startWithFrozenACEmulation;
    }

    public OpenSecurityAnalysisParameters setStartWithFrozenACEmulation(boolean startWithFrozenACEmulation) {
        this.startWithFrozenACEmulation = startWithFrozenACEmulation;
        return this;
    }

    public NetworkPerThreadMode getNetworkPerThreadMode() {
        return networkPerThreadMode;
    }

    public OpenSecurityAnalysisParameters setNetworkPerThreadMode(NetworkPerThreadMode networkPerThreadMode) {
        this.networkPerThreadMode = Objects.requireNonNull(networkPerThreadMode);
        return this;
    }

    public ContingencyPartitioningMode getContingencyPartitioningMode() {
        return contingencyPartitioningMode;
    }

    public OpenSecurityAnalysisParameters setContingencyPartitioningMode(ContingencyPartitioningMode contingencyPartitioningMode) {
        this.contingencyPartitioningMode = Objects.requireNonNull(contingencyPartitioningMode);
        return this;
    }

    public static OpenSecurityAnalysisParameters getOrDefault(SecurityAnalysisParameters parameters) {
        OpenSecurityAnalysisParameters parametersExt = parameters.getExtension(OpenSecurityAnalysisParameters.class);
        if (parametersExt == null) {
            parametersExt = new OpenSecurityAnalysisParameters();
        }
        return parametersExt;
    }

    public static OpenSecurityAnalysisParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    public static OpenSecurityAnalysisParameters load(PlatformConfig platformConfig) {
        OpenSecurityAnalysisParameters parameters = new OpenSecurityAnalysisParameters();
        platformConfig.getOptionalModuleConfig("open-security-analysis-default-parameters")
                .ifPresent(config -> parameters
                        .setCreateResultExtension(config.getBooleanProperty(CREATE_RESULT_EXTENSION_PARAM_NAME, CREATE_RESULT_EXTENSION_DEFAULT_VALUE))
                        .setContingencyPropagation(config.getBooleanProperty(CONTINGENCY_PROPAGATION_PARAM_NAME, CONTINGENCY_PROPAGATION_DEFAULT_VALUE))
                        .setThreadCount(config.getIntProperty(THREAD_COUNT_PARAM_NAME, THREAD_COUNT_DEFAULT_VALUE))
                        .setDcFastMode(config.getBooleanProperty(DC_FAST_MODE_PARAM_NAME, DC_FAST_MODE_DEFAULT_VALUE))
                        .setContingencyActivePowerLossDistribution(config.getStringProperty(CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_PARAM_NAME,
                            CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_DEFAULT_VALUE))
                        .setStartWithFrozenACEmulation(config.getBooleanProperty(START_WITH_FROZEN_AC_EMULATION_PARAM_NAME, START_WITH_FROZEN_AC_EMULATION_DEFAULT_VALUE))
                        .setNetworkPerThreadMode(config.getEnumProperty(NETWORK_PER_THREAD_MODE_PARAM_NAME, NetworkPerThreadMode.class, NETWORK_PER_THREAD_MODE_DEFAULT_VALUE))
                        .setContingencyPartitioningMode(config.getEnumProperty(CONTINGENCY_PARTITIONING_MODE_PARAM_NAME,
                            ContingencyPartitioningMode.class, CONTINGENCY_PARTITIONING_MODE_DEFAULT_VALUE)));
        return parameters;
    }

    public static OpenSecurityAnalysisParameters load(Map<String, String> properties) {
        return new OpenSecurityAnalysisParameters()
                .update(properties);
    }

    public OpenSecurityAnalysisParameters update(Map<String, String> properties) {
        Optional.ofNullable(properties.get(CREATE_RESULT_EXTENSION_PARAM_NAME))
                .ifPresent(value -> this.setCreateResultExtension(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(CONTINGENCY_PROPAGATION_PARAM_NAME))
                .ifPresent(value -> this.setContingencyPropagation(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(THREAD_COUNT_PARAM_NAME))
                .ifPresent(value -> this.setThreadCount(Integer.parseInt(value)));
        Optional.ofNullable(properties.get(DC_FAST_MODE_PARAM_NAME))
                .ifPresent(value -> this.setDcFastMode(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(CONTINGENCY_ACTIVE_POWER_LOSS_DISTRIBUTION_PARAM_NAME))
                .ifPresent(this::setContingencyActivePowerLossDistribution);
        Optional.ofNullable(properties.get(START_WITH_FROZEN_AC_EMULATION_PARAM_NAME))
                .ifPresent(value -> this.setStartWithFrozenACEmulation(Boolean.parseBoolean(value)));
        Optional.ofNullable(properties.get(NETWORK_PER_THREAD_MODE_PARAM_NAME))
                .ifPresent(value -> this.setNetworkPerThreadMode(NetworkPerThreadMode.valueOf(value)));
        Optional.ofNullable(properties.get(CONTINGENCY_PARTITIONING_MODE_PARAM_NAME))
                .ifPresent(value -> this.setContingencyPartitioningMode(ContingencyPartitioningMode.valueOf(value)));
        return this;
    }
}
