package com.powsybl.openloadflow.network.action;

import com.powsybl.action.SwitchAction;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LfSwitchAction extends AbstractLfBranchAction<SwitchAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfSwitchAction.class);

    public LfSwitchAction(String id, SwitchAction action) {
        super(id, action);
    }

    @Override
    boolean findEnabledDisabledBranches(LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getSwitchId());
        if (branch != null) {
            if (action.isOpen()) {
                setDisabledBranch(branch);
            } else {
                setEnabledBranch(branch);
            }
            return true;
        } else {
            LOGGER.warn("Switch action {} : branch matching switch id {} not found", action.getId(), action.getSwitchId());
            return false;
        }
    }
}
