/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractLoadFlowFromCache<P extends AbstractLoadFlowParameters<P>> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractLoadFlowFromCache.class);

    protected final Network network;

    protected final LoadFlowParameters parameters;

    protected final OpenLoadFlowParameters parametersExt;

    protected final P acOrDcParameters;

    protected final ReportNode reportNode;

    protected AbstractLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               P acOrDcParameters, ReportNode reportNode) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.parametersExt = Objects.requireNonNull(parametersExt);
        this.acOrDcParameters = Objects.requireNonNull(acOrDcParameters);
        this.reportNode = Objects.requireNonNull(reportNode);
    }

    protected void configureTopoConfig(LfTopoConfig topoConfig) {
        for (String switchId : parametersExt.getActionableSwitchesIds()) {
            Switch sw = network.getSwitch(switchId);
            if (sw != null) {
                if (sw.isOpen()) {
                    topoConfig.getSwitchesToClose().add(sw);
                } else {
                    topoConfig.getSwitchesToOpen().add(sw);
                }
            } else {
                LOGGER.warn("Actionable switch '{}' does not exist", switchId);
            }
        }
        for (String transformerId : parametersExt.getActionableTransformersIds()) {
            Branch<?> branch = network.getBranch(transformerId);
            if (branch != null) {
                topoConfig.addBranchIdWithRtcToRetain(transformerId);
                topoConfig.addBranchIdWithPtcToRetain(transformerId);
            } else {
                ThreeWindingsTransformer tw3 = network.getThreeWindingsTransformer(transformerId);
                if (tw3 != null) {
                    for (ThreeSides side : ThreeSides.values()) {
                        topoConfig.addBranchIdWithRtcToRetain(LfLegBranch.getId(side, transformerId));
                        topoConfig.addBranchIdWithPtcToRetain(LfLegBranch.getId(side, transformerId));
                    }
                }
                LOGGER.warn("Actionable transformer '{}' does not exist", transformerId);
            }
        }
        if (topoConfig.isBreaker()) {
            acOrDcParameters.getNetworkParameters().setBreakers(true);
        }
    }
}
