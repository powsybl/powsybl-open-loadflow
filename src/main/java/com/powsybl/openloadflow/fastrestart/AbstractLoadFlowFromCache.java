/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.fastrestart;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.lf.AbstractLoadFlowContext;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Sylvestre Prabakaran {@literal <sylvestre.prabakaran at rte-france.com>}
 */
public abstract class AbstractLoadFlowFromCache<P extends AbstractLoadFlowParameters<P>, C extends AbstractLoadFlowContext<?, ?, P>> {

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

    protected List<C> initContexts(NetworkCache.Entry<C> entry, Function<LfNetwork, C> contextConstructor) {
        List<C> contexts;
        LfTopoConfig topoConfig = new LfTopoConfig();
        configureTopoConfig(topoConfig);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topoConfig, acOrDcParameters.getNetworkParameters(),
                LfNetworkList.WorkingVariantReverter::new, reportNode)) {
            contexts = lfNetworkList.getList()
                    .stream()
                    .map(contextConstructor)
                    .collect(Collectors.toList());
            entry.setContexts(contexts);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setTmpVariantId(variantCleaner.getTmpVariantId());
            }
        }
        return contexts;
    }
}
