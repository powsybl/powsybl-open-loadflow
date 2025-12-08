/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlows;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PostContingencyNetworkResult extends AbstractNetworkResult {

    private final List<BranchResult> branchResults = new ArrayList<>();

    private final PreContingencyNetworkResult preContingencyMonitorInfos;

    private final Contingency contingency;

    public PostContingencyNetworkResult(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension,
                                        PreContingencyNetworkResult preContingencyMonitorInfos, Contingency contingency, LoadFlowModel loadFlowModel, double dcPowerFactor) {
        super(network, monitorIndex, createResultExtension, loadFlowModel, dcPowerFactor);
        this.preContingencyMonitorInfos = Objects.requireNonNull(preContingencyMonitorInfos);
        this.contingency = Objects.requireNonNull(contingency);
    }

    @Override
    protected void clear() {
        super.clear();
        branchResults.clear();
    }

    public void addResults(StateMonitor monitor, Predicate<LfBranch> isBranchDisabled, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        addResults(monitor, branch -> {
            var preContingencyBranchResult = preContingencyMonitorInfos.getBranchResult(branch.getId());
            double preContingencyBranchP1 = preContingencyBranchResult != null ? preContingencyBranchResult.getP1() : Double.NaN;
            double preContingencyBranchOfContingencyP1 = Double.NaN;
            if (contingency.getElements().size() == 1) {
                ContingencyElement contingencyElement = contingency.getElements().get(0);
                if (contingencyElement.getType() == ContingencyElementType.BRANCH
                        || contingencyElement.getType() == ContingencyElementType.LINE
                        || contingencyElement.getType() == ContingencyElementType.DANGLING_LINE
                        || contingencyElement.getType() == ContingencyElementType.TWO_WINDINGS_TRANSFORMER) {
                    BranchResult preContingencyBranchOfContingencyResult = preContingencyMonitorInfos.getBranchResult(contingencyElement.getId());
                    if (preContingencyBranchOfContingencyResult != null) {
                        preContingencyBranchOfContingencyP1 = preContingencyBranchOfContingencyResult.getP1();
                    }
                }
            }
            branchResults.addAll(branch.createBranchResult(preContingencyBranchP1, preContingencyBranchOfContingencyP1, createResultExtension, zeroImpedanceFlows, loadFlowModel));
        }, isBranchDisabled);
    }

    @Override
    public void update() {
        update(LfBranch::isDisabled);
    }

    public void update(Predicate<LfBranch> isBranchDisabled) {
        clear();
        StateMonitor stateMonitor = monitorIndex.getSpecificStateMonitors().get(contingency.getId());
        if (stateMonitor != null) {
            Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(stateMonitor, network);
            addResults(stateMonitor, isBranchDisabled, zeroImpedanceFlows);
        } else {
            Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(monitorIndex.getAllStateMonitor(), network);
            addResults(monitorIndex.getAllStateMonitor(), isBranchDisabled, zeroImpedanceFlows);
        }

        // TODO HG: 3WT
    }

    @Override
    public List<BranchResult> getBranchResults() {
        return branchResults;
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
