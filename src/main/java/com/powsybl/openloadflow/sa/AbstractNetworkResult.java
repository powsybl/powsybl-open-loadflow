/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sa;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfStarBus;
import com.powsybl.openloadflow.network.impl.Transformers;
import com.powsybl.openloadflow.network.util.ZeroImpedanceFlows;
import com.powsybl.openloadflow.sa.extensions.PhaseTapChangerResult;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.MovedPhaseShifterResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.powsybl.openloadflow.network.LfBranch.BranchType.*;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public abstract class AbstractNetworkResult {

    protected final LfNetwork network;

    protected final StateMonitorIndex monitorIndex;

    protected final StateMonitorIndex zeroImpedanceMonitorIndex;

    protected final boolean createResultExtension;

    protected final LoadFlowModel loadFlowModel;

    protected final double dcPowerFactor;

    static final List<LfBranch.BranchType> T3WT_BRANCH_TYPES = List.of(TRANSFO_3_LEG_1, TRANSFO_3_LEG_2, TRANSFO_3_LEG_3);

    public record StateMonitorIndexes(StateMonitorIndex monitorIndex, StateMonitorIndex zeroImpedanceMonitorIndex) {
    }

    protected List<PhaseTapChangerResult> phaseTapChangerResults = new ArrayList<>();

    protected final Map<String, MovedPhaseShifterResult> movedPhaseShifterResults = new HashMap<>();

    protected AbstractNetworkResult(LfNetwork network, StateMonitorIndexes monitorIndexes, boolean createResultExtension, LoadFlowModel loadFlowModel, double dcPowerFactor) {
        this.network = Objects.requireNonNull(network);
        this.monitorIndex = Objects.requireNonNull(monitorIndexes.monitorIndex);
        this.zeroImpedanceMonitorIndex = Objects.requireNonNull(monitorIndexes.zeroImpedanceMonitorIndex);
        this.createResultExtension = createResultExtension;
        this.loadFlowModel = loadFlowModel;
        this.dcPowerFactor = dcPowerFactor;
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

    public abstract void update();

    private boolean isATransfo3WBranch(LfBranch lfBranch) {
        return T3WT_BRANCH_TYPES.contains(lfBranch.getBranchType());
    }

    private boolean isContainingAMonitoredBranch(LfZeroImpedanceNetwork zeroImpedanceNetwork, StateMonitor monitor) {
        for (LfBranch lfBranch : zeroImpedanceNetwork.getGraph().edgeSet()) {
            if (isATransfo3WBranch(lfBranch)) {
                if (lfBranch.getThreeWindingsTransformerId()
                        .filter(monitor.getThreeWindingsTransformerIds()::contains)
                        .isPresent()) {
                    return true;
                }
            } else {
                if (monitor.getBranchIds().contains(lfBranch.getId())) {
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

    private Optional<PhaseTapChanger> extractPhaseTapChanger(LfBranch branch) {
        return branch.getPhaseTapChanger();
    }

    protected void storeInitialPhaseTapChangerInfo() {
        phaseTapChangerResults = network.getBranches().stream()
                    .filter(b -> !b.isDisabled())
                    .filter(LfBranch::hasPhaseControllerCapability)
                    .map(b -> new PhaseTapChangerResult(b.getPhaseTapChanger().orElseThrow(),
                                b.getMainOriginalId(),
                                b.getPiModel(),
                                b.getPhaseTapChanger().orElseThrow().getTapPosition())
                    )
                    .toList();
    }

    protected void updateMovedPhaseShifters() {
        for (PhaseTapChangerResult ptcResult : phaseTapChangerResults) {
            int newTapPosition = Transformers.findTapPosition(ptcResult.getPhaseTapChanger(), Math.toDegrees(ptcResult.getPiModel().getA1()));
            if (ptcResult.getCurrentTap() != newTapPosition) {
                movedPhaseShifterResults.put(ptcResult.getTransformerId(), new MovedPhaseShifterResult(ptcResult.getTransformerId(), ptcResult.getCurrentTap(), newTapPosition));
                ptcResult.setCurrentTap(newTapPosition);
            }
        }
    }

    public Map<String, MovedPhaseShifterResult> getMovedPhaseShifterResults() {
        return movedPhaseShifterResults;
    }
}
