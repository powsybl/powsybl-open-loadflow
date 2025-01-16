package com.powsybl.openloadflow.network.action;

import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.openloadflow.graph.GraphConnectivity;
import com.powsybl.openloadflow.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LfPhaseTapChangerAction extends AbstractLfTapChangerAction<PhaseTapChangerTapPositionAction> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LfPhaseTapChangerAction.class);

    public LfPhaseTapChangerAction(String id, PhaseTapChangerTapPositionAction action, LfNetwork network) {
        super(id, action, network);
    }

    @Override
    public boolean apply(LfNetwork network, LfContingency contingency, LfNetworkParameters networkParameters, GraphConnectivity<LfBus, LfBranch> connectivity) {
        if (branch != null) {
            if (branch.getPhaseControl().isPresent()) {
                LOGGER.warn("Phase tap changer tap position action: phase control is present on the tap changer, tap position could be overriden.");
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
