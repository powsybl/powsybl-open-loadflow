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
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.solver.AcSolverStatus;
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
public class AcLoadFlowFromCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowFromCache.class);

    private final Network network;

    private final LoadFlowParameters parameters;

    private final OpenLoadFlowParameters parametersExt;

    private final AcLoadFlowParameters acParameters;

    private final ReportNode reportNode;

    public AcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               AcLoadFlowParameters acParameters, ReportNode reportNode) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.parametersExt = Objects.requireNonNull(parametersExt);
        this.acParameters = Objects.requireNonNull(acParameters);
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
            acParameters.getNetworkParameters().setBreakers(true);
        }
    }

    private List<NetworkCache.AcLfValue> initValues(NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry) {
        List<NetworkCache.AcLfValue> values;
        LfTopoConfig topoConfig = new LfTopoConfig();
        configureTopoConfig(topoConfig);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.loadWithReconnectableElements(network, topoConfig, acParameters.getNetworkParameters(),
                LfNetworkList.WorkingVariantReverter::new, reportNode, null)) {
            values = lfNetworkList.getList()
                    .stream()
                    .map(n -> new NetworkCache.AcLfValue(new AcLoadFlowContext(n, acParameters)))
                    .toList();
            entry.setValues(values);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setVariantCleaner(new LfNetworkList.DefaultVariantCleaner(network, entry.getWorkingVariantId(), variantCleaner.getTmpVariantId()));
            }
        }
        return values;
    }

    private static AcLoadFlowResult run(NetworkCache.AcLfValue value) {
        if (value.getNetwork().getValidity() != LfNetwork.Validity.VALID) {
            return AcLoadFlowResult.createNoCalculationResult(value.getNetwork());
        }
        if (value.isNetworkUpdated()) {
            AcLoadFlowResult result = new AcloadFlowEngine(value.getContext())
                    .run();
            value.setNetworkUpdated(false);
            return result;
        }
        return new AcLoadFlowResult(value.getNetwork(), 0, 0, AcSolverStatus.CONVERGED, OuterLoopResult.stable(), 0d, 0d);
    }

    public List<AcLoadFlowResult> run() {
        NetworkCache.Entry<NetworkCache.LfInput, NetworkCache.AcLfValue> entry = NetworkCache.AC_LF_INSTANCE.get(network, new NetworkCache.LfInput(parameters));
        List<NetworkCache.AcLfValue> values = entry.getValues();
        if (values == null) {
            values = initValues(entry);
        }
        return values.stream()
                .map(AcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
