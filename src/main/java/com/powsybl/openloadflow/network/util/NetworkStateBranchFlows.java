/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.util;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfZeroImpedanceNetwork;
import com.powsybl.openloadflow.network.LoadFlowModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Single entry point to get the flows of any branch (impedant or zero impedance) for a given network state.
 * Zero impedance branch flows are lazily computed per zero impedance network, only on first request of one of its
 * branches, and cached. Must not be reused after any change of the network state (load flow run, contingency or
 * action applied) as flows have changed and zero impedance networks may have been split or merged.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworkStateBranchFlows {

    private final LoadFlowModel loadFlowModel;

    private final double dcPowerFactor;

    private final Map<LfZeroImpedanceNetwork, Map<LfBranch, LfBranch.LfBranchResults>> flowsByZeroImpedanceNetwork = new HashMap<>();

    public NetworkStateBranchFlows(LoadFlowModel loadFlowModel, double dcPowerFactor) {
        this.loadFlowModel = Objects.requireNonNull(loadFlowModel);
        this.dcPowerFactor = dcPowerFactor;
    }

    /**
     * Gets the flows (active power, reactive power and current, per unit) at both sides of a branch in the current
     * network state:
     * <ul>
     *     <li>impedant branch: flows are evaluated from its equation terms,</li>
     *     <li>zero impedance branch connected at both sides: flows of its whole zero impedance network are computed
     *     on first request, then cached,</li>
     *     <li>zero impedance branch connected at one side only: zero at the connected side and NaN at the open side,
     *     as no flow can go through it.</li>
     * </ul>
     *
     * @param branch a branch of the network, not disabled
     * @return the flows at both sides of the branch
     * @throws PowsyblException if the branch is disabled
     */
    public LfBranch.LfBranchResults getFlows(LfBranch branch) {
        if (branch.isDisabled()) {
            // equation terms would still evaluate as if closed, and a disabled branch is not a zero impedance network edge
            throw new PowsyblException("Flows of disabled branch '" + branch.getId() + "' are not available");
        }
        if (!branch.isZeroImpedance(loadFlowModel)) {
            return new LfBranch.LfBranchResults(branch.getP1().eval(), branch.getP2().eval(),
                    branch.getQ1().eval(), branch.getQ2().eval(),
                    branch.getI1().eval(), branch.getI2().eval());
        }
        if (branch.getBus1() == null || branch.getBus2() == null) {
            // not part of any zero impedance network: no flow can go through a zero impedance branch open at one side
            double side1 = branch.getBus1() != null ? 0 : Double.NaN;
            double side2 = branch.getBus2() != null ? 0 : Double.NaN;
            return new LfBranch.LfBranchResults(side1, side2, side1, side2, side1, side2);
        }
        branch.getNetwork().updateZeroImpedanceCache(loadFlowModel);
        // both sides are in the same zero impedance network as the branch is one of its edges
        LfZeroImpedanceNetwork zeroImpedanceNetwork = branch.getBus1().getZeroImpedanceNetwork(loadFlowModel);
        return flowsByZeroImpedanceNetwork.computeIfAbsent(zeroImpedanceNetwork,
                        zn -> new ZeroImpedanceFlows(zn.getGraph(), zn.getSpanningTree(), loadFlowModel, dcPowerFactor).compute())
                .get(branch);
    }

    int getComputedZeroImpedanceNetworkCount() {
        return flowsByZeroImpedanceNetwork.size();
    }
}
