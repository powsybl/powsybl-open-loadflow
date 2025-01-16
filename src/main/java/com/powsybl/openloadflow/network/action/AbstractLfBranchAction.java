package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractLfBranchAction<A extends Action> extends AbstractLfAction<A> {

    private LfBranch disabledBranch = null; // switch to open

    private LfBranch enabledBranch = null; // switch to close

    AbstractLfBranchAction(String id, A action) {
        super(id, action);
    }

    protected void setDisabledBranch(LfBranch disabledBranch) {
        this.disabledBranch = disabledBranch;
    }

    protected void setEnabledBranch(LfBranch enabledBranch) {
        this.enabledBranch = enabledBranch;
    }

    public LfBranch getDisabledBranch() {
        return this.disabledBranch;
    }

    public LfBranch getEnabledBranch() {
        return this.enabledBranch;
    }

    abstract boolean findEnabledDisabledBranches(LfNetwork lfNetwork);

    /**
     * Standalone apply
     */
    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        boolean found = findEnabledDisabledBranches(network);
        GraphConnectivity<LfBus, LfBranch> connectivityTmp = network.getConnectivity();

        // re-update connectivity according to post contingency state (revert after LfContingency apply)
        connectivityTmp.startTemporaryChanges();
        if (contingency != null) {
            contingency.getDisabledNetwork().getBranches().forEach(connectivityTmp::removeEdge);
        }

        // update connectivity according to post action state
        connectivityTmp.startTemporaryChanges();

        applyOnConnectivity(connectivityTmp);
        updateBusesAndBranchStatus(connectivityTmp);

        // reset connectivity to discard post contingency connectivity and post action connectivity
        connectivityTmp.undoTemporaryChanges();
        connectivityTmp.undoTemporaryChanges();
        return found;
    }

    /**
     * Optimized apply on an existing connectivity (to apply several branch actions together)
     */
    public void applyOnConnectivity(GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (disabledBranch != null && disabledBranch.getBus1() != null && disabledBranch.getBus2() != null) {
            connectivity.removeEdge(disabledBranch);
        }
        if (enabledBranch != null) {
            connectivity.addEdge(enabledBranch.getBus1(), enabledBranch.getBus2(), enabledBranch);
        }
    }

    public static void updateBusesAndBranchStatus(GraphConnectivity<LfBus, LfBranch> connectivity) {
        // disable buses and branches that won't be part of the main connected component
        Set<LfBus> removedBuses = connectivity.getVerticesRemovedFromMainComponent();
        removedBuses.forEach(bus -> bus.setDisabled(true));
        Set<LfBranch> removedBranches = new HashSet<>(connectivity.getEdgesRemovedFromMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : removedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(removedBranches::add);
        }
        removedBranches.forEach(branch -> branch.setDisabled(true));

        // enable buses and branches that will be part of the main connected component
        Set<LfBus> addedBuses = connectivity.getVerticesAddedToMainComponent();
        addedBuses.forEach(bus -> bus.setDisabled(false));
        Set<LfBranch> addedBranches = new HashSet<>(connectivity.getEdgesAddedToMainComponent());
        // we should manage branches open at one side.
        for (LfBus bus : addedBuses) {
            bus.getBranches().stream().filter(b -> !b.isConnectedAtBothSides()).forEach(addedBranches::add);
        }
        addedBranches.forEach(branch -> branch.setDisabled(false));
    }
}
