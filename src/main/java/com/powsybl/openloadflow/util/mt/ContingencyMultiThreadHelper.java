/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

package com.powsybl.openloadflow.util.mt;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.CompletableFutureTask;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openloadflow.lf.AbstractLoadFlowParameters;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.impl.LfNetworkCopier;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public final class ContingencyMultiThreadHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContingencyMultiThreadHelper.class);

    private ContingencyMultiThreadHelper() {
    }

    public static void mergeReportThreadResults(ReportNode mainReport, ReportNode toMerge) {

        Map<LfNetworkId, ReportNode> mainNodes = indexLfNetworkNodes(mainReport);

        Map<LfNetworkId, ReportNode> toMergeNodes = indexLfNetworkNodes(toMerge);

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

    public interface ParameterProvider<P extends AbstractLoadFlowParameters<P>> {
        P createParameters(LfTopoConfig partitionTopoConfig);
    }

    public interface ContingencyRunner<P extends AbstractLoadFlowParameters<P>> {
        /**
         * @param presolved when true, the networks were presolved before being copied (see
         *                  {@link NetworksPresolver}): the pre-contingency state is already in the
         *                  networks and the runner can skip its own pre-contingency simulation
         */
        void run(int partitionNum, LfNetworkList lfNetworks, List<PropagatedContingency> propagatedContingencies, P parameters, boolean presolved);
    }

    /**
     * Runs the pre-contingency simulations once on the networks built by
     * {@link #buildOnceCopyAndRunAnalysis}, before they are copied for each partition, so that every
     * partition starts from the already solved state instead of re-solving it.
     */
    public interface NetworksPresolver<P extends AbstractLoadFlowParameters<P>> {
        void presolve(LfNetworkList lfNetworks, P parameters);
    }

    public interface ReportMerger {
        void mergeReportThreadResults(ReportNode rootReportNode, List<ReportNode> threadReportNodes);
    }

    /**
     * Legacy merge: appends each thread's post-contingency report nodes in thread order. Yields the
     * contingency list order when the partitions are contiguous slices.
     */
    public static void mergeReportThreadResults(ReportNode mainReport, List<ReportNode> threadReports) {
        for (ReportNode threadReport : threadReports) {
            mergeReportThreadResults(mainReport, threadReport);
        }
    }

    /**
     * Ordered merge: collects the pre and post contingency simulation nodes of all the thread reports
     * (including the first partition's, see the {@code detachFirstPartitionReporting} parameter of the
     * analysis methods) and appends them to the main network nodes in contingency list order. Required
     * to keep the report identical to a single-threaded run when the partitions are not contiguous
     * slices (round-robin partitioning).
     */
    public static void mergeReportThreadResultsOrdered(ReportNode mainReport, List<ReportNode> threadReports,
                                                       Map<String, Integer> contingencyPositions) {
        Map<LfNetworkId, ReportNode> mainNodes = indexLfNetworkNodes(mainReport);
        List<Map<LfNetworkId, ReportNode>> threadNodes = threadReports.stream()
                .map(ContingencyMultiThreadHelper::indexLfNetworkNodes)
                .toList();
        for (Map.Entry<LfNetworkId, ReportNode> entry : mainNodes.entrySet()) {
            List<ReportNode> preNodes = new ArrayList<>();
            List<ReportNode> postNodes = new ArrayList<>();
            for (Map<LfNetworkId, ReportNode> byNetwork : threadNodes) {
                ReportNode threadNetworkNode = byNetwork.get(entry.getKey());
                if (threadNetworkNode == null) {
                    continue;
                }
                for (ReportNode child : threadNetworkNode.getChildren()) {
                    if (child.getMessageKey().equals(Reports.PRE_CONTINGENCY_SIMULATION_KEY)) {
                        preNodes.add(child);
                    } else if (child.getMessageKey().equals(Reports.POST_CONTINGENCY_SIMULATION_KEY)) {
                        postNodes.add(child);
                    }
                }
            }
            postNodes.sort(Comparator.comparingInt(n -> contingencyPositions.getOrDefault(
                    n.getValue(Reports.CONTINGENCY_ID).orElseThrow().getValue().toString(), Integer.MAX_VALUE)));
            ReportNode mainNetworkNode = entry.getValue();
            preNodes.forEach(mainNetworkNode::addCopy);
            postNodes.forEach(mainNetworkNode::addCopy);
        }
    }

    private static Map<LfNetworkId, ReportNode> indexLfNetworkNodes(ReportNode report) {
        return report.getChildren().stream()
                .filter(r -> r.getMessageKey().equals(Reports.LF_NETWORK_KEY))
                .collect(Collectors.toMap(
                        n -> new LfNetworkId(n.getValue(Reports.NETWORK_NUM_CC).orElseThrow().getValue(),
                                n.getValue(Reports.NETWORK_NUM_SC).orElseThrow().getValue()),
                        n -> n));
    }

    /**
     * Build the LF networks once on the calling thread, then give each partition (but the first one)
     * its own deep copy ({@link LfNetworkCopier}), created in parallel and lock free. All partitions
     * simulate the same network as a single threaded analysis (built with the topo config covering
     * all the contingencies), so results do not depend on the thread count. Falls back to
     * {@link #createLFNetworksPerContingencyPartitionAndRunAnalysis} when the network uses features
     * not supported by the copy.
     */
    public static <P extends AbstractLoadFlowParameters<P>> void buildOnceCopyAndRunAnalysis(Network network,
                                                                                             String workingVariantId,
                                                                                             List<List<Contingency>> contingenciesPartitions,
                                                                                             PropagatedContingencyCreationParameters creationParameters,
                                                                                             LfTopoConfig topoConfig,
                                                                                             ParameterProvider<P> parameterProvider,
                                                                                             NetworksPresolver<P> presolver,
                                                                                             ContingencyRunner<P> contingencyRunner,
                                                                                             ReportNode rootReportNode,
                                                                                             ReportMerger reportMerger,
                                                                                             boolean detachFirstPartitionReporting,
                                                                                             Executor executor) throws ExecutionException {
        int partitionCount = contingenciesPartitions.size();
        List<ReportNode> reportNodes = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(partitionCount, ReportNode.NO_OP)));
        boolean oldAllowVariantMultiThreadAccess = network.getVariantManager().isVariantMultiThreadAccessAllowed();
        network.getVariantManager().allowVariantMultiThreadAccess(true);
        boolean fallbackToRebuild = false;
        try {
            network.getVariantManager().setWorkingVariant(workingVariantId);

            // Create the propagated contingencies per partition, each with its own topo config, so we
            // can later give each partition's network only its own single-side openable branches. The
            // networks themselves are built once with the union topo config below, so the topology (and
            // therefore the results) is identical to a single threaded analysis whatever the thread count.
            Stopwatch phaseStopwatch = Stopwatch.createStarted();
            var unionTopoConfig = new LfTopoConfig(topoConfig);
            List<List<PropagatedContingency>> propagatedPartitions = new ArrayList<>(partitionCount);
            List<Set<String>> partitionOpenableSide1 = new ArrayList<>(partitionCount);
            List<Set<String>> partitionOpenableSide2 = new ArrayList<>(partitionCount);
            int startIndex = 0;
            for (List<Contingency> partition : contingenciesPartitions) {
                var partitionTopoConfig = new LfTopoConfig(topoConfig);
                propagatedPartitions.add(PropagatedContingency.createList(network, partition, partitionTopoConfig, creationParameters, startIndex));
                startIndex += partition.size();
                // single-side openable branches needed by this partition's contingencies only
                partitionOpenableSide1.add(Set.copyOf(partitionTopoConfig.getBranchIdsOpenableSide1()));
                partitionOpenableSide2.add(Set.copyOf(partitionTopoConfig.getBranchIdsOpenableSide2()));
                // accumulate the union used to build the networks
                unionTopoConfig.getSwitchesToOpen().addAll(partitionTopoConfig.getSwitchesToOpen());
                unionTopoConfig.getBusIdsToLose().addAll(partitionTopoConfig.getBusIdsToLose());
                unionTopoConfig.getBranchIdsOpenableSide1().addAll(partitionTopoConfig.getBranchIdsOpenableSide1());
                unionTopoConfig.getBranchIdsOpenableSide2().addAll(partitionTopoConfig.getBranchIdsOpenableSide2());
            }

            long propagationMs = phaseStopwatch.elapsed(TimeUnit.MILLISECONDS);

            // one parameters instance per partition, all created on the calling thread as IIDM is read
            List<P> partitionParameters = new ArrayList<>(partitionCount);
            for (int i = 0; i < partitionCount; i++) {
                partitionParameters.add(parameterProvider.createParameters(unionTopoConfig));
            }
            long parametersMs = phaseStopwatch.elapsed(TimeUnit.MILLISECONDS) - propagationMs;

            try (LfNetworkList lfNetworks = Networks.loadWithReconnectableElements(network, unionTopoConfig,
                    partitionParameters.get(0).getNetworkParameters(), rootReportNode)) {
                long buildMs = phaseStopwatch.elapsed(TimeUnit.MILLISECONDS) - propagationMs - parametersMs;
                if (lfNetworks.getList().stream().allMatch(LfNetworkCopier::canCopy)) {
                    boolean presolved = false;
                    long presolveMs = 0;
                    if (presolver != null) {
                        // run the pre-contingency simulations once, on the calling thread, before the copies
                        // are taken: every partition then starts from the solved state
                        Stopwatch presolveStopwatch = Stopwatch.createStarted();
                        presolver.presolve(lfNetworks, partitionParameters.get(0));
                        presolveMs = presolveStopwatch.elapsed(TimeUnit.MILLISECONDS);
                        // the solve may have changed the topology (e.g. an automation system tripping a
                        // breaker), in which case the solved networks are not copyable anymore
                        presolved = lfNetworks.getList().stream().allMatch(LfNetworkCopier::canCopy);
                        if (!presolved) {
                            LOGGER.warn("LF networks are not copyable anymore after the pre-contingency simulations: falling back to one network build per thread");
                        }
                    }
                    LOGGER.info("COPY mode setup phases: contingency propagation {} ms, parameters {} ms, networks build {} ms, presolve {} ms (presolved={})",
                            propagationMs, parametersMs, buildMs, presolveMs, presolved);
                    if (presolver == null || presolved) {
                        runOnCopies(network, workingVariantId, lfNetworks, propagatedPartitions, partitionParameters,
                                partitionOpenableSide1, partitionOpenableSide2, contingencyRunner, rootReportNode, reportNodes,
                                executor, presolved, detachFirstPartitionReporting);
                    } else {
                        fallbackToRebuild = true;
                    }
                } else {
                    LOGGER.warn("LF network deep copy is not supported for this network: falling back to one network build per thread");
                    fallbackToRebuild = true;
                }
            }
        } finally {
            network.getVariantManager().allowVariantMultiThreadAccess(oldAllowVariantMultiThreadAccess);
        }

        if (fallbackToRebuild) {
            createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions,
                    creationParameters, topoConfig, parameterProvider, contingencyRunner, rootReportNode, reportMerger,
                    detachFirstPartitionReporting, executor);
            return;
        }

        reportMerger.mergeReportThreadResults(rootReportNode, collectThreadReportNodes(reportNodes, rootReportNode));
    }

    /**
     * Redirect the simulation reporting of already built networks (whose build was reported under the
     * main report) to a detached thread root, so an ordered report merge can re-insert the simulation
     * nodes in contingency order.
     */
    private static ReportNode detachSimulationReporting(List<LfNetwork> lfNetworks, ReportNode rootReportNode) {
        ReportNode threadRootNode = Reports.createRootThreadReport(rootReportNode);
        for (LfNetwork lfNetwork : lfNetworks) {
            ReportNode networkReportNode = Reports.createRootLfNetworkReportNode(threadRootNode, lfNetwork.getNumCC(), lfNetwork.getSynchronousNetworks().getFirst().getNumSC());
            Reports.includeLfNetworkReportNode(threadRootNode, networkReportNode);
            lfNetwork.setReportNode(networkReportNode);
        }
        return threadRootNode;
    }

    private static List<ReportNode> collectThreadReportNodes(List<ReportNode> reportNodes, ReportNode rootReportNode) {
        return reportNodes.stream()
                .filter(n -> n != rootReportNode && n != ReportNode.NO_OP)
                .toList();
    }

    private static <P extends AbstractLoadFlowParameters<P>> void runOnCopies(Network network,
                                                                              String workingVariantId,
                                                                              LfNetworkList lfNetworks,
                                                                              List<List<PropagatedContingency>> propagatedPartitions,
                                                                              List<P> partitionParameters,
                                                                              List<Set<String>> partitionOpenableSide1,
                                                                              List<Set<String>> partitionOpenableSide2,
                                                                              ContingencyRunner<P> contingencyRunner,
                                                                              ReportNode rootReportNode,
                                                                              List<ReportNode> reportNodes,
                                                                              Executor executor,
                                                                              boolean presolved,
                                                                              boolean detachFirstPartitionReporting) throws ExecutionException {
        int partitionCount = propagatedPartitions.size();
        // the networks may have been built on a temporary variant (when switches are retained): worker
        // threads have their own working variant in multi thread access mode and must select it
        String builtVariantId = lfNetworks.getVariantCleaner() != null ? lfNetworks.getVariantCleaner().getTmpVariantId() : workingVariantId;
        LoadFlowModel loadFlowModel = partitionParameters.get(0).getNetworkParameters().getLoadFlowModel();

        // first phase: deep copy the networks in parallel; only reads the original networks, so no
        // synchronization is needed, but it must complete before any simulation starts mutating them.
        // When the networks were presolved, the first partition gets a copy too (with the original
        // report nodes, where the presolve already reported the pre-contingency simulations), so the
        // originals are never simulated after the presolve and keep a clean solved state
        List<LfNetworkList> partitionNetworks = Collections.synchronizedList(new ArrayList<>(Collections.nCopies(partitionCount, (LfNetworkList) null)));
        reportNodes.set(0, rootReportNode);
        if (!presolved) {
            partitionNetworks.set(0, lfNetworks);
            if (detachFirstPartitionReporting) {
                reportNodes.set(0, detachSimulationReporting(lfNetworks.getList(), rootReportNode));
            }
        }
        Stopwatch copyPhaseStopwatch = Stopwatch.createStarted();
        List<CompletableFuture<Void>> copyFutures = new ArrayList<>();
        for (int i = presolved ? 0 : 1; i < partitionCount; i++) {
            final int partitionNum = i;
            if (partitionNum > 0 && propagatedPartitions.get(partitionNum).isEmpty()) {
                continue;
            }
            copyFutures.add(CompletableFutureTask.runAsync(() -> {
                network.getVariantManager().setWorkingVariant(builtVariantId);
                boolean detachedReporting = partitionNum > 0 || detachFirstPartitionReporting;
                ReportNode threadRootNode = detachedReporting ? Reports.createRootThreadReport(rootReportNode) : null;
                List<LfNetwork> copies = new ArrayList<>(lfNetworks.getList().size());
                for (LfNetwork lfNetwork : lfNetworks.getList()) {
                    ReportNode networkReportNode;
                    if (detachedReporting) {
                        networkReportNode = Reports.createRootLfNetworkReportNode(threadRootNode, lfNetwork.getNumCC(), lfNetwork.getSynchronousNetworks().getFirst().getNumSC());
                        Reports.includeLfNetworkReportNode(threadRootNode, networkReportNode);
                    } else {
                        networkReportNode = lfNetwork.getReportNode();
                    }
                    copies.add(LfNetworkCopier.copy(lfNetwork, loadFlowModel, networkReportNode));
                }
                partitionNetworks.set(partitionNum, new LfNetworkList(copies));
                if (detachedReporting) {
                    reportNodes.set(partitionNum, threadRootNode);
                }
                return null;
            }, executor));
        }
        waitForFutures(copyFutures);
        LOGGER.info("COPY mode copy phase (parallel) completed in {} ms", copyPhaseStopwatch.elapsed(TimeUnit.MILLISECONDS));

        // second phase: run the simulations, each partition on its own networks
        Stopwatch runPhaseStopwatch = Stopwatch.createStarted();
        List<CompletableFuture<Void>> runFutures = new ArrayList<>();
        for (int i = 0; i < partitionCount; i++) {
            final int partitionNum = i;
            if (partitionNetworks.get(partitionNum) == null) {
                continue;
            }
            runFutures.add(CompletableFutureTask.runAsync(() -> {
                network.getVariantManager().setWorkingVariant(builtVariantId);
                // restrict the (result neutral, AC only) single-side open branch equation terms to the
                // branches this partition actually opens, so each partition's equation system is as
                // light as in the legacy one-build-per-thread mode while keeping identical results
                restrictDisconnectionAllowed(partitionNetworks.get(partitionNum),
                        partitionOpenableSide1.get(partitionNum), partitionOpenableSide2.get(partitionNum));
                Stopwatch partitionStopwatch = Stopwatch.createStarted();
                contingencyRunner.run(partitionNum, partitionNetworks.get(partitionNum),
                        propagatedPartitions.get(partitionNum), partitionParameters.get(partitionNum), presolved);
                LOGGER.info("COPY mode partition {} simulated in {} ms", partitionNum, partitionStopwatch.elapsed(TimeUnit.MILLISECONDS));
                return null;
            }, executor));
        }
        waitForFutures(runFutures);
        LOGGER.info("COPY mode run phase completed in {} ms", runPhaseStopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    /**
     * Disable the {@code disconnectionAllowed} flag (which only adds, in AC, the single-side open branch
     * equation terms, all inactive while the branch is connected, so this does not change any result) on
     * the branches not opened by this partition's contingencies. The networks were built once with the
     * union of all the partitions' openable branches; this trims each copy back to its own, recovering the
     * lighter equation system of the legacy one-build-per-thread mode.
     */
    private static void restrictDisconnectionAllowed(LfNetworkList networks, Set<String> openableSide1, Set<String> openableSide2) {
        for (LfNetwork lfNetwork : networks.getList()) {
            for (LfBranch branch : lfNetwork.getBranches()) {
                if (branch.isDisconnectionAllowedSide1() && !openableSide1.contains(branch.getId())) {
                    branch.setDisconnectionAllowedSide1(false);
                }
                if (branch.isDisconnectionAllowedSide2() && !openableSide2.contains(branch.getId())) {
                    branch.setDisconnectionAllowedSide2(false);
                }
            }
        }
    }

    private static void waitForFutures(List<CompletableFuture<Void>> futures) throws ExecutionException {
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
        createLFNetworksPerContingencyPartitionAndRunAnalysis(network, workingVariantId, contingenciesPartitions, creationParameters,
                topoConfig, parameterProvider, contingencyRunner, rootReportNode, reportMerger, false, executor);
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
                                                                                                                       boolean detachFirstPartitionReporting,
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
                futures.add(CompletableFutureTask.runAsync(() -> {

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
                    Stopwatch lockStopwatch = Stopwatch.createStarted();
                    networkLock.lock();
                    long lockWaitMs = lockStopwatch.elapsed(TimeUnit.MILLISECONDS);
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
                    LOGGER.info("REBUILD mode partition {}: lock wait {} ms, in-lock propagation+parameters+build {} ms",
                            partitionNum, lockWaitMs, lockStopwatch.elapsed(TimeUnit.MILLISECONDS) - lockWaitMs);

                    if (partitionNum == 0 && detachFirstPartitionReporting) {
                        // the first partition's networks were built under the main report (network info
                        // belongs there); their simulation nodes must go to a detached thread root so the
                        // ordered merge can re-insert them in contingency order
                        reportNodes.set(0, detachSimulationReporting(lfNetworks.getList(), rootReportNode));
                    }

                    // run simulation on largest network
                    Stopwatch partitionStopwatch = Stopwatch.createStarted();
                    contingencyRunner.run(partitionNum, lfNetworks, propagatedContingencies, parameters, false);
                    LOGGER.info("REBUILD mode partition {} simulated in {} ms", partitionNum, partitionStopwatch.elapsed(TimeUnit.MILLISECONDS));
                    return null;
                }, executor));
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

        reportMerger.mergeReportThreadResults(rootReportNode, collectThreadReportNodes(reportNodes, rootReportNode));
        for (var lfNetworks : lfNetworksList) {
            lfNetworks.close();
        }
    }

    public record LfNetworkId(Object numCC, Object numSC) {
    }
}
