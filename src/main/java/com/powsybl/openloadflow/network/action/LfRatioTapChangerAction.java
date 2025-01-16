package com.powsybl.openloadflow.network.action;

import com.powsybl.action.RatioTapChangerTapPositionAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LfRatioTapChangerAction extends AbstractLfTapChangerAction<RatioTapChangerTapPositionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfRatioTapChangerAction.class);

    public LfRatioTapChangerAction(String id, RatioTapChangerTapPositionAction action, LfNetwork network) {
        super(id, action, network);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (this.branch != null) {
            if (branch.getVoltageControl().isPresent()) {
                LOGGER.warn("Ratio tap changer tap position action: voltage control is present on the tap changer, tap position could be overriden.");
            }
            if (branch.getPiModel() instanceof SimplePiModel) {
                throw new UnsupportedOperationException("Tap position action: only one tap in branch " + branch.getId());
            } else {
                branch.getPiModel().setTapPosition(this.change.getNewTapPosition());
                return true;
            }
        }
        return false;
    }
}
