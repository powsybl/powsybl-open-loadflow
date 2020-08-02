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
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.MatrixFactory;
import com.powsybl.openloadflow.ac.ContingencyOuterLoop;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowParameters;
import com.powsybl.openloadflow.ac.outerloop.AcLoadFlowResult;
import com.powsybl.openloadflow.ac.outerloop.AcloadFlowEngine;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.impl.LfContingencyImpl;
import com.powsybl.security.*;
import com.powsybl.security.interceptors.SecurityAnalysisInterceptor;

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

    private final MatrixFactory matrixFactory;

    public OpenSecurityAnalysis(Network network, LimitViolationDetector detector, LimitViolationFilter filter,
                                MatrixFactory matrixFactory) {
        this.network = Objects.requireNonNull(network);
        this.detector = Objects.requireNonNull(detector);
        this.filter = Objects.requireNonNull(filter);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
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

        final Contingency contingency;

        final Set<String> branchIdsToOpen = new HashSet<>();

        final Set<Switch> switchesToOpen = new HashSet<>();

        ContingencyContext(Contingency contingency) {
            this.contingency = contingency;
        }
    }

    SecurityAnalysisResult runSync(SecurityAnalysisParameters securityAnalysisParameters, ContingenciesProvider contingenciesProvider) {
        LoadFlowParameters lfParameters = securityAnalysisParameters.getLoadFlowParameters();
        OpenLoadFlowParameters lfParametersExt = OpenLoadFlowProvider.getParametersExt(securityAnalysisParameters.getLoadFlowParameters());

        // load contingencies
        List<Contingency> contingencies = contingenciesProvider.getContingencies(network);

        // try to find all swiches impacted by at least one contingency
        Set<Switch> allSwitchesToOpen = new HashSet<>();
        List<ContingencyContext> contingencyContexts = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            ContingencyContext contingencyContext = new ContingencyContext(contingency);
            contingencyContexts.add(contingencyContext);

            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        contingencyContext.branchIdsToOpen.add(element.getId());
                        break;
                    default:
                        throw new UnsupportedOperationException("TODO");
                }
                AbstractTrippingTask task = element.toTask();
                task.traverse(network, null, contingencyContext.switchesToOpen, new HashSet<>());
            }

            allSwitchesToOpen.addAll(contingencyContext.switchesToOpen);
        }

        // create an AC engine with a network including all necessary switches
        AcloadFlowEngine acEngine;
        String tmpVariantId = "olf-tmp-" + UUID.randomUUID().toString();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), tmpVariantId);
        try {
            for (Switch sw : network.getSwitches()) {
                sw.setRetained(false);
            }
            for (Switch sw : allSwitchesToOpen) {
                sw.setRetained(true);
            }
            AcLoadFlowParameters acParameters = OpenLoadFlowProvider.createAcParameters(network, matrixFactory, lfParameters, lfParametersExt, true);
            acEngine = new AcloadFlowEngine(network, acParameters);
        } finally {
            network.getVariantManager().removeVariant(tmpVariantId);
        }

        // add an outer loop to simulate contingencies
        List<LfContingency> lfContingencies = new ArrayList<>(contingencyContexts.size());
        for (ContingencyContext contingencyContext : contingencyContexts) {
            lfContingencies.add(new LfContingencyImpl(contingencyContext.contingency));
        }
        acEngine.getParameters().getOuterLoops().add(new ContingencyOuterLoop(lfContingencies));

        List<AcLoadFlowResult> acResults = acEngine.run();

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(false, Collections.emptyList());
        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        return new SecurityAnalysisResult(preContingencyResult, postContingencyResults);
    }
}
