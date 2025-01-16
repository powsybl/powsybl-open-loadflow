package com.powsybl.openloadflow.network.action;

import com.powsybl.action.Action;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;

public abstract class AbstractLfAction<A extends Action> {

    protected final String id;

    protected final A action;

    AbstractLfAction(String id, A action) {
        this.id = id;
        this.action = action;
    }

    public String getId() {
        return id;
    }

    public abstract boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity);
}
