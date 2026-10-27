/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.sensi;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.math.matrix.SparseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.dc.DcLoadFlowContext;
import com.powsybl.openloadflow.dc.DcLoadFlowParameters;
import com.powsybl.openloadflow.dc.fastdc.LodfEngine;
import com.powsybl.openloadflow.dc.fastdc.LodfParameters;
import com.powsybl.openloadflow.dc.fastdc.LodfResult;
import com.powsybl.openloadflow.graph.EvenShiloachGraphDecrementalConnectivityFactory;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.impl.Networks;

import java.util.List;
import java.util.Objects;

/**
 * DC LODF matrix of an iidm network, see {@link LodfEngine}.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at gmail.com>}
 */
public final class LodfAnalysis {

    private LodfAnalysis() {
    }

    /**
     * Same as {@link #run(Network, List, List, LodfParameters, LoadFlowParameters, ReportNode)} with the default
     * {@link LodfParameters} and no report.
     */
    public static LodfResult run(Network network, List<String> monitoredBranchIds, List<String> outagedBranchIds,
                                 LoadFlowParameters parameters) {
        return run(network, monitoredBranchIds, outagedBranchIds, new LodfParameters(), parameters, ReportNode.NO_OP);
    }

    /**
     * Computed on the main synchronous component, with phase control off, HVDC lines as fixed injections and minimal
     * impedance for zero impedance branches. A branch outside the component or not closed gets an empty row as monitored
     * branch and is undefined as outage.
     */
    public static LodfResult run(Network network, List<String> monitoredBranchIds, List<String> outagedBranchIds,
                                 LodfParameters lodfParameters, LoadFlowParameters parameters, ReportNode reportNode) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(monitoredBranchIds);
        Objects.requireNonNull(outagedBranchIds);
        Objects.requireNonNull(lodfParameters);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(reportNode);

        OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
        DcLoadFlowParameters dcParameters = OpenLoadFlowParameters.createDcParameters(network, parameters, parametersExt,
                new SparseMatrixFactory(), new EvenShiloachGraphDecrementalConnectivityFactory<>(), false);
        dcParameters.getNetworkParameters()
                .setComponentMode(LoadFlowParameters.ComponentMode.MAIN_SYNCHRONOUS)
                .setPhaseControl(false)
                .setHvdcAcEmulation(false)
                .setMinImpedance(true); // otherwise zero impedance branches have no flow term

        LfNetwork lfNetwork = Networks.load(network, dcParameters.getNetworkParameters(), reportNode).stream()
                .filter(n -> n.getValidity() == LfNetwork.Validity.VALID)
                .findFirst()
                .orElseThrow(() -> new PowsyblException("Network '" + network.getId() + "' has no valid main synchronous component"));
        List<LfBranch> monitoredBranches = getLfBranches(network, lfNetwork, monitoredBranchIds);
        List<LfBranch> outagedBranches = getLfBranches(network, lfNetwork, outagedBranchIds);
        try (DcLoadFlowContext loadFlowContext = new DcLoadFlowContext(lfNetwork, dcParameters, false)) {
            return LodfEngine.run(loadFlowContext, monitoredBranches, outagedBranches, lodfParameters);
        }
    }

    // null for a branch not in the LF network
    private static List<LfBranch> getLfBranches(Network network, LfNetwork lfNetwork, List<String> branchIds) {
        return branchIds.stream()
                .map(branchId -> {
                    LfBranch branch = lfNetwork.getBranchById(branchId);
                    if (branch == null && network.getIdentifiable(branchId) == null) {
                        throw new PowsyblException("Branch '" + branchId + "' not found");
                    }
                    return branch;
                })
                .toList();
    }
}
