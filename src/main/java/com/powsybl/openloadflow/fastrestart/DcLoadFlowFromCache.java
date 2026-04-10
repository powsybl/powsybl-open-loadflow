/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
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
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowEngine;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowResult;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopResult;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DcLoadFlowFromCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcLoadFlowFromCache.class);

    private final Network network;

    private final LoadFlowParameters parameters;

    private final OpenLoadFlowParameters parametersExt;

    private final DcLoadFlowParameters dcParameters;

    private final ReportNode reportNode;

    public DcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               DcLoadFlowParameters dcParameters, ReportNode reportNode) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.parametersExt = Objects.requireNonNull(parametersExt);
        this.dcParameters = Objects.requireNonNull(dcParameters);
        this.reportNode = Objects.requireNonNull(reportNode);
    }

    private void configureTopoConfig(LfTopoConfig topoConfig) {
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
            dcParameters.getNetworkParameters().setBreakers(true);
        }
    }

    private List<DcLoadFlowContext> initContexts(NetworkCache.Entry<DcLoadFlowContext> entry) {
        List<DcLoadFlowContext> contexts;
        LfTopoConfig topoConfig = new LfTopoConfig();
        configureTopoConfig(topoConfig);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topoConfig, dcParameters.getNetworkParameters(),
                LfNetworkList.WorkingVariantReverter::new, reportNode)) {
            contexts = lfNetworkList.getList()
                    .stream()
                    .map(n -> new DcLoadFlowContext(n, dcParameters))
                    .collect(Collectors.toList());
            entry.setContexts(contexts);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setTmpVariantId(variantCleaner.getTmpVariantId());
            }
        }
        return contexts;
    }

    private static DcLoadFlowResult run(DcLoadFlowContext context) {
        if (context.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return DcLoadFlowResult.createNoCalculationResult(context.getNetwork());
        }
        if (context.getNetwork().isNetworkUpdated()) {
            DcLoadFlowResult result = new DcLoadFlowEngine(context)
                    .run();
            context.getNetwork().setNetworkUpdated(false);
            return result;
        }
        return new DcLoadFlowResult(context.getNetwork(), 0, true, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<DcLoadFlowResult> run() {
        NetworkCache.Entry<DcLoadFlowContext> entry = NetworkCache.INSTANCE.getDc(network, parameters);
        List<DcLoadFlowContext> contexts = entry.getContexts();
        if (contexts == null) {
            contexts = initContexts(entry);
        }
        return contexts.stream()
                .map(DcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
