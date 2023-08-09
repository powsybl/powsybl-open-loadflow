/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.commons.reporter.Reporter;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.AcloadFlowEngine;
import com.powsybl.openloadflow.ac.nr.NewtonRaphsonStatus;
import com.powsybl.openloadflow.lf.outerloop.OuterLoopStatus;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowFromCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcLoadFlowFromCache.class);

    private final Network network;

    private final LoadFlowParameters parameters;

    private final OpenLoadFlowParameters parametersExt;

    private final AcLoadFlowParameters acParameters;

    private final Reporter reporter;

    public AcLoadFlowFromCache(Network network, LoadFlowParameters parameters, OpenLoadFlowParameters parametersExt,
                               AcLoadFlowParameters acParameters, Reporter reporter) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.parametersExt = Objects.requireNonNull(parametersExt);
        this.acParameters = Objects.requireNonNull(acParameters);
        this.reporter = Objects.requireNonNull(reporter);
    }

    private void configureSwitches(Set<Switch> switchesToOpen, Set<Switch> switchesToClose) {
        for (String switchId : parametersExt.getActionableSwitchesIds()) {
            Switch sw = network.getSwitch(switchId);
            if (sw != null) {
                if (sw.isOpen()) {
                    switchesToClose.add(sw);
                } else {
                    switchesToOpen.add(sw);
                }
            } else {
                LOGGER.warn("Actionable switch '{}' does not exist", switchId);
            }
        }
        if (!switchesToClose.isEmpty() || !switchesToOpen.isEmpty()) {
            acParameters.getNetworkParameters().setBreakers(true);
        }
    }

    private List<AcLoadFlowContext> initContexts(NetworkCache.Entry entry) {
        List<AcLoadFlowContext> contexts;
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Switch> switchesToClose = new HashSet<>();
        configureSwitches(switchesToOpen, switchesToClose);

        // Because of caching, we only need to switch back to working variant but not to remove the variant, thus
        // WorkingVariantReverter is used instead of DefaultVariantCleaner
        try (LfNetworkList lfNetworkList = Networks.load(network, acParameters.getNetworkParameters(), switchesToOpen, switchesToClose,
                LfNetworkList.WorkingVariantReverter::new, reporter)) {
            contexts = lfNetworkList.getList()
                    .stream()
                    .map(n -> new AcLoadFlowContext(n, acParameters))
                    .collect(Collectors.toList());
            entry.setContexts(contexts);
            LfNetworkList.VariantCleaner variantCleaner = lfNetworkList.getVariantCleaner();
            if (variantCleaner != null) {
                entry.setTmpVariantId(variantCleaner.getTmpVariantId());
            }
        }
        return contexts;
    }

    private static AcLoadFlowResult run(AcLoadFlowContext context) {
        if (!context.getNetwork().isValid()) {
            return AcLoadFlowResult.createNoCalculationResult(context.getNetwork());
        }
        if (context.isNetworkUpdated()) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            context.setNetworkUpdated(false);
            return result;
        }
        return new AcLoadFlowResult(context.getNetwork(), 0, 0, NewtonRaphsonStatus.CONVERGED, OuterLoopStatus.STABLE, 0d, 0d);
    }

    public List<AcLoadFlowResult> run() {
        NetworkCache.Entry entry = NetworkCache.INSTANCE.get(network, parameters);
        List<AcLoadFlowContext> contexts = entry.getContexts();
        if (contexts == null) {
            contexts = initContexts(entry);
        }
        return contexts.stream()
                .map(AcLoadFlowFromCache::run)
                .collect(Collectors.toList());
    }
}
