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
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.openloadflow.network.util.NetworkStateBranchFlows;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PreContingencyNetworkResult extends AbstractNetworkResult {

    private final Map<String, BranchResult> branchResults = new LinkedHashMap<>();
    private final Map<String, BusResult> busResults = new LinkedHashMap<>();
    private final Map<String, ThreeWindingsTransformerResult> threeWindingsTransformerResults = new LinkedHashMap<>();

    public PreContingencyNetworkResult(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension) {
        super(network, monitorIndex, createResultExtension);
    }

    protected void clear() {
        busResults.clear();
        branchResults.clear();
        threeWindingsTransformerResults.clear();
    }

    private void addResults(StateMonitor monitor, Predicate<LfBranch> isBranchDisabled, NetworkStateBranchFlows branchFlows) {
        addResults(monitor,
                branch -> branch.createBranchResult(Double.NaN, Double.NaN, createResultExtension, branchFlows)
                .forEach(branchResult -> branchResults.put(branchResult.getBranchId(), branchResult)),
                isBranchDisabled,
                bus -> bus.createBusResults().forEach(busResult -> busResults.put("%s_%s".formatted(busResult.getVoltageLevelId(), busResult.getBusId()), busResult)),
                id -> threeWindingsTransformerResults.put(id, LfLegBranch.createThreeWindingsTransformerResult(network, id, createResultExtension, branchFlows)));
    }

    @Override
    public void update(NetworkStateBranchFlows branchFlows) {
        update(LfBranch::isDisabled, branchFlows);
    }

    @Override
    protected void storeInitialPhaseTapChangerInfo() {
        phaseTapChangerInfos = network.getBranches().stream()
                .filter(b -> !b.isDisabled())
                .filter(LfBranch::hasPhaseControllerCapability)
                .collect(Collectors.toMap(
                        Function.identity(),
                        b -> b.getPhaseTapChanger().orElseThrow().getTapPosition()));
    }

    public void update(Predicate<LfBranch> isBranchDisabled, NetworkStateBranchFlows branchFlows) {
        clear();
        addResults(monitorIndex.getNoneStateMonitor(), isBranchDisabled, branchFlows);
        addResults(monitorIndex.getAllStateMonitor(), isBranchDisabled, branchFlows);
        storeInitialPhaseTapChangerInfo();
        updateChangedPhaseTapChanger();
    }

    public BranchResult getBranchResult(String branchId) {
        Objects.requireNonNull(branchId);
        return branchResults.get(branchId);
    }

    @Override
    public List<BranchResult> getBranchResults() {
        return new ArrayList<>(branchResults.values());
    }

    public BusResult getBusResult(String busId) {
        Objects.requireNonNull(busId);
        return busResults.get(busId);
    }

    @Override
    public List<BusResult> getBusResults() {
        return new ArrayList<>(busResults.values());
    }

    public ThreeWindingsTransformerResult getThreeWindingsTransformerResult(String id) {
        Objects.requireNonNull(id);
        return threeWindingsTransformerResults.get(id);
    }

    @Override
    public List<ThreeWindingsTransformerResult> getThreeWindingsTransformerResults() {
        return new ArrayList<>(threeWindingsTransformerResults.values());
    }
}
