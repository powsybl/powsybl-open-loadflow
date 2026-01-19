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
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.impl.LfStarBus;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlows;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.powsybl.openloadflow.network.LfBranch.BranchType.TRANSFO_3_LEG_1;
import static com.powsybl.openloadflow.network.LfBranch.BranchType.TRANSFO_3_LEG_2;
import static com.powsybl.openloadflow.network.LfBranch.BranchType.TRANSFO_3_LEG_3;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractNetworkResult {

    protected final LfNetwork network;

    protected final StateMonitorIndex monitorIndex;

    protected final boolean createResultExtension;

    protected final LoadFlowModel loadFlowModel;

    protected final double dcPowerFactor;

    protected final List<BusResult> busResults = new ArrayList<>();

    protected final List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();

    static final List<LfBranch.BranchType> T3WT_BRANCH_TYPES = List.of(TRANSFO_3_LEG_1, TRANSFO_3_LEG_2, TRANSFO_3_LEG_3);

    protected AbstractNetworkResult(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension, LoadFlowModel loadFlowModel, double dcPowerFactor) {
        this.network = Objects.requireNonNull(network);
        this.monitorIndex = Objects.requireNonNull(monitorIndex);
        this.createResultExtension = createResultExtension;
        this.loadFlowModel = loadFlowModel;
        this.dcPowerFactor = dcPowerFactor;
    }

    protected void addResults(StateMonitor monitor, Consumer<LfBranch> branchConsumer, Predicate<LfBranch> isBranchDisabled, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        Objects.requireNonNull(monitor);
        if (!monitor.getBranchIds().isEmpty()) {
            network.getBranches().stream()
                    .filter(lfBranch -> !isBranchDisabled.test(lfBranch))
                    .forEach(lfBranch -> {
                        for (String originalId : lfBranch.getOriginalIds()) {
                            if (monitor.getBranchIds().contains(originalId)) {
                                branchConsumer.accept(lfBranch);
                                break; // only generate result at first original ID match
                            }
                        }
                    });
        }

        if (!monitor.getVoltageLevelIds().isEmpty()) {
            network.getBuses().stream()
                    .filter(lfBus -> monitor.getVoltageLevelIds().contains(lfBus.getVoltageLevelId()))
                    .filter(lfBus -> !lfBus.isDisabled())
                    .forEach(lfBus -> busResults.addAll(lfBus.createBusResults()));
        }

        if (!monitor.getThreeWindingsTransformerIds().isEmpty()) {
            monitor.getThreeWindingsTransformerIds().stream()
                    .filter(id -> network.getBusById(LfStarBus.getId(id)) != null && !network.getBusById(LfStarBus.getId(id)).isDisabled())
                    .forEach(id -> threeWindingsTransformerResults.add(LfLegBranch.createThreeWindingsTransformerResult(network, id, createResultExtension, zeroImpedanceFlows, loadFlowModel)));
        }
    }

    protected void clear() {
        busResults.clear();
        threeWindingsTransformerResults.clear();
    }

    public List<BusResult> getBusResults() {
        return busResults;
    }

    public List<ThreeWindingsTransformerResult> getThreeWindingsTransformerResults() {
        return threeWindingsTransformerResults;
    }

    public abstract List<BranchResult> getBranchResults();

    public abstract void update();

    private boolean isATransfo3WBranch(LfBranch lfBranch) {
        return T3WT_BRANCH_TYPES.contains(lfBranch.getBranchType());
    }

    private boolean isContainingAMonitoredBranch(LfZeroImpedanceNetwork zeroImpedanceNetwork, StateMonitor monitor) {
        for (LfBranch lfBranch : zeroImpedanceNetwork.getGraph().edgeSet()) {
            if (isATransfo3WBranch(lfBranch)) {
                LfLegBranch lfLegBranch = (LfLegBranch) lfBranch;
                if (monitor.getThreeWindingsTransformerIds().contains(lfLegBranch.getTwt().getId())) {
                    return true;
                }
            } else {
                if (monitor.getBranchIds().contains(lfBranch.getMainOriginalId())) {
                    return true;
                }
            }
        }

        return false;
    }

    protected Map<String, LfBranch.LfBranchResults> storeResultsForZeroImpedanceBranches(StateMonitor monitor, LfNetwork network) {
        Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = new LinkedHashMap<>();
        if (monitor.getBranchIds().isEmpty() && monitor.getThreeWindingsTransformerIds().isEmpty()) {
            // Nothing to store as no branches are monitored
            return zeroImpedanceFlows;
        }
        for (LfZeroImpedanceNetwork zeroImpedanceNetwork : network.getZeroImpedanceNetworks(loadFlowModel)) {
            if (isContainingAMonitoredBranch(zeroImpedanceNetwork, monitor)) {
                new ZeroImpedanceFlows(zeroImpedanceNetwork.getGraph(), zeroImpedanceNetwork.getSpanningTree(), loadFlowModel, dcPowerFactor)
                        .computeFlows(true, zeroImpedanceFlows);
            }
        }
        return zeroImpedanceFlows;
    }
}
