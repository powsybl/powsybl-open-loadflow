/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow;

import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.tasks.AbstractTrippingTask;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.openloadflow.network.FirstSlackBusSelector;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfNetworkLoadingParameters;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;

import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class OpenSecurityAnalysis implements SecurityAnalysis {

    private final Network network;

    private final LimitViolationDetector detector;

    private final LimitViolationFilter filter;

    private final List<SecurityAnalysisInterceptor> interceptors = new ArrayList<>();

    public OpenSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter) {
        this.network = Objects.requireNonNull(network);
        this.detector = Objects.requireNonNull(detector);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public void addInterceptor(SecurityAnalysisInterceptor interceptor) {
        interceptors.add(Objects.requireNonNull(interceptor));
    }

    @Override
    public boolean removeInterceptor(SecurityAnalysisInterceptor interceptor) {
        return interceptors.remove(Objects.requireNonNull(interceptor));
    }

    @Override
    public CompletableFuture<SecurityAnalysisResult> run(String workingVariantId, SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        Objects.requireNonNull(workingVariantId);
        Objects.requireNonNull(securityAnalysisParameters);
        Objects.requireNonNull(contingenciesProvider);
        return CompletableFuture.supplyAsync(() -> {
            String oldWorkingVariantId = network.getVariantManager().getWorkingVariantId();
            network.getVariantManager().setWorkingVariant(workingVariantId);
            SecurityAnalysisResult result = runSync(securityAnalysisParameters, contingenciesProvider);
            network.getVariantManager().setWorkingVariant(oldWorkingVariantId);
            return result;
        });
    }

    static class ContingencyContext {

        final Set<String> branchIdsToOpen = new HashSet<>();

        final Set<Switch> switchesToOpen = new HashSet<>();
    }

    private List<LfNetwork> buildLfNetworks(List<Contingency> contingencies) {
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<ContingencyContext> contexts = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            ContingencyContext context = new ContingencyContext();
            contexts.add(context);

            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        context.branchIdsToOpen.add(element.getId());
                        break;
                    default:
                        throw new UnsupportedOperationException("TODO");
                }
                AbstractTrippingTask task = element.toTask();
                task.traverse(network, null, context.switchesToOpen, new HashSet<>());
            }

            allSwitchesToOpen.addAll(context.switchesToOpen);
        }

        System.out.println(allSwitchesToOpen);

        // try to find all swiches impacted by at least one contingency
        String tmpVariantId = "tmp";
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            for (Switch sw : network.getSwitches()) {
                sw.setRetained(false);
            }
            for (Switch sw : allSwitchesToOpen) {
                sw.setRetained(true);
            }
            return LfNetwork.load(network, new LfNetworkLoadingParameters(new FirstSlackBusSelector(), false, false, false, true));
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }
    }

    SecurityAnalysisResult runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        List<LfNetwork> lfNetworks = buildLfNetworks(contingencies);

        lfNetworks.get(0).writeJson(new OutputStreamWriter(System.out));

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(false, Collections.emptyList());
        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        SecurityAnalysisResult result = new SecurityAnalysisResult(preContingencyResult, postContingencyResults);
        return result;
    }
}
