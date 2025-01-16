package com.powsybl.openloadflow.network.action;

import com.powsybl.action.AreaInterchangeTargetAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;

public class LfAreaInterchangeTargetAction extends AbstractLfAction<AreaInterchangeTargetAction> {

    public LfAreaInterchangeTargetAction(String id, AreaInterchangeTargetAction action) {
        super(id, action);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        LfArea area = network.getAreaById(action.getId());
        if (area != null) {
            area.setInterchangeTarget(action.getInterchangeTarget());
            return true;
        }
        return false;
    }
}
