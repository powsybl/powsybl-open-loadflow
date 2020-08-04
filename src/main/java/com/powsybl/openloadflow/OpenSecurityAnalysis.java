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
import com.powsybl.openloadflow.graph.GraphDecrementalConnectivity;
import com.powsybl.openloadflow.graph.NaiveGraphDecrementalConnectivity;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.LfNetwork;
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

        final String contingencyId;

        final Set<String> branchIdsToOpen = new HashSet<>();

        ContingencyContext(String contingencyId) {
            this.contingencyId = contingencyId;
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
            ContingencyContext contingencyContext = new ContingencyContext(contingency.getId());
            contingencyContexts.add(contingencyContext);

            Set<Switch> switchesToOpen = new HashSet<>();
            for (ContingencyElement element : contingency.getElements()) {
                switch (element.getType()) {
                    case BRANCH:
                        contingencyContext.branchIdsToOpen.add(element.getId());
                        break;
                    default:
                        throw new UnsupportedOperationException("TODO");
                }
                AbstractTrippingTask task = element.toTask();
                task.traverse(network, null, switchesToOpen, new HashSet<>());
            }

            for (Switch sw : switchesToOpen) {
                contingencyContext.branchIdsToOpen.add(sw.getId());
                allSwitchesToOpen.add(sw);
            }
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

        // map contingencies to LF networks

        Map<Integer, List<LfContingency>> lfContingenciesByLfNetworkNum = new HashMap<>();
        for (LfNetwork lfNetwork : acEngine.getNetworks()) {

            // create connectivity data structure
            GraphDecrementalConnectivity<LfBus> connectivity = new NaiveGraphDecrementalConnectivity<>(LfBus::getNum);
            for (LfBus bus : lfNetwork.getBuses()) {
                connectivity.addVertex(bus);
            }
            for (LfBranch branch : lfNetwork.getBranches()) {
                connectivity.addEdge(branch.getBus1(), branch.getBus2());
            }

            List<LfContingency> lfContingencies = new ArrayList<>();
            Iterator<ContingencyContext> contingencyIt = contingencyContexts.iterator();
            while (contingencyIt.hasNext()) {
                ContingencyContext contingencyContext = contingencyIt.next();

                // find contingency branches that are part of this network
                Set<LfBranch> lfBranches = new HashSet<>(1);
                Iterator<String> branchIt = contingencyContext.branchIdsToOpen.iterator();
                while (branchIt.hasNext()) {
                    String branchId = branchIt.next();
                    LfBranch lfBranch = lfNetwork.getBranchById(branchId);
                    if (lfBranch != null) {
                        lfBranches.add(lfBranch);
                        branchIt.remove();
                    }
                }

                // if no more branch in the contingency, remove contingency from the list because
                // it won't be part of another network
                if (contingencyContext.branchIdsToOpen.isEmpty()) {
                    contingencyIt.remove();
                }

                // check if contingency split this network into multiple components
                if (lfBranches.isEmpty()) {
                    continue;
                }

                // update connectivity with triggered branches
                for (LfBranch lfBranch : lfBranches) {
                    connectivity.cut(lfBranch.getBus1(), lfBranch.getBus2());
                }

                // add to contingency description buses and branches that won't be part of the main connected
                // component in post contingency state
                Set<LfBus> lfBuses = new HashSet<>();
                for (LfBus lfBus : lfNetwork.getBuses()) {
                    if (connectivity.getComponentNumber(lfBus) > 0) {
                        lfBuses.add(lfBus);
                    }
                }
                for (LfBranch lfBranch : lfNetwork.getBranches()) {
                    if (connectivity.getComponentNumber(lfBranch.getBus1()) > 0
                            && connectivity.getComponentNumber(lfBranch.getBus2()) > 0) {
                        lfBranches.add(lfBranch);
                    }
                }

                // reset connectivity to discard triggered branches
                connectivity.reset();

                LfContingency lfContingency = new LfContingency(contingencyContext.contingencyId, lfBuses, lfBranches);
                lfContingencies.add(lfContingency);
            }

            if (!lfContingencies.isEmpty()) {
                lfContingenciesByLfNetworkNum.put(lfNetwork.getNum(), lfContingencies);
            }
        }

        // add an outer loop to simulate contingencies
        acEngine.getParameters().getOuterLoops().add(new ContingencyOuterLoop(lfContingenciesByLfNetworkNum));

        List<AcLoadFlowResult> acResults = acEngine.run();

        LimitViolationsResult preContingencyResult = new LimitViolationsResult(false, Collections.emptyList());
        List<PostContingencyResult> postContingencyResults = new ArrayList<>();
        return new SecurityAnalysisResult(preContingencyResult, postContingencyResults);
    }
}
