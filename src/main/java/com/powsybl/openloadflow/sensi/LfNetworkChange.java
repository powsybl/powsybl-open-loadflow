package com.powsybl.openloadflow.sensi;

import com.powsybl.openloadflow.network.LfContingency;
import com.powsybl.openloadflow.network.action.LfAction;

import java.util.List;

public class LfNetworkChange {
    private final LfContingency contingency;
    private final List<LfAction> actions;

    public LfNetworkChange(LfContingency contingency, List<LfAction> actions) {
        this.contingency = contingency;
        this.actions = actions;
    }
}
