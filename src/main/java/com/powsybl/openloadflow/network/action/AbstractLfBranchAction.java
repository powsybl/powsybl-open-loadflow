/**
 * Copyright (c) 2025, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.util.Reports;

import java.util.*;

/**
 * @author Bertrand Rix {@literal <bertrand.rix at artelys.com>}
 * @author Anne Tilloy {@literal <anne.tilloy at rte-france.com>}
 * @author Jean-Luc Bouchot {@literal <jlbouchot at gmail.com>}
 */
public abstract class AbstractLfBranchAction<A extends Action> extends AbstractLfAction<A> {

    private final List<LfBranch> disabledBranch = new ArrayList<>(); // switch to open

    private final List<LfBranch> enabledBranch = new ArrayList<>(); // switch to close

    AbstractLfBranchAction(A action, LfNetwork lfNetwork) {
        super(action);
        findEnabledDisabledBranches(lfNetwork);
    }

    protected void addDisabledBranch(LfBranch disabledBranch) {
        Objects.requireNonNull(disabledBranch);
        this.disabledBranch.add(disabledBranch);
    }

    protected void addEnabledBranch(LfBranch enabledBranch) {
        Objects.requireNonNull(enabledBranch);
        this.enabledBranch.add(enabledBranch);
    }

    public List<LfBranch> getDisabledBranches() {
        return this.disabledBranch;
    }

    public List<LfBranch> getEnabledBranches() {
        return this.enabledBranch;
    }

    abstract void findEnabledDisabledBranches(LfNetwork lfNetwork);

    @Override
    public boolean isValid() {
        return !disabledBranch.isEmpty() || !enabledBranch.isEmpty();
    }

    /**
     * Standalone apply
     */
    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters) {
        if (!isValid()) {
            return false;
        }
        getNetworkActivations(network, contingency, List.of(this)).apply();
        return true;
    }

    public boolean updateConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (isValid()) {
            disabledBranch.forEach(branch -> {
                if (branch.getBus1() != null && branch.getBus2() != null) {
                    connectivity.removeEdge(branch);
                }
            });
            enabledBranch.forEach(branch -> connectivity.addEdge(branch.getBus1(), branch.getBus2(), branch));
            return true;
        }
        return false;
    }

    public static NetworkActivations getNetworkActivations(GraphConnectivity<LfBus, LfBranch> connectivity) {
        // disable buses and branches that won't be part of the main connected component
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        DisabledNetwork disabledNetwork = new DisabledNetwork(removedBuses, removedBranches);

        // enable buses and branches that will be part of the main connected component
        Set<LfBus> addedBuses = connectivity.getVerticesAddedToMainComponent();
        Set<LfBranch> addedBranches = new HashSet<>(connectivity.getEdgesAddedToMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : addedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(addedBranches::add);
        }
        EnabledNetwork enabledNetwork = new EnabledNetwork(addedBuses, addedBranches);

        return new NetworkActivations(disabledNetwork, enabledNetwork);
    }

    public static NetworkActivations getNetworkActivations(LfNetwork network, LfContingency contingency,
                                                           List<AbstractLfBranchAction<?>> actions) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(actions);

        GraphConnectivity<LfBus, LfBranch> connectivity = network.getConnectivity();

        // re-update connectivity according to post contingency state (revert after LfContingency apply)
        connectivity.startTemporaryChanges();
        if (contingency != null) {
            contingency.getDisabledNetwork().getBranches().forEach(connectivity::removeEdge);
        }

        // update connectivity according to post action state
        connectivity.startTemporaryChanges();

        for (AbstractLfBranchAction<?> action : actions) {
            if (!action.updateConnectivity(connectivity)) {
                Reports.reportActionApplicationFailure(action.getId(), contingency != null ? contingency.getId() : "", network.getReportNode());
            }
        }

        NetworkActivations networkActivations = getNetworkActivations(connectivity);

        // reset connectivity to discard post contingency connectivity and post action connectivity
        connectivity.undoTemporaryChanges();
        connectivity.undoTemporaryChanges();

        return networkActivations;
    }
}
