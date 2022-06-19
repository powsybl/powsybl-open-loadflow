/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.openloadflow.sa.monitor;

import com.powsybl.openloadflow.network.LfNetwork;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.monitor.StateMonitorIndex;
import com.powsybl.security.results.BranchResult;

import java.util.*;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class PreContingencyMonitorInfos extends AbstractMonitorInfos {

    private final Map<String, BranchResult> branchResults = new HashMap<>();

    public PreContingencyMonitorInfos(LfNetwork network, StateMonitorIndex monitorIndex, boolean createResultExtension) {
        super(network, monitorIndex, createResultExtension);
    }

    @Override
    protected void clear() {
        super.clear();
        branchResults.clear();
    }

    private void addMonitorInfo(StateMonitor monitor) {
        addMonitorInfo(monitor, branch -> {
            var branchResult = branch.createBranchResult(Double.NaN, Double.NaN, createResultExtension);
            branchResults.put(branch.getId(), branchResult);
        });
    }

    public void update() {
        clear();
        addMonitorInfo(monitorIndex.getNoneStateMonitor());
        addMonitorInfo(monitorIndex.getAllStateMonitor());
    }

    public BranchResult getBranchResult(String branchId) {
        Objects.requireNonNull(branchId);
        return branchResults.get(branchId);
    }

    @Override
    public List<BranchResult> getBranchResults() {
        return new ArrayList<>(branchResults.values());
    }
}
