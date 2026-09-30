/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfBus;
import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.openloadflow.network.LfShunt;
import com.powsybl.openloadflow.network.LoadFlowModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps LfBranches and fixed LfShunts to their position in the GPU element packs
 * (closed branches, then open branches, then shunts — the same order
 * {@link com.powsybl.openloadflow.ac.equations.vector.gpu.GpuAcDataExtractor} enumerates them).
 * Used to build per-scenario disabled element masks for batched N-1 security analysis.
 *
 * @author Gautier Bureau {@literal <gautier.bureau at rte-france.com>}
 */
public class GpuBatchElementMapper {

    private final Map<LfBranch, Integer> branchToElementIndex;
    private final Map<LfShunt, Integer> shuntToElementIndex;
    private final int closedBranchCount;
    private final int openBranchCount;
    private final int shuntCount;

    /**
     * Build the mapping from a network by re-enumerating branches in the same order
     * as GpuAcDataExtractor: first all closed branches, then all open branches.
     */
    public GpuBatchElementMapper(LfNetwork network) {
        Objects.requireNonNull(network);
        this.branchToElementIndex = new HashMap<>();
        List<LfBranch> closedBranches = new ArrayList<>();
        List<LfBranch> openBranches = new ArrayList<>();

        // Enumerate branches in the same order as GpuAcDataExtractor. Zero-impedance branches (bus couplers)
        // are a SEPARATE family there (not in the closed/open packs), so they must be skipped here too — else
        // every later element index would be off by the zero-imp count and the disabled mask would corrupt.
        // Skipping them also means getElementIndex(zeroImpBranch) == -1, so a contingency that outages a bus
        // coupler is rejected to the CPU (NOT_IN_GPU_MODEL) — correct, as that outage is islanding-like.
        for (LfBranch branch : network.getBranches()) {
            if (branch.isDisabled() || branch.isZeroImpedance(LoadFlowModel.AC)) {
                continue;
            }
            if (branch.getBus1() != null && branch.getBus2() != null) {
                closedBranches.add(branch);
            } else if (branch.getBus1() != null || branch.getBus2() != null) {
                openBranches.add(branch);
            }
        }

        this.closedBranchCount = closedBranches.size();
        this.openBranchCount = openBranches.size();

        // Map closed branches to indices [0, closedBranchCount)
        for (int i = 0; i < closedBranches.size(); i++) {
            branchToElementIndex.put(closedBranches.get(i), i);
        }

        // Map open branches to indices [closedBranchCount, closedBranchCount + openBranchCount)
        for (int i = 0; i < openBranches.size(); i++) {
            branchToElementIndex.put(openBranches.get(i), closedBranchCount + i);
        }

        // Map fixed shunts to indices [closedBranchCount + openBranchCount, ...), in the SAME bus
        // enumeration order GpuAcDataExtractor uses to build the shunt pack (one fixed shunt per bus).
        this.shuntToElementIndex = new HashMap<>();
        int shOffset = closedBranchCount + openBranchCount;
        int shIndex = 0;
        for (LfBus bus : network.getBuses()) {
            var shunt = bus.getShunt();
            if (shunt.isPresent()) {
                shuntToElementIndex.put(shunt.get(), shOffset + shIndex);
                shIndex++;
            }
        }
        this.shuntCount = shIndex;
    }

    /**
     * Get the element index of a branch in the GPU element pack enumeration.
     *
     * @param branch the branch to look up
     * @return the element index (in the combined [closed, open] enumeration), or -1 if not found
     */
    public int getElementIndex(LfBranch branch) {
        Integer index = branchToElementIndex.get(branch);
        return index != null ? index : -1;
    }

    /**
     * Get the element index of a fixed shunt in the GPU element pack enumeration.
     *
     * @param shunt the shunt to look up
     * @return the element index (in the combined [closed, open, shunt] enumeration), or -1 if not a
     *         packed fixed shunt (e.g. a voltage-controlling controller/SVC shunt the GPU does not model)
     */
    public int getElementIndex(LfShunt shunt) {
        Integer index = shuntToElementIndex.get(shunt);
        return index != null ? index : -1;
    }

    public int getClosedBranchCount() {
        return closedBranchCount;
    }

    public int getOpenBranchCount() {
        return openBranchCount;
    }

    public int getShuntCount() {
        return shuntCount;
    }

    public int getTotalBranchCount() {
        return closedBranchCount + openBranchCount;
    }
}
