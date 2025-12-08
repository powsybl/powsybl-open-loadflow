/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfZeroImpedanceNetwork;
import com.powsybl.openloadflow.network.LoadFlowModel;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlows;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PreContingencyNetworkResult extends AbstractNetworkResult {

    private final Map<String, BranchResult> branchResults = new HashMap<>();

    public PreContingencyNetworkResult(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension, LoadFlowModel loadFlowModel, double dcPowerFactor) {
        super(network, monitorIndex, createResultExtension, loadFlowModel, dcPowerFactor);
    }

    @Override
    protected void clear() {
        super.clear();
        branchResults.clear();
    }

    private void addResults(StateMonitor monitor, Predicate<LfBranch> isBranchDisabled, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        addResults(monitor, branch -> {
            branch.createBranchResult(Double.NaN, Double.NaN, createResultExtension, zeroImpedanceFlows, loadFlowModel)
                    .forEach(branchResult -> branchResults.put(branchResult.getBranchId(), branchResult));
        }, isBranchDisabled);
    }

    @Override
    public void update() {
        update(LfBranch::isDisabled);
    }

    public void update(Predicate<LfBranch> isBranchDisabled) {
        clear();
        Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(monitorIndex.getNoneStateMonitor(), network);
        addResults(monitorIndex.getNoneStateMonitor(), isBranchDisabled, zeroImpedanceFlows);
        zeroImpedanceFlows.clear();
        zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(monitorIndex.getAllStateMonitor(), network);
        addResults(monitorIndex.getAllStateMonitor(), isBranchDisabled, zeroImpedanceFlows);

        // TODO HG: 3WT
    }

    public BranchResult getBranchResult(String branchId) {
        Objects.requireNonNull(branchId);
        return branchResults.get(branchId);
    }

    @Override
    public List<BranchResult> getBranchResults() {
        return new ArrayList<>(branchResults.values());
    }

    private Map<String, LfBranch.LfBranchResults> storeResultsForZeroImpedanceBranches(StateMonitor monitor, LfNetwork network) {
        Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = new LinkedHashMap<>();
        for (LfZeroImpedanceNetwork zeroImpedanceNetwork : network.getZeroImpedanceNetworks(loadFlowModel)) {
            if (zeroImpedanceNetwork.getGraph().edgeSet().stream().map(LfBranch::getOriginalIds).flatMap(List::stream).anyMatch(monitor.getBranchIds()::contains)) {
                new ZeroImpedanceFlows(zeroImpedanceNetwork.getGraph(), zeroImpedanceNetwork.getSpanningTree(), loadFlowModel, dcPowerFactor)
                        .computeAndProvideResults(zeroImpedanceFlows);
            }
        }
        return zeroImpedanceFlows;
    }
}
