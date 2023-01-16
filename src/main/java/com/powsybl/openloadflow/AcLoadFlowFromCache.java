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
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowContext;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class AcLoadFlowFromCache {

    private static final int MAX_PLAUSIBLE_SWITCHES_TO_RETAIN = 10;

    private final Network network;

    private final LoadFlowParameters parameters;

    private final AcLoadFlowParameters acParameters;

    private final Reporter reporter;

    public AcLoadFlowFromCache(Network network, LoadFlowParameters parameters, AcLoadFlowParameters acParameters, Reporter reporter) {
        this.network = Objects.requireNonNull(network);
        this.parameters = Objects.requireNonNull(parameters);
        this.acParameters = Objects.requireNonNull(acParameters);
        this.reporter = Objects.requireNonNull(reporter);
    }

    private void configureSwitches(Set<Switch> switchesToOpen, Set<Switch> switchesToClose) {
        List<Switch> retainedSwitches = network.getSwitchStream()
                .filter(sw -> sw.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER
                        && sw.isRetained())
                .collect(Collectors.toList());
        // if number of switch to retain is small, we can consider that open ones could be closed during simulation
        // and closed ones could be opened during simulation
        if (retainedSwitches.size() < MAX_PLAUSIBLE_SWITCHES_TO_RETAIN) {
            for (Switch sw : retainedSwitches) {
                if (sw.isOpen()) {
                    switchesToClose.add(sw);
                } else {
                    switchesToOpen.add(sw);
                }
            }
            acParameters.getNetworkParameters().setBreakers(true);
        }
    }

    private List<AcLoadFlowContext> initContexts(NetworkCache.Entry entry) {
        List<AcLoadFlowContext> contexts;
        Set<Switch> switchesToOpen = new HashSet<>();
        Set<Switch> switchesToClose = new HashSet<>();
        configureSwitches(switchesToOpen, switchesToClose);
        try (LfNetworkList lfNetworkList = Networks.load(network, acParameters.getNetworkParameters(), switchesToOpen, switchesToClose, reporter)) {
            contexts = lfNetworkList.getList()
                    .stream()
                    .map(n -> new AcLoadFlowContext(n, acParameters))
                    .collect(Collectors.toList());
            entry.setContexts(contexts);
            entry.setVariantCleaner(lfNetworkList.release());
        }
        return contexts;
    }

    private static AcLoadFlowResult run(AcLoadFlowContext context) {
        if (context.getNetwork().isValid() && context.isNetworkUpdated()) {
            AcLoadFlowResult result = new AcloadFlowEngine(context)
                    .run();
            context.setNetworkUpdated(false);
            return result;
        }
        return AcLoadFlowResult.createNoCalculationResult(context.getNetwork());
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
