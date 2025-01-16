package com.powsybl.openloadflow.network.action;

import com.powsybl.openloadflow.network.*;

public interface LfAction {

    String getId();

    boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters);
}
