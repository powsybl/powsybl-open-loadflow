/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfStarBus;
import com.powsybl.openloadflow.network.impl.Transformers;
import com.powsybl.openloadflow.network.util.NetworkStateBranchFlows;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.ChangedPhaseTapChanger;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractNetworkResult {

    protected final LfNetwork network;

    protected final StateMonitorIndex monitorIndex;

    protected final boolean createResultExtension;

    protected Map<LfBranch, Integer> phaseTapChangerInfos = new HashMap<>();

    protected final Map<LfBranch, ChangedPhaseTapChanger> changedPhaseTapChangers = new HashMap<>();

    protected AbstractNetworkResult(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension) {
        this.network = Objects.requireNonNull(network);
        this.monitorIndex = Objects.requireNonNull(monitorIndex);
        this.createResultExtension = createResultExtension;
    }

    protected void addResults(StateMonitor monitor, Consumer<LfBranch> branchConsumer, Predicate<LfBranch> isBranchDisabled,
                              Consumer<LfBus> busConsumer, Consumer<String> threeWindingsTransformerResultsConsumer) {
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
                    .forEach(busConsumer);
        }

        if (!monitor.getThreeWindingsTransformerIds().isEmpty()) {
            monitor.getThreeWindingsTransformerIds().stream()
                    .filter(id -> network.getBusById(LfStarBus.getId(id)) != null && !network.getBusById(LfStarBus.getId(id)).isDisabled())
                    .forEach(threeWindingsTransformerResultsConsumer);
        }
    }

    public abstract List<BusResult> getBusResults();

    public abstract List<ThreeWindingsTransformerResult> getThreeWindingsTransformerResults();

    public abstract List<BranchResult> getBranchResults();

    protected abstract void storeInitialPhaseTapChangerInfo();

    protected void updateChangedPhaseTapChanger() {
        for (Map.Entry<LfBranch, Integer> ptcInfo : phaseTapChangerInfos.entrySet()) {
            LfBranch b = ptcInfo.getKey();
            int currentTap = ptcInfo.getValue();
            int newTapPosition = Transformers.findTapPosition(b.getPhaseTapChanger().orElseThrow(),
                    Math.toDegrees(b.getPiModel().getA1()));
            if (currentTap != newTapPosition) {
                changedPhaseTapChangers.put(b, new ChangedPhaseTapChanger(b.getMainOriginalId(), b.getOriginalSide().orElse(null), currentTap, newTapPosition));
            }
        }
    }

    public abstract void update(NetworkStateBranchFlows branchFlows);

    public Map<LfBranch, ChangedPhaseTapChanger> getChangedPhaseTapChangers() {
        return changedPhaseTapChangers;
    }
}
