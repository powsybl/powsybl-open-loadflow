/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.util.mt;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.*;
import com.powsybl.openloadflow.util.Reports;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public final class ContingencyMultiThreadHelper {

    private ContingencyMultiThreadHelper() {
    }

    public static void mergeReportThreadResults(ReportNode mainReport, ReportNode toMerge) {

        Map<LfNetworkId, ReportNode> mainNodes = mainReport.getChildren().stream()
                .filter(r -> r.getMessageKey().equals(Reports.LF_NETWORK_KEY))
                .collect(Collectors.toMap(
                        n -> new LfNetworkId(n.getValue(Reports.NETWORK_NUM_CC).orElseThrow().getValue(),
                                                       n.getValue(Reports.NETWORK_NUM_SC).orElseThrow().getValue()),
                                  n -> n));

        Map<LfNetworkId, ReportNode> toMergeNodes = toMerge.getChildren().stream()
                .filter(r -> r.getMessageKey().equals(Reports.LF_NETWORK_KEY))
                .collect(Collectors.toMap(
                        n -> new LfNetworkId(n.getValue(Reports.NETWORK_NUM_CC).orElseThrow().getValue(),
                                n.getValue(Reports.NETWORK_NUM_SC).orElseThrow().getValue()),
                        n -> n));

        // By construction all threads should have the same lfNetwork List
        // So the merge is just about appending relevant data to lfNetwork nodes of the
        // main thread

        for (Map.Entry<LfNetworkId, ReportNode> entry : mainNodes.entrySet()) {
            // Both should exist
            ReportNode mainReportNode = entry.getValue();
            ReportNode toMergeNode = toMergeNodes.get(entry.getKey());
            toMergeNode.getChildren().stream()
                    .filter(n -> n.getMessageKey().equals(Reports.POST_CONTINGENCY_SIMULATION_KEY))
                    .forEach(mainReportNode::addCopy);
        }
    }

    public static void buildFinalReport(final ReportNode mainReport,
                                        final Map<Integer, List<LfNetwork>> networkCopies,
                                        final List<Contingency> contingencies) {
        Objects.requireNonNull(mainReport);
        Objects.requireNonNull(networkCopies);

        // For each thread except 0 (which already filled the mainReport)
        for (Map.Entry<Integer, List<LfNetwork>> entry : networkCopies.entrySet()) {
            // For each LfNetwork in the thread
            for (LfNetwork lfNetwork : entry.getValue()) {
                ReportNode networkReport = lfNetwork.getReportNode();

                int numCC = lfNetwork.getNumCC();
                int numSC = lfNetwork.getSynchronousNetworks().getFirst().getNumSC();

                ReportNode mainNetworkNode = findLfNetworkNode(mainReport, numCC, numSC);

                if (mainNetworkNode != null) {
                    // Add all POST_CONTINGENCY_SIMULATION_KEY nodes from this network's report
                    mainNetworkNode.addCopy(networkReport);
                    /*networkReport.getChildren().stream()
                            .filter(n -> n.getMessageKey()
                                    .equals(Reports.POST_CONTINGENCY_SIMULATION_KEY))
                            .forEach(mainNetworkNode::addCopy);*/
                }
            }
        }
    }

    private static ReportNode findLfNetworkNode(final ReportNode mainReport,
                                                final int numCC, final int numSC) {
        return mainReport.getChildren().stream()
                .filter(r -> r.getMessageKey().equals(Reports.LF_NETWORK_KEY))
                .filter(r -> r.getValue(Reports.NETWORK_NUM_CC).isPresent()
                        && r.getValue(Reports.NETWORK_NUM_CC).get().getValue().equals(numCC)
                        && r.getValue(Reports.NETWORK_NUM_SC).isPresent()
                        && r.getValue(Reports.NETWORK_NUM_SC).get().getValue().equals(numSC))
                .findFirst()
                .orElse(null);
    }

    public interface ParameterProvider<P extends AbstractLoadFlowParameters<P>> {
        P createParameters(LfTopoConfig partitionTopoConfig);
    }

    public interface ContingencyRunner<P extends AbstractLoadFlowParameters<P>> {
        void run(int partitionNum, LfNetworkList lfNetworks, List<PropagatedContingency> propagatedContingencies, P parameters);
    }

    public interface ReportMerger {
        void mergeReportThreadResults(ReportNode rootReportNode, ReportNode threadReportNode);
    }

    public static <P extends AbstractLoadFlowParameters<P>> void createLFNetworksPerContingencyPartitionAndRunAnalysis(Network network,
                                                                                                                       String workingVariantId,
                                                                                                                       List<List<Contingency>> contingenciesPartitions,
                                                                                                                       PropagatedContingencyCreationParameters creationParameters,
                                                                                                                       LfTopoConfig topoConfig,
                                                                                                                       ParameterProvider<P> parameterProvider,
                                                                                                                       ContingencyRunner<P> contingencyRunner,
                                                                                                                       ReportNode rootReportNode,
                                                                                                                       ReportMerger reportMerger,
                                                                                                                       Executor executor) throws ExecutionException {

        List<ReportNode> reportNodes = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(contingenciesPartitions.size(), ReportNode.NO_OP)));
        List<LfNetworkList> lfNetworksList = new ArrayList<>();
        boolean oldAllowVariantMultiThreadAccess = network.getVariantManager().isVariantMultiThreadAccessAllowed();
        network.getVariantManager().allowVariantMultiThreadAccess(true);
        try {
            Lock networkLock = new ReentrantLock();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int startIndexMutable = 0;
            for (int i = 0; i < contingenciesPartitions.size(); i++) {
                final int partitionNum = i;
                var contingenciesPartition = contingenciesPartitions.get(i);
                if (partitionNum > 0 && contingenciesPartition.isEmpty()) {
                    continue;
                }
                // store startIndex for completableFuture launched in this loop
                final int startIndex = startIndexMutable;
                futures.add(CompletableFutureTask.runAsync(
                    () -> runTask(network, workingVariantId, creationParameters, topoConfig, parameterProvider,
                        contingencyRunner, rootReportNode, contingenciesPartition, lfNetworksList, networkLock, startIndex,
                        partitionNum, reportNodes),
                    executor));
                startIndexMutable += contingenciesPartition.size();
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(); // we need to use get instead of join to get an interruption exception
            } catch (InterruptedException e) {
                // also interrupt worker threads
                for (var future : futures) {
                    future.cancel(true);
                }
                Thread.currentThread().interrupt();
            }
        } finally {
            network.getVariantManager().allowVariantMultiThreadAccess(oldAllowVariantMultiThreadAccess);
        }

        int networkRank = 0;
        for (var lfNetworks : lfNetworksList) {
            if (networkRank != 0) {
                reportMerger.mergeReportThreadResults(rootReportNode, reportNodes.get(networkRank));
            }
            lfNetworks.close();
            networkRank += 1;
        }
    }

    public static <P extends AbstractLoadFlowParameters<P>> void runAnalysisOnCopy(int nbThreads,
                                                                                  ContingencyRunner<P> contingencyRunner,
                                                                                  ReportNode rootReportNode,
                                                                                  ReportMerger reportMerger,
                                                                                  Executor executor) throws ExecutionException {
        List<ReportNode> reportNodes = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(nbThreads, ReportNode.NO_OP)));
        List<LfNetworkList> lfNetworksList = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < nbThreads; i++) {
            final int workerNum = i;
            futures.add(CompletableFutureTask.runAsync(
                    () -> contingencyRunner.run(workerNum, null, null, null),
                    executor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(); // we need to use get instead of join to get an interruption exception
        } catch (InterruptedException e) {
            // also interrupt worker threads
            for (var future : futures) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
        }

        int networkRank = 0;
        for (var lfNetworks : lfNetworksList) {
            if (networkRank != 0) {
                reportMerger.mergeReportThreadResults(rootReportNode, reportNodes.get(networkRank));
            }
            lfNetworks.close();
            networkRank += 1;
        }
    }

    private static <P extends AbstractLoadFlowParameters<P>> Void runTask(Network network,
                                                                          String workingVariantId,
                                                                          PropagatedContingencyCreationParameters creationParameters,
                                                                          LfTopoConfig topoConfig,
                                                                          ParameterProvider<P> parameterProvider,
                                                                          ContingencyRunner<P> contingencyRunner,
                                                                          ReportNode rootReportNode,
                                                                          List<Contingency> contingenciesPartition,
                                                                          List<LfNetworkList> lfNetworksList,
                                                                          Lock networkLock,
                                                                          int startIndex,
                                                                          int partitionNum,
                                                                          List<ReportNode> reportNodes) {

        var partitionTopoConfig = new LfTopoConfig(topoConfig);

        //  we have to pay attention with IIDM network multi threading even when allowVariantMultiThreadAccess is set:
        //    - variant cloning and removal is not thread safe
        //    - we cannot read or write on an exising variant while another thread clone or remove a variant
        //    - be aware that even after LF network loading, though LF network we get access to original IIDM
        //      variant (for instance to get reactive capability curve), so allowVariantMultiThreadAccess mode
        //      is absolutely required
        //  so in order to be thread safe, we need to:
        //    - lock LF network creation (which create a working variant, see {@code LfNetworkList})
        //    - delay {@code LfNetworkList} closing (which remove a working variant) out of worker thread
        LfNetworkList lfNetworks;
        List<PropagatedContingency> propagatedContingencies;
        P parameters;
        networkLock.lock();
        try {
            network.getVariantManager().setWorkingVariant(workingVariantId);

            propagatedContingencies = PropagatedContingency.createList(network, contingenciesPartition, partitionTopoConfig, creationParameters, startIndex);

            parameters = parameterProvider.createParameters(partitionTopoConfig);

            ReportNode threadRootNode = partitionNum == 0 ? rootReportNode : Reports.createRootThreadReport(rootReportNode);
            reportNodes.set(partitionNum, threadRootNode);

            // create networks including all necessary switches
            lfNetworks = Networks.loadWithReconnectableElements(network, partitionTopoConfig, parameters.getNetworkParameters(), threadRootNode);
            lfNetworksList.add(0, lfNetworks); // FIXME to workaround variant removal bug, to fix in core
        } finally {
            networkLock.unlock();
        }

        // run simulation on largest network
        contingencyRunner.run(partitionNum, lfNetworks, propagatedContingencies, parameters);
        return null;
    }

    public record LfNetworkId(Object numCC, Object numSC) {
    }
}
