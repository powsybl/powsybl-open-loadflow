package com.powsybl.openloadflow.network.action;

import com.powsybl.action.AbstractTapChangerTapPositionAction;
import com.powsybl.openloadflow.network.*;
import com.powsybl.openloadflow.network.impl.LfLegBranch;

public abstract class AbstractLfTapChangerAction<A extends AbstractTapChangerTapPositionAction> extends AbstractLfAction<A> {

    protected TapPositionChange change;

    protected LfBranch branch;

    AbstractLfTapChangerAction(String id, A action, LfNetwork network) {
        super(id, action);
        String branchId = action.getSide().map(side -> LfLegBranch.getId(side, action.getTransformerId())).orElseGet(action::getTransformerId);
        this.branch = network.getBranchById(branchId);
        if (this.branch != null) {
            if (branch.getPiModel() instanceof SimplePiModel) {
                throw new UnsupportedOperationException("Tap position action: only one tap in branch " + branch.getId());
            }
            this.change = new TapPositionChange(branch, action.getTapPosition(), action.isRelativeValue());
        }
    }

    public TapPositionChange getChange() {
        return change;
    }
}
