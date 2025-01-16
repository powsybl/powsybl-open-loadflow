package com.powsybl.openloadflow.network.action;

import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.openloadflow.network.LfBranch;
import com.powsybl.openloadflow.network.LfNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LfTerminalsConnectionAction extends AbstractLfBranchAction<TerminalsConnectionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfTerminalsConnectionAction.class);

    public LfTerminalsConnectionAction(String id, TerminalsConnectionAction action) {
        super(id, action);
    }

    @Override
    boolean findEnabledDisabledBranches(LfNetwork lfNetwork) {
        LfBranch branch = lfNetwork.getBranchById(action.getElementId());
        if (branch != null && branch.getBus1() != null && branch.getBus2() != null) {
            if (action.getSide().isEmpty()) {
                if (action.isOpen()) {
                    setDisabledBranch(branch);
                } else {
                    setEnabledBranch(branch);
                }
            } else {
                throw new UnsupportedOperationException("Terminals connection action: only open or close branch at both sides is supported yet.");
            }
            return true;
        } else {
            LOGGER.warn("TerminalsConnectionAction action {} : branch matching element id {} not found", action.getId(), action.getElementId());
            return false;
        }
    }
}
