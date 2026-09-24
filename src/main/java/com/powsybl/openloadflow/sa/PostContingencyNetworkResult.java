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
import com.powsybl.openloadflow.network.impl.LfLegBranch;
import com.powsybl.security.SecurityAnalysisParameters.ModifiedMonitoredElementsParameters;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.ChangedPhaseTapChanger;
import com.powsybl.security.results.ThreeWindingsTransformerResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PostContingencyNetworkResult extends AbstractNetworkResult {

    private final List<BranchResult> branchResults = new ArrayList<>();
    private final List<BusResult> busResults = new ArrayList<>();
    private final List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();

    private final PreContingencyNetworkResult preContingencyNetworkResult;

    private final Contingency contingency;

    private final ModifiedMonitoredElementsParameters modifiedMonitoredElementsParameters;

    public PostContingencyNetworkResult(LfNetwork network, StateMonitorIndexes monitorIndexes, boolean createResultExtension,
                                        PreContingencyNetworkResult preContingencyNetworkResult, Contingency contingency, LoadFlowModel loadFlowModel, double dcPowerFactor,
                                        ModifiedMonitoredElementsParameters modifiedMonitoredElementsParameters) {
        super(network, monitorIndexes, createResultExtension, loadFlowModel, dcPowerFactor);
        this.preContingencyNetworkResult = Objects.requireNonNull(preContingencyNetworkResult);
        this.contingency = Objects.requireNonNull(contingency);
        this.modifiedMonitoredElementsParameters = modifiedMonitoredElementsParameters;
    }

    protected void clear() {
        busResults.clear();
        branchResults.clear();
        threeWindingsTransformerResults.clear();
    }

    public void addResults(StateMonitor monitor, Predicate<LfBranch> isBranchDisabled, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        addResults(monitor, branch -> createBranchResults(branch, zeroImpedanceFlows), isBranchDisabled,
                this::createBusResults, id -> create3WTransformerResults(id, zeroImpedanceFlows));
    }

    private void createBranchResults(LfBranch branch, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        var preContingencyBranchResult = preContingencyNetworkResult.getBranchResult(branch.getId());
        double preContingencyBranchP1 = preContingencyBranchResult != null ? preContingencyBranchResult.getP1() : Double.NaN;
        double preContingencyBranchOfContingencyP1 = Double.NaN;
        if (contingency.getElements().size() == 1) {
            ContingencyElement contingencyElement = contingency.getElements().getFirst();
            if (contingencyElement.getType() == ContingencyElementType.BRANCH
                    || contingencyElement.getType() == ContingencyElementType.LINE
                    || contingencyElement.getType() == ContingencyElementType.BOUNDARY_LINE
                    || contingencyElement.getType() == ContingencyElementType.TWO_WINDINGS_TRANSFORMER) {
                BranchResult preContingencyBranchOfContingencyResult = preContingencyNetworkResult.getBranchResult(contingencyElement.getId());
                if (preContingencyBranchOfContingencyResult != null) {
                    preContingencyBranchOfContingencyP1 = preContingencyBranchOfContingencyResult.getP1();
                }
            }
        }
        List<BranchResult> results = branch.createBranchResult(preContingencyBranchP1, preContingencyBranchOfContingencyP1, createResultExtension, zeroImpedanceFlows, loadFlowModel);
        BranchResult postContingencyBranchResult = results.getFirst(); // getFirst for the tie-line case, all others have only one BranchResult
        if (changed(preContingencyBranchResult, postContingencyBranchResult)) {
            branchResults.addAll(results);
        }
    }

    private static boolean changed(double pre, double post, double threshold) {
        return Double.isNaN(pre) || Double.isNaN(post) || Math.abs(pre - post) > threshold;
    }

    private boolean changed(BranchResult preCtg, BranchResult postCtg) {
        if (preCtg == null) {
            return true;
        }
        Objects.requireNonNull(postCtg);
        double threshold = modifiedMonitoredElementsParameters.getPowerModificationThreshold();
        return threshold <= 0
            || changed(preCtg.getP1(), postCtg.getP1(), threshold)
            || changed(preCtg.getQ1(), postCtg.getQ1(), threshold)
            || changed(preCtg.getP2(), postCtg.getP2(), threshold)
            || changed(preCtg.getQ2(), postCtg.getQ2(), threshold);
    }

    private void createBusResults(LfBus bus) {
        List<BusResult> unfilteredBusResults = bus.createBusResults();
        for (BusResult busResult : unfilteredBusResults) {
            var preContingencyBusResult = preContingencyNetworkResult.getBusResult("%s_%s".formatted(busResult.getVoltageLevelId(), busResult.getBusId()));
            if (preContingencyBusResult != null) {
                double threshold = modifiedMonitoredElementsParameters.getVoltageModificationThreshold(preContingencyBusResult.getV());
                if (Math.abs(preContingencyBusResult.getV() - busResult.getV()) < threshold) {
                    continue;
                }
            }
            busResults.add(busResult);
        }
    }

    private void create3WTransformerResults(String id, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        var preContingencyResult = preContingencyNetworkResult.getThreeWindingsTransformerResult(id);
        var postContingencyResult = LfLegBranch.createThreeWindingsTransformerResult(network, id, createResultExtension, zeroImpedanceFlows, loadFlowModel);
        if (changed(preContingencyResult, postContingencyResult)) {
            threeWindingsTransformerResults.add(postContingencyResult);
        }
    }

    private boolean changed(ThreeWindingsTransformerResult preCtg, ThreeWindingsTransformerResult postCtg) {
        if (preCtg == null) {
            return true;
        }
        Objects.requireNonNull(postCtg);
        double threshold = modifiedMonitoredElementsParameters.getPowerModificationThreshold();
        return threshold <= 0
                || changed(preCtg.getP1(), postCtg.getP1(), threshold)
                || changed(preCtg.getQ1(), postCtg.getQ1(), threshold)
                || changed(preCtg.getP2(), postCtg.getP2(), threshold)
                || changed(preCtg.getQ2(), postCtg.getQ2(), threshold)
                || changed(preCtg.getP3(), postCtg.getP3(), threshold)
                || changed(preCtg.getQ3(), postCtg.getQ3(), threshold);
    }

    @Override
    public void update() {
        update(LfBranch::isDisabled);
    }

    @Override
    protected void storeInitialPhaseTapChangerInfo() {
        var preContingencyChangedTaps = preContingencyNetworkResult.getChangedPhaseTapChangers();
        phaseTapChangerInfos = network.getBranches().stream()
                .filter(b -> !b.isDisabled())
                .filter(LfBranch::hasPhaseControllerCapability)
                .map(b -> {
                    // Finding if phase tap has been changed in previous pre contingency state
                    ChangedPhaseTapChanger changedPhaseTapChanger = preContingencyChangedTaps.getOrDefault(Pair.of(b.getMainOriginalId(), b.getOriginalSide()), null);
                    // Creating initial info with either network initial tap, or pre contingency tap if it has been changed
                    return new PhaseTapChangerInfo(b.getPhaseTapChanger().orElseThrow(),
                            b.getMainOriginalId(),
                            b.getOriginalSide().orElse(null),
                            b.getPiModel(),
                            changedPhaseTapChanger != null ? changedPhaseTapChanger.finalTap() : b.getPhaseTapChanger().orElseThrow().getTapPosition());
                })
                .toList();
    }

    public void update(Predicate<LfBranch> isBranchDisabled) {
        clear();
        StateMonitor stateMonitor = monitorIndex.getSpecificStateMonitors().get(contingency.getId());
        if (stateMonitor != null) {
            Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(zeroImpedanceMonitorIndex.getSpecificStateMonitors().get(contingency.getId()), network);
            addResults(stateMonitor, isBranchDisabled, zeroImpedanceFlows);
        } else {
            Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows = storeResultsForZeroImpedanceBranches(zeroImpedanceMonitorIndex.getAllStateMonitor(), network);
            addResults(monitorIndex.getAllStateMonitor(), isBranchDisabled, zeroImpedanceFlows);
        }
        storeInitialPhaseTapChangerInfo();
        updateChangedPhaseTapChanger();
    }

    @Override
    public List<BranchResult> getBranchResults() {
        return branchResults;
    }

    @Override
    public List<BusResult> getBusResults() {
        return busResults;
    }

    @Override
    public List<ThreeWindingsTransformerResult> getThreeWindingsTransformerResults() {
        return threeWindingsTransformerResults;
    }
}
