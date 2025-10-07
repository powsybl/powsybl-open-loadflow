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
import com.powsybl.openloadflow.network.LfTopoConfig;
import com.powsybl.openloadflow.network.impl.LfNetworkList;
import com.powsybl.openloadflow.network.impl.Networks;
import com.powsybl.openloadflow.network.impl.PropagatedContingency;
import com.powsybl.openloadflow.network.impl.PropagatedContingencyCreationParameters;
import com.powsybl.openloadflow.util.Reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 * @author Didier Vidal {@literal <didier.vidal-ext at rte-france.com>}
 */
public final class LfNetworkLoadMT {

    private LfNetworkLoadMT() {
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

    public static <P extends AbstractLoadFlowParameters<P>> void createLFNetworksPerContingencyPartition(Network network,
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
            for (int i = 0; i < contingenciesPartitions.size(); i++) {
                final int partitionNum = i;
                var contingenciesPartition = contingenciesPartitions.get(i);
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
                    networkLock.lock();
                    try {
                        network.getVariantManager().setWorkingVariant(workingVariantId);

                        propagatedContingencies = PropagatedContingency.createList(network, contingenciesPartition, partitionTopoConfig, creationParameters);

                        parameters = parameterProvider.createParameters(partitionTopoConfig);

                        ReportNode threadRootNode = partitionNum == 0 ? rootReportNode : Reports.createRootThreadReport(rootReportNode);
                        reportNodes.set(partitionNum, threadRootNode);

                        // create networks including all necessary switches
                        lfNetworks = Networks.load(network, parameters.getNetworkParameters(), partitionTopoConfig, threadRootNode);
                        lfNetworksList.add(0, lfNetworks); // FIXME to workaround variant removal bug, to fix in core
                    } finally {
                        networkLock.unlock();
                    }

                    // run simulation on largest network
                    contingencyRunner.run(partitionNum, lfNetworks, propagatedContingencies, parameters);
                    return null;
                }, executor));
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
}
