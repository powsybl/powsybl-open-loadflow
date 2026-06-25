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
import com.powsybl.openloadflow.util.PerUnit;
import com.powsybl.security.SecurityAnalysisParameters.ModifiedMonitoredElementsParameters;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class PostContingencyNetworkResult extends AbstractNetworkResult {

    private final List<BranchResult> branchResults = new ArrayList<>();
    private final List<BusResult> busResults = new ArrayList<>();
    private final List<ThreeWindingsTransformerResult> threeWindingsTransformerResults = new ArrayList<>();

    private final PreContingencyNetworkResult preContingencyMonitorInfos;

    private final Contingency contingency;

    private final ModifiedMonitoredElementsParameters modifiedMonitoredElementsParameters;

    public PostContingencyNetworkResult(LfNetwork network, StateMonitorIndexes monitorIndexes, boolean createResultExtension,
                                        PreContingencyNetworkResult preContingencyMonitorInfos, Contingency contingency, LoadFlowModel loadFlowModel, double dcPowerFactor,
                                        ModifiedMonitoredElementsParameters modifiedMonitoredElementsParameters) {
        super(network, monitorIndexes, createResultExtension, loadFlowModel, dcPowerFactor);
        this.preContingencyMonitorInfos = Objects.requireNonNull(preContingencyMonitorInfos);
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
                this::createBusResults, id -> create3WTransformerResults(id, zeroImpedanceFlows), zeroImpedanceFlows);
    }

    private void createBranchResults(LfBranch branch, Map<String, LfBranch.LfBranchResults> zeroImpedanceFlows) {
        var preContingencyBranchResult = preContingencyMonitorInfos.getBranchResult(branch.getId());
        double preContingencyBranchP1 = preContingencyBranchResult != null ? preContingencyBranchResult.getP1() : Double.NaN;
        double preContingencyBranchP2 = preContingencyBranchResult != null ? preContingencyBranchResult.getP2() : Double.NaN;
        double preContingencyBranchQ1 = preContingencyBranchResult != null ? preContingencyBranchResult.getQ1() : Double.NaN;
        double preContingencyBranchQ2 = preContingencyBranchResult != null ? preContingencyBranchResult.getQ2() : Double.NaN;
        double preContingencyBranchOfContingencyP1 = Double.NaN;
        if (contingency.getElements().size() == 1) {
            ContingencyElement contingencyElement = contingency.getElements().get(0);
            if (contingencyElement.getType() == ContingencyElementType.BRANCH
                    || contingencyElement.getType() == ContingencyElementType.LINE
                    || contingencyElement.getType() == ContingencyElementType.BOUNDARY_LINE
                    || contingencyElement.getType() == ContingencyElementType.TWO_WINDINGS_TRANSFORMER) {
                BranchResult preContingencyBranchOfContingencyResult = preContingencyMonitorInfos.getBranchResult(contingencyElement.getId());
                if (preContingencyBranchOfContingencyResult != null) {
                    preContingencyBranchOfContingencyP1 = preContingencyBranchOfContingencyResult.getP1();
                }
            }
        }
        if (preContingencyBranchResult != null) {
            double threshold = modifiedMonitoredElementsParameters.getPowerModificationThreshold();
            if (Math.abs((preContingencyBranchP1 - branch.getP1().eval() * PerUnit.SB) / preContingencyBranchP1) < threshold &&
                    Math.abs((preContingencyBranchP2 - branch.getP2().eval() * PerUnit.SB) / preContingencyBranchP2) < threshold &&
                    Math.abs((preContingencyBranchQ1 - branch.getQ1().eval() * PerUnit.SB) / preContingencyBranchQ1) < threshold &&
                    Math.abs((preContingencyBranchQ2 - branch.getQ2().eval() * PerUnit.SB) / preContingencyBranchQ2) < threshold) {
                return;
            }
        }
        branchResults.addAll(branch.createBranchResult(preContingencyBranchP1, preContingencyBranchOfContingencyP1, createResultExtension, zeroImpedanceFlows, loadFlowModel));
    }

    private void createBusResults(LfBus bus) {
        List<BusResult> unfilteredBusResults = bus.createBusResults();
        for (BusResult busResult : unfilteredBusResults) {
            var preContingencyBusResult = preContingencyMonitorInfos.getBusResult("%s_%s".formatted(busResult.getVoltageLevelId(), busResult.getBusId()));
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
        LfLegBranch leg1 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(id, 1));
        LfLegBranch leg2 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(id, 2));
        LfLegBranch leg3 = (LfLegBranch) network.getBranchById(LfLegBranch.getId(id, 3));
        var preContingencyResult = preContingencyMonitorInfos.getThreeWindingsTransformerResult(id);
        double preContingencyLeg1P = preContingencyResult != null ? preContingencyResult.getP1() : Double.NaN;
        double preContingencyLeg2P = preContingencyResult != null ? preContingencyResult.getP2() : Double.NaN;
        double preContingencyLeg3P = preContingencyResult != null ? preContingencyResult.getP3() : Double.NaN;
        double preContingencyLeg1Q = preContingencyResult != null ? preContingencyResult.getQ1() : Double.NaN;
        double preContingencyLeg2Q = preContingencyResult != null ? preContingencyResult.getQ2() : Double.NaN;
        double preContingencyLeg3Q = preContingencyResult != null ? preContingencyResult.getQ3() : Double.NaN;

        if (preContingencyResult != null) {
            double threshold = modifiedMonitoredElementsParameters.getPowerModificationThreshold();
            if (Math.abs((preContingencyLeg1P - leg1.getP1().eval() * PerUnit.SB) / preContingencyLeg1P) < threshold &&
                    Math.abs((preContingencyLeg2P - leg2.getP1().eval() * PerUnit.SB) / preContingencyLeg2P) < threshold &&
                    Math.abs((preContingencyLeg3P - leg3.getP1().eval() * PerUnit.SB) / preContingencyLeg3P) < threshold &&
                    Math.abs((preContingencyLeg1Q - leg1.getQ1().eval() * PerUnit.SB) / preContingencyLeg1Q) < threshold &&
                    Math.abs((preContingencyLeg2Q - leg2.getQ1().eval() * PerUnit.SB) / preContingencyLeg2Q) < threshold &&
                    Math.abs((preContingencyLeg3Q - leg3.getQ1().eval() * PerUnit.SB) / preContingencyLeg3Q) < threshold) {
                return;
            }
        }
        threeWindingsTransformerResults.add(LfLegBranch.createThreeWindingsTransformerResult(network, id, createResultExtension, zeroImpedanceFlows, loadFlowModel));
    }

    @Override
    public void update() {
        update(LfBranch::isDisabled);
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
